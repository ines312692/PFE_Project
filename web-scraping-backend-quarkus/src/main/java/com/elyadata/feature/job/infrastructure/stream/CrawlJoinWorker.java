package com.elyadata.feature.job.infrastructure.stream;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.service.JobService;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyOutcome;
import com.elyadata.feature.proxy.domain.service.ProxyPoolService;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.BrowserFactory;
import com.elyadata.feature.scraper.crawler.pipeline.DistributedCrawlCoordinator;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractorRegistry;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class CrawlJoinWorker {

    private final StreamCommands<String, String, String> streams;
    private final StreamConfig config;
    private final ConsumerIdentity identity;
    private final CrawlJoinWorkerPool pool;
    private final StreamConsumerSupport support;
    private final JobPort jobPort;
    private final JobService jobService;
    private final DistributedCrawlCoordinator coordinator;
    private final BrowserFactory browserFactory;
    private final ExtractorRegistry extractorRegistry;
    private final ProxyPoolService proxyPoolService;
    private final boolean enabled;

    public CrawlJoinWorker(RedisDataSource redisDataSource, StreamConfig config, ConsumerIdentity identity,
                           CrawlJoinWorkerPool pool, StreamConsumerSupport support,
                           JobPort jobPort, JobService jobService,
                           DistributedCrawlCoordinator coordinator, BrowserFactory browserFactory,
                           ExtractorRegistry extractorRegistry, ProxyPoolService proxyPoolService) {
        this.streams = redisDataSource.stream(String.class, String.class, String.class);
        this.config = config;
        this.identity = identity;
        this.pool = pool;
        this.support = support;
        this.jobPort = jobPort;
        this.jobService = jobService;
        this.coordinator = coordinator;
        this.browserFactory = browserFactory;
        this.extractorRegistry = extractorRegistry;
        this.proxyPoolService = proxyPoolService;
        this.enabled = config.enabled();
    }

    void onStart(@Observes StartupEvent ev) {
        if (enabled) support.ensureGroup(RedisStreamNames.CRAWL_JOIN, config.crawlJoin().group());
    }

    @Scheduled(every = "{scraper.stream.crawl-join.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        if (!enabled) return;
        int free = pool.freeSlots();
        if (free <= 0) return;

        List<StreamMessage<String, String, String>> msgs = readNew(free);
        if (msgs.isEmpty()) return;

        log.debug("[CRAWL-JOIN] polled {} message(s) (free={})", msgs.size(), free);
        for (StreamMessage<String, String, String> msg : msgs) {
            if (!pool.submit(() -> process(msg))) break;
        }
    }

    private List<StreamMessage<String, String, String>> readNew(int count) {
        try {
            return streams.xreadgroup(
                    config.crawlJoin().group(), identity.getName(),
                    RedisStreamNames.CRAWL_JOIN, ">",
                    new XReadGroupArgs().count(count));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NOGROUP")) {
                support.ensureGroup(RedisStreamNames.CRAWL_JOIN, config.crawlJoin().group());
                return List.of();
            }
            log.warn("[CRAWL-JOIN] XREADGROUP failed: {}", msg);
            return List.of();
        }
    }

    void process(StreamMessage<String, String, String> msg) {
        String messageId = msg.id();
        Map<String, String> payload = msg.payload();
        String group = config.crawlJoin().group();
        String rawJobId = payload.get(RedisStreamNames.FIELD_JOB_ID);

        if (rawJobId == null || rawJobId.isBlank()) {
            support.handleFailure(RedisStreamNames.CRAWL_JOIN, group, RedisStreamNames.CRAWL_JOIN_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.crawlJoin().maxDeliveries(), "missing jobId");
            return;
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(rawJobId);
        } catch (IllegalArgumentException e) {
            support.handleFailure(RedisStreamNames.CRAWL_JOIN, group, RedisStreamNames.CRAWL_JOIN_DLQ,
                    messageId, payload, Long.MAX_VALUE, config.crawlJoin().maxDeliveries(), "invalid jobId: " + rawJobId);
            return;
        }

        Optional<Job> maybeJob = jobPort.findById(jobId);
        if (maybeJob.isEmpty() || isTerminal(maybeJob.get())) {
            log.info("[CRAWL-JOIN] job={} absent/terminal — ack sans renfort", jobId);
            support.ack(RedisStreamNames.CRAWL_JOIN, group, messageId);
            return;
        }

        long deliveryCount = support.recordAttempt(RedisStreamNames.CRAWL_JOIN, messageId);
        Optional<Proxy> proxy = Optional.empty();
        try {
            InternalScrapeRequest request = jobService.buildRequestForJoin(jobId);

            if (request.getJobType() != JobType.CRAWLER) {
                log.warn("[CRAWL-JOIN] job={} n'est pas un CRAWLER — ack, ignore", jobId);
                support.ack(RedisStreamNames.CRAWL_JOIN, group, messageId);
                return;
            }

            proxy = acquireProxyIfNeeded(request, jobId);
            InternalScrapeRequest enriched = proxy
                    .map(p -> request.toBuilder().proxy(p).build())
                    .orElse(request);

            ABrowser browser = browserFactory.create(enriched);
            BaseExtractor extractor = extractorRegistry.get(enriched.getExtractionMode());

            log.info("[CRAWL-JOIN] renfort job={} (msg={} livraison={}) proxy={}",
                    jobId, messageId, deliveryCount, proxy.isPresent());

            coordinator.joinExistingCrawl(enriched, browser, extractor);

            support.ack(RedisStreamNames.CRAWL_JOIN, group, messageId);
            log.info("[CRAWL-JOIN] renfort terminé job={} — ack msg {}", jobId, messageId);

        } catch (Exception e) {
            log.error("[CRAWL-JOIN] renfort échoué job={} (msg={}): {}", jobId, messageId, e.getMessage(), e);
            support.handleFailure(RedisStreamNames.CRAWL_JOIN, group, RedisStreamNames.CRAWL_JOIN_DLQ,
                    messageId, payload, deliveryCount, config.crawlJoin().maxDeliveries(), e.getMessage());
        } finally {
            releaseProxy(proxy, jobId);
        }
    }

    private Optional<Proxy> acquireProxyIfNeeded(InternalScrapeRequest request, UUID jobId) {
        if (request.getProxyConfig() == null || !request.getProxyConfig().isEnabled()) return Optional.empty();
        try {
            Optional<Proxy> p = proxyPoolService.acquire(request.getProxyConfig(), jobId, List.of());
            if (p.isEmpty()) log.warn("[CRAWL-JOIN] aucun proxy dispo job={} — renfort IP directe", jobId);
            return p;
        } catch (Exception e) {
            log.warn("[CRAWL-JOIN] acquire proxy échoué job={}: {} — renfort IP directe", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    private void releaseProxy(Optional<Proxy> proxy, UUID jobId) {
        proxy.ifPresent(p -> {
            try {
                proxyPoolService.release(p.id(), jobId, ProxyOutcome.success(0));
            } catch (Exception e) {
                log.warn("[CRAWL-JOIN] release proxy échoué job={}: {}", jobId, e.getMessage());
            }
        });
    }

    private boolean isTerminal(Job job) {
        return job.status() == JobStatus.COMPLETED
                || job.status() == JobStatus.FAILED
                || job.status() == JobStatus.CANCELLED;
    }
}