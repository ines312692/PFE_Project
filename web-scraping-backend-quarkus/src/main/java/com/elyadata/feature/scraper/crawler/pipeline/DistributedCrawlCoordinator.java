package com.elyadata.feature.scraper.crawler.pipeline;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.infrastructure.stream.CrawlJoinDispatcher;
import com.elyadata.feature.job.infrastructure.stream.StreamConfig;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.crawler.discovery.SiteFileDiscoverer;
import com.elyadata.feature.scraper.crawler.frontier.CrawlNode;
import com.elyadata.feature.scraper.crawler.frontier.DistributedCrawlFrontier;
import com.elyadata.feature.scraper.crawler.frontier.DistributedCrawlFrontier.FrontierEntry;
import com.elyadata.feature.scraper.crawler.url.LinkHarvester;
import com.elyadata.feature.scraper.crawler.url.UrlFilter;
import com.elyadata.feature.scraper.crawler.url.UrlNormalizer;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractionOutput;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.pipeline.PipelineResult;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.resilience.DomainCircuitBreaker;
import com.elyadata.feature.scraper.resilience.RetryExecutor;
import com.elyadata.feature.scraper.security.SsrfGuard;
import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class DistributedCrawlCoordinator {

    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    private final DistributedCrawlFrontier frontier;
    private final RobotsTxtGate robotsGate;
    private final SsrfGuard ssrfGuard;
    private final CircuitBreakerRegistry cbRegistry;
    private final ObjectMapper objectMapper;
    private final CrawlResultPersister persister;
    private final StreamConfig config;
    private final CrawlJoinDispatcher joinDispatcher;

    @ConfigProperty(name = "scraper.crawl.distributed.batch-size", defaultValue = "5")
    int batchSize;
    @ConfigProperty(name = "scraper.crawl.distributed.poll-delay-ms", defaultValue = "500")
    long pollDelayMs;
    @ConfigProperty(name = "scraper.crawl.distributed.reclaim-min-idle-ms", defaultValue = "120000")
    long reclaimMinIdleMs;
    @ConfigProperty(name = "scraper.crawl.distributed.reclaim-every-ms", defaultValue = "30000")
    long reclaimEveryMs;
    @ConfigProperty(name = "scraper.crawl.distributed.max-cb-retries", defaultValue = "5")
    int maxCbRetries;

    public DistributedCrawlCoordinator(DistributedCrawlFrontier frontier, RobotsTxtGate robotsGate,
                                       SsrfGuard ssrfGuard, CircuitBreakerRegistry cbRegistry,
                                       ObjectMapper objectMapper, CrawlResultPersister persister,
                                       StreamConfig config, CrawlJoinDispatcher joinDispatcher) {
        this.frontier = frontier;
        this.robotsGate = robotsGate;
        this.ssrfGuard = ssrfGuard;
        this.cbRegistry = cbRegistry;
        this.objectMapper = objectMapper;
        this.persister = persister;
        this.config = config;
        this.joinDispatcher = joinDispatcher;
    }

    public PipelineResult run(InternalScrapeRequest request, ABrowser browser, BaseExtractor extractor) {
        String consumerId = newConsumerId();
        UUID jobId = request.getJobId();
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> logs = new ArrayList<>();

        frontier.ensureGroup(jobId);
        seedFrontier(request, logs);
        publishJoins(request);

        UrlFilter filter = new UrlFilter(request, ssrfGuard);
        log.info("[DCRAWL] job={} consumer={} — drain PRINCIPAL", jobId, consumerId);

        drainWithBrowser(consumerId, request, browser, extractor, filter, errors, logs);
        return finalizeAndPersist(request, consumerId, start, errors, logs, browser);
    }

    public void joinExistingCrawl(InternalScrapeRequest request, ABrowser browser, BaseExtractor extractor) {
        String consumerId = newConsumerId();
        UUID jobId = request.getJobId();
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> logs = new ArrayList<>();

        frontier.ensureGroup(jobId);
        UrlFilter filter = new UrlFilter(request, ssrfGuard);
        log.info("[DCRAWL] job={} consumer={} — drain RENFORT", jobId, consumerId);

        drainWithBrowser(consumerId, request, browser, extractor, filter, errors, logs);

        long duration = System.currentTimeMillis() - start;
        logs.add("[INFO] Renfort termine — finalisation laissee au noeud principal");
        log.info("[DCRAWL] job={} consumer={} — renfort termine (no finalize)", jobId, consumerId);
        emptyCompletedResult(errors, logs, duration, browser);
    }

    private void drainWithBrowser(String consumerId, InternalScrapeRequest request, ABrowser browser,
                                  BaseExtractor extractor, UrlFilter filter,
                                  List<String> errors, List<String> logs) {
        try (browser) {
            browser.start();
            drain(consumerId, request, browser, extractor, filter, errors, logs);
        } catch (Exception e) {
            log.warn("[DCRAWL] browser close failed job={}: {}", request.getJobId(), e.getMessage());
        }
    }

    private void publishJoins(InternalScrapeRequest request) {
        int parallelism = config.crawlJoin().parallelism();
        int joins = Math.max(0, parallelism - 1);
        if (joins == 0) return;
        try {
            joinDispatcher.publish(request.getJobId(), joins);
        } catch (Exception e) {
            log.warn("[DCRAWL] job={} — publication renforts echouee (crawl continue en solo): {}",
                    request.getJobId(), e.getMessage());
        }
    }

    private void seedFrontier(InternalScrapeRequest request, List<String> logs) {
        UUID jobId = request.getJobId();
        UrlNormalizer.normalize(request.getTargetUrl(), request.getTargetUrl())
                .ifPresent(u -> frontier.seed(jobId, new CrawlNode(u, 0, null)));
        if (request.getSeedUrls() != null) {
            for (String s : request.getSeedUrls()) {
                UrlNormalizer.normalize(s, request.getTargetUrl())
                        .ifPresent(u -> frontier.seed(jobId, new CrawlNode(u, 0, null)));
            }
        }
        logs.add("[INFO] Frontier seeded for job=" + jobId);
    }

    private void drain(String consumerId, InternalScrapeRequest request, ABrowser browser, BaseExtractor extractor,
                       UrlFilter filter, List<String> errors, List<String> logs) {
        UUID jobId = request.getJobId();
        long lastReclaim = 0;

        while (shouldContinueDraining(jobId, consumerId, request, logs)) {
            if (frontier.frontierExists(jobId)
                    && System.currentTimeMillis() - lastReclaim > reclaimEveryMs) {
                for (FrontierEntry e : frontier.reclaim(jobId, consumerId, Duration.ofMillis(reclaimMinIdleMs), batchSize)) {
                    if (!frontier.underPageCap(jobId, request.getMaxPages())) {
                        break;
                    }
                    processOne(e, request, browser, extractor, filter, errors, logs);
                }
                lastReclaim = System.currentTimeMillis();
            }

            List<FrontierEntry> batch = frontier.claim(jobId, consumerId, batchSize);
            if (batch.isEmpty()) {
                if (!handleEmptyBatch(jobId, consumerId, logs)) {
                    break;
                }
            } else {
                processBatch(batch, request, browser, extractor, filter, errors, logs);
            }
        }
    }

    private boolean shouldContinueDraining(UUID jobId, String consumerId, InternalScrapeRequest request,
                                           List<String> logs) {
        if (!frontier.frontierExists(jobId)) {
            logs.add("[INFO] Frontier disparue (crawl finalise par le principal) — arret du drain");
            log.info("[DCRAWL] job={} consumer={} — frontier absente, stop drain (finalise ailleurs)",
                    jobId, consumerId);
            return false;
        }
        if (!frontier.underPageCap(jobId, request.getMaxPages())) {
            logs.add("[INFO] maxPages=" + request.getMaxPages() + " atteint — arret du drain");
            log.info("[DCRAWL] job={} maxPages={} atteint — stop drain", jobId, request.getMaxPages());
            return false;
        }
        return true;
    }

    private boolean handleEmptyBatch(UUID jobId, String consumerId, List<String> logs) {
        if (!frontier.frontierExists(jobId)) {
            logs.add("[INFO] Frontier disparue pendant le claim — arret du drain");
            log.info("[DCRAWL] job={} consumer={} — frontier absente apres claim, stop drain", jobId, consumerId);
            return false;
        }
        if (frontier.isQuiescent(jobId)) {
            return false;
        }
        sleep(pollDelayMs);
        return true;
    }

    private void processBatch(List<FrontierEntry> batch, InternalScrapeRequest request, ABrowser browser,
                              BaseExtractor extractor, UrlFilter filter,
                              List<String> errors, List<String> logs) {
        UUID jobId = request.getJobId();
        for (FrontierEntry e : batch) {
            if (!frontier.frontierExists(jobId) || !frontier.underPageCap(jobId, request.getMaxPages())) {
                log.info("[DCRAWL] job={} — arrêt mid-batch (frontier absente ou cap atteint)", jobId);
                return;
            }
            processOne(e, request, browser, extractor, filter, errors, logs);
        }
    }

    private void processOne(FrontierEntry entry, InternalScrapeRequest request, ABrowser browser,
                            BaseExtractor extractor, UrlFilter filter, List<String> errors, List<String> logs) {
        UUID jobId = request.getJobId();
        CrawlNode node = entry.node();
        String url = node.url();
        if (!frontier.frontierExists(jobId)) {
            log.info("[DCRAWL] job={} — frontier absente, abandon de la page {} (crawl finalisé)", jobId, url);
            return;
        }
        boolean shouldComplete = true;
        try {
            if (!passesPreChecks(url, request, errors, logs)) {
                return;
            }
            DomainCircuitBreaker cb = resolveCb(url, request);
            if (cb != null && !cb.allowRequest()) {
                long attempts = frontier.recordUrlAttempt(jobId, url);
                if (attempts >= maxCbRetries) {
                    errors.add("Circuit breaker still OPEN after " + attempts
                            + " attempts — giving up on " + url);
                    log.warn("[DCRAWL] job={} url={} — CB toujours OPEN après {} tentatives, abandon",
                            jobId, url, attempts);
                } else {
                    errors.add("Circuit breaker OPEN — skipping " + url
                            + " (tentative " + attempts + "/" + maxCbRetries + ")");
                    shouldComplete = false;
                }
                return;
            }

            PageSnapshot snap = fetchSnapshotGuarded(node, request, browser, cb, url, errors);
            if (snap == null) {
                return;
            }

            ExtractionOutput out = extractor.extract(snap, request);
            if (out.records() != null && !out.records().isEmpty()) {
                frontier.saveRecords(jobId, url, writeJson(out.records()));
            }
            long pages = frontier.incrementPages(jobId);
            logs.add(String.format("[INFO] page=%d url=%s records=%d",
                    pages, url, out.records() == null ? 0 : out.records().size()));

            harvestChildLinks(node, url, snap, request, filter, logs);
        } catch (Exception e) {
            errors.add("Unexpected error at " + url + ": " + e.getMessage());
            log.warn("[DCRAWL] error url={}: {}", url, e.getMessage());
        } finally {
            if (shouldComplete) {
                frontier.complete(jobId, url, entry.messageId());
            } else {
                log.info("[DCRAWL] job={} url={} — circuit breaker OPEN, laissé en PEL pour reprise", jobId, url);
            }
        }
    }

    private boolean passesPreChecks(String url, InternalScrapeRequest request,
                                    List<String> errors, List<String> logs) {
        if (request.isRespectRobotsTxt() && !robotsGate.isAllowed(url)) {
            logs.add("[WARNING] robots.txt blocked: " + url);
            return false;
        }
        if (ssrfGuard != null && !ssrfGuard.isAllowed(url)) {
            errors.add("SSRF blocked: " + url);
            return false;
        }
        return true;
    }

    private PageSnapshot fetchSnapshotGuarded(CrawlNode node, InternalScrapeRequest request, ABrowser browser,
                                              DomainCircuitBreaker cb, String url, List<String> errors) {
        try {
            PageSnapshot snap = fetchSnapshot(node, request, browser);
            if (cb != null) cb.recordSuccess();
            return snap;
        } catch (Infrastructure5xxException e) {
            if (cb != null) cb.recordFailure();
            errors.add("Crawl failed at " + url + ": " + e.getMessage());
            return null;
        }
    }

    private void harvestChildLinks(CrawlNode node, String url, PageSnapshot snap, InternalScrapeRequest request,
                                   UrlFilter filter, List<String> logs) {
        UUID jobId = request.getJobId();
        if (node.depth() >= request.getMaxDepth() || !frontier.underPageCap(jobId, request.getMaxPages())) {
            return;
        }
        int added = 0;
        for (String link : LinkHarvester.harvest(snap)) {
            if (filter.accepts(link) && frontier.expand(jobId, node.child(link), request.getMaxDepth())) {
                added++;
            }
        }
        if (added > 0) {
            logs.add(String.format("[INFO] url=%s discovered=%d new URLs", url, added));
        }
    }

    private PageSnapshot fetchSnapshot(CrawlNode node, InternalScrapeRequest request, ABrowser browser) {
        return RetryExecutor.execute(() -> navigateAndSnapshot(node, request, browser),
                request.getRetryPolicy(), "dcrawl:" + node.url(), log);
    }

    private PageSnapshot navigateAndSnapshot(CrawlNode node, InternalScrapeRequest request, ABrowser browser) {
        try {
            browser.navigate(node.url());
            browser.dismissCookies();
            if (request.getWaitForSelector() != null) browser.waitForSelector(request.getWaitForSelector());
            if (request.isScrollToBottom()) browser.scroll(true);
            return browser.snapshot();
        } catch (Infrastructure5xxException e) {
            throw e;
        } catch (Exception e) {
            throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                    .message("Browser error at " + node.url() + ": " + e.getMessage())
                    .uriPath(node.url()).subErrors(Set.of()).build();
        }
    }

    private DomainCircuitBreaker resolveCb(String url, InternalScrapeRequest request) {
        boolean enabled = request.getRetryPolicy() != null
                && Boolean.TRUE.equals(request.getRetryPolicy().circuitBreakerEnabled());
        return enabled ? cbRegistry.getOrCreate(url, request.getRetryPolicy()) : null;
    }

    private PipelineResult finalizeAndPersist(InternalScrapeRequest request, String consumerId,
                                              long start, List<String> errors, List<String> logs, ABrowser browser) {
        UUID jobId = request.getJobId();
        if (!frontier.tryBecomeFinalizer(jobId)) {
            log.info("[DCRAWL] job={} consumer={} — finalisation deja prise, skip", jobId, consumerId);
            long duration = System.currentTimeMillis() - start;
            logs.add("[INFO] Finalisation deleguee a un autre worker");
            return emptyCompletedResult(errors, logs, duration, browser);
        }

        log.info("[DCRAWL] job={} consumer={} — elu FINALIZER", jobId, consumerId);
        PipelineResult result = aggregate(request, start, errors, logs, browser);

        try {
            persister.persistAndDispatch(jobId, result);
        } catch (Exception e) {
            log.error("[DCRAWL] job={} — persist ECHOUE, cleanup ANNULE (records preserves pour reprise): {}",
                    jobId, e.getMessage(), e);
            throw e;
        }
        safeCleanup(jobId);
        return result;
    }

    private PipelineResult aggregate(InternalScrapeRequest request, long start,
                                     List<String> errors, List<String> logs, ABrowser browser) {
        UUID jobId = request.getJobId();
        List<Map<String, Object>> records = new ArrayList<>();
        for (String v : frontier.readAllRecords(jobId).values()) {
            try {
                records.addAll(readJson(v));
            } catch (Exception e) {
                log.warn("[DCRAWL] records parse failed: {}", e.getMessage());
            }
        }

        List<DiscoveredFile> files = discoverFiles(request);

        long pages = frontier.pagesCount(jobId);
        long duration = System.currentTimeMillis() - start;
        String status = records.isEmpty() ? STATUS_FAILED : STATUS_COMPLETED;
        if (records.isEmpty()) errors.add("Distributed crawl produced no records");

        logs.add(String.format("[INFO] Distributed crawl finished — status=%s pages=%d records=%d duration=%dms",
                status, pages, records.size(), duration));
        log.info("[DCRAWL] job={} finished status={} pages={} records={} duration={}ms",
                jobId, status, pages, records.size(), duration);

        return new PipelineResult(status, records, errors, logs, (int) pages, duration, resolveEngine(browser), null, files);
    }

    private List<DiscoveredFile> discoverFiles(InternalScrapeRequest request) {
        try {
            return new SiteFileDiscoverer().discover(request.getTargetUrl());
        } catch (Exception e) {
            return List.of();
        }
    }

    private void safeCleanup(UUID jobId) {
        try {
            frontier.cleanup(jobId);
            log.info("[DCRAWL] job={} — frontier Redis nettoyee", jobId);
        } catch (Exception e) {
            log.warn("[DCRAWL] job={} — cleanup frontier echoue: {}", jobId, e.getMessage());
        }
    }

    private PipelineResult emptyCompletedResult(List<String> errors, List<String> logs,
                                                long duration, ABrowser browser) {
        return new PipelineResult(STATUS_COMPLETED, List.of(), errors, logs,
                0, duration, resolveEngine(browser), null, List.of());
    }

    private static String resolveEngine(ABrowser browser) {
        return browser.getClass().getSimpleName().replace("Browser", "").toLowerCase();
    }

    private String writeJson(List<Map<String, Object>> recs) {
        try {
            return objectMapper.writeValueAsString(recs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("records serialization failed", e);
        }
    }

    private List<Map<String, Object>> readJson(String s) throws Exception {
        return objectMapper.readValue(s, new TypeReference<>() {
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String newConsumerId() {
        String host = System.getenv().getOrDefault("HOSTNAME", "node");
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}