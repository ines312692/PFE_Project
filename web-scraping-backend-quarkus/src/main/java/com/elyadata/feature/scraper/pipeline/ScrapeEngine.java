package com.elyadata.feature.scraper.pipeline;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.application.dto.SiteProbeResponse;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.model.SiteSignal;
import com.elyadata.feature.job.domain.service.SiteProbeService;
import com.elyadata.feature.job.domain.service.port.JobDispatchPort;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyOutcome;
import com.elyadata.feature.proxy.domain.service.ProxyPoolService;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.service.LlmExtractionService;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaHandler;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.BrowserFactory;
import com.elyadata.feature.scraper.browser.PlaywrightBrowser;
import com.elyadata.feature.scraper.crawler.discovery.SiteFileDiscoverer;
import com.elyadata.feature.scraper.crawler.pagination.PaginationStrategyRegistry;
import com.elyadata.feature.scraper.crawler.pipeline.CrawlPipeline;
import com.elyadata.feature.scraper.crawler.pipeline.DistributedCrawlCoordinator;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractorRegistry;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.security.SsrfGuard;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ScrapeEngine {

    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    private static final String UNKNOWN_ENGINE = "unknown";
    private static final String PAGE_SEPARATOR = " ===\n";
    private static final int MIN_REAL_HTML_LENGTH = 5_000;
    private static final String PROXY_BLOCKED_MARKER = "Proxy blocked by target site";
    private static final String DEAD_PROXY_MARKER = "DEAD_PROXY";
    private final JobDispatchPort jobDispatchPort;

    private static final Set<String> DEAD_PROXY_ERRORS = Set.of(
            "ERR_PROXY_CONNECTION_FAILED",
            "ERR_SOCKS_CONNECTION_FAILED",
            "ERR_CONNECTION_REFUSED",
            "ERR_CONNECTION_TIMED_OUT",
            "ERR_TUNNEL_CONNECTION_FAILED"
    );

    private static final Set<SiteSignal> SELENIUM_SIGNALS = Set.of(
            SiteSignal.CLOUDFLARE,
            SiteSignal.DATADOME,
            SiteSignal.AKAMAI,
            SiteSignal.LIKELY_BLOCKED
    );

    private final BrowserFactory browserFactory;
    private final ExtractorRegistry extractorRegistry;
    private final ScrapeResultPort scrapeResultPort;
    private final JobPort jobPort;
    private final NotificationService notificationService;
    private final ExtractionSchemaPort schemaPort;
    private final LlmExtractionService llmExtractionService;
    private final SiteProbeService siteProbeService;
    private final CircuitBreakerRegistry cbRegistry;
    private final RobotsTxtGate robotsGate;
    private final PaginationStrategyRegistry paginationRegistry;
    private final ProxyPoolService proxyPoolService;
    private final CaptchaHandler captchaHandler;
    private final SsrfGuard ssrfGuard;
    private final DistributedCrawlCoordinator distributedCrawlCoordinator;

    @ConfigProperty(name = "scraper.cloudflare-domains",
            defaultValue = "carrefour.fr,carrefour.com,auchan.fr")
    List<String> cloudflareDomains;

    @ConfigProperty(name = "proxy.max-rotations", defaultValue = "3")
    int maxProxyRotations;

    @ConfigProperty(name = "scraper.crawl.distributed.enabled", defaultValue = "false")
    boolean distributedCrawl;


    public void runScrape(InternalScrapeRequest request) {
        log.info("[ENGINE] job={} type={} mode={} url={} retry={} proxy={}",
                request.getJobId(), request.getJobType(),
                request.getExtractionMode(), request.getTargetUrl(),
                describeRetry(request),
                describeProxyConfig(request.getProxyConfig()));

        SsrfGuard.SsrfVerdict verdict = ssrfGuard.evaluate(request.getTargetUrl());
        if (!verdict.allowed()) {
            log.warn("[ENGINE] SSRF guard blocked target job={} url={} — {}",
                    request.getJobId(), request.getTargetUrl(), verdict.reason());
            PipelineResult blocked = failureResult("Blocked by SSRF guard: " + verdict.reason());
            persistResult(request, blocked, List.of(), STATUS_FAILED);
            return;
        }

        boolean proxyEnabled = request.getProxyConfig() != null
                && request.getProxyConfig().isEnabled();

        if (isDistributedCrawlerResult(request)) {
            runOneAttempt(request, acquireSingleProxy(request, proxyEnabled), true);
            return;
        }

        if (!proxyEnabled) {
            runOneAttempt(request, Optional.empty(), true);
            return;
        }

        runWithProxyRotation(request);
    }

    private Optional<Proxy> acquireSingleProxy(InternalScrapeRequest request, boolean proxyEnabled) {
        if (!proxyEnabled) return Optional.empty();
        try {
            return proxyPoolService.acquire(request.getProxyConfig(), request.getJobId(), new ArrayList<>());
        } catch (Exception e) {
            log.warn("[ENGINE] proxy acquire failed (distributed) job={}: {}", request.getJobId(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isDistributedCrawlerResult(InternalScrapeRequest request) {
        return distributedCrawl && JobType.CRAWLER.equals(request.getJobType());
    }


    private void runWithProxyRotation(InternalScrapeRequest request) {
        List<UUID> tried = new ArrayList<>();

        for (int attempt = 1; attempt <= maxProxyRotations; attempt++) {
            Optional<Proxy> acquired = proxyPoolService.acquire(
                    request.getProxyConfig(), request.getJobId(), tried);

            if (acquired.isEmpty()) {
                log.warn("[ENGINE] No more proxies available for job={} (already tried {}) — falling back to direct",
                        request.getJobId(), tried.size());
                runOneAttempt(request, Optional.empty(), true);
                return;
            }

            tried.add(acquired.get().id());
            cbRegistry.reset(request.getTargetUrl());

            log.info("[ENGINE] Proxy attempt {}/{} for job={} → {} (already tried: {})",
                    attempt, maxProxyRotations, request.getJobId(),
                    acquired.get().endpoint(), tried.size() - 1);

            boolean isLastAttempt = (attempt == maxProxyRotations);
            AttemptOutcome outcome = runOneAttempt(request, acquired, isLastAttempt);

            if (!outcome.proxyBlocked()) {
                return;
            }

            String reason = outcome.deadProxy() ? "dead (connection failed)" : "blocked by target site";
            log.warn("[ENGINE] Proxy {} {} for job={} — rotating ({}/{})",
                    acquired.get().endpoint(), reason, request.getJobId(), attempt, maxProxyRotations);
        }

        log.error("[ENGINE] All {} proxy rotation(s) exhausted for job={}",
                maxProxyRotations, request.getJobId());
    }

    private AttemptOutcome runOneAttempt(InternalScrapeRequest request,
                                         Optional<Proxy> acquired,
                                         boolean persistOnBlock) {
        long startMs = System.currentTimeMillis();
        InternalScrapeRequest enriched = enrichWithProxy(request, acquired);

        PipelineResult result = null;
        Exception failure = null;
        boolean blocked = false;
        boolean dead = false;

        try {
            result = executePipeline(enriched);
            blocked = isProxyBlocked(result, acquired);
            dead = blocked && isDeadProxy(result);
            if ((!blocked || persistOnBlock) && !isDistributedCrawlerResult(request)) {
                List<Map<String, Object>> finalRecs = resolveLlmRecords(request.getJobId(), result);
                String finalStatus = resolveFinalStatus(result.status(), finalRecs);
                persistResult(request, result, finalRecs, finalStatus);
            }
        } catch (Exception e) {
            failure = e;
            if (acquired.isEmpty()) throw e;
            log.warn("[ENGINE] Pipeline crashed with proxy={} job={}: {}",
                    acquired.get().id(), request.getJobId(), e.getMessage());
        } finally {
            releaseProxy(acquired, request.getJobId(), result, failure, startMs, dead);
        }

        return new AttemptOutcome(blocked, dead);
    }


    private static boolean isProxyBlocked(PipelineResult result, Optional<Proxy> proxy) {
        if (proxy.isEmpty() || result == null) return false;
        if (!STATUS_FAILED.equals(result.status())) return false;
        return result.errors() != null && result.errors().stream()
                .anyMatch(e -> e != null &&
                        (e.contains(PROXY_BLOCKED_MARKER) || containsDeadProxyError(e)));
    }

    private static boolean isDeadProxy(PipelineResult result) {
        if (result == null || result.errors() == null) return false;
        return result.errors().stream()
                .filter(Objects::nonNull)
                .anyMatch(ScrapeEngine::containsDeadProxyError);
    }

    private static boolean containsDeadProxyError(String error) {
        return DEAD_PROXY_ERRORS.stream().anyMatch(error::contains);
    }

    private static InternalScrapeRequest enrichWithProxy(InternalScrapeRequest request,
                                                         Optional<Proxy> proxy) {
        return proxy.map(p -> request.toBuilder().proxy(p).build()).orElse(request);
    }

    private void releaseProxy(Optional<Proxy> proxy, UUID jobId,
                              PipelineResult result, Exception failure,
                              long startMs, boolean dead) {
        if (proxy.isEmpty()) return;
        Proxy p = proxy.get();
        int latencyMs = (int) (System.currentTimeMillis() - startMs);
        ProxyOutcome outcome = buildOutcome(result, failure, latencyMs, dead);

        try {
            proxyPoolService.release(p.id(), jobId, outcome);
        } catch (Exception e) {
            log.warn("[ENGINE] Proxy release failed for proxy={} job={}: {}",
                    p.id(), jobId, e.getMessage());
        }
    }

    private static ProxyOutcome buildOutcome(PipelineResult result, Exception failure,
                                             int latencyMs, boolean dead) {
        if (failure != null) {
            return ProxyOutcome.builder()
                    .success(false)
                    .latencyMs(latencyMs)
                    .errorMessage(truncate(failure.getMessage()))
                    .build();
        }

        if (dead) {
            log.warn("[ENGINE] Proxy is dead (connection refused) — marking for ban");
            return ProxyOutcome.builder()
                    .success(false)
                    .latencyMs(latencyMs)
                    .errorMessage(DEAD_PROXY_MARKER)
                    .build();
        }

        boolean ok = result != null && STATUS_COMPLETED.equals(result.status());
        return ok
                ? ProxyOutcome.success(latencyMs)
                : ProxyOutcome.failure("Pipeline returned non-completed status");
    }

    private PipelineResult executePipeline(InternalScrapeRequest request) {
        ABrowser browser = null;
        try {
            browser = browserFactory.create(request);
            BaseExtractor extractor = extractorRegistry.get(request.getExtractionMode());
            PipelineResult result = runByJobType(request, browser, extractor);

            if (request.getProxy() != null && isBlockedPage(result)) {
                log.warn("[ENGINE] Proxy job={} returned a blocked/challenge page (html={}b)",
                        request.getJobId(), estimateHtmlLength(result));
                return failureResult(PROXY_BLOCKED_MARKER
                        + " — challenge page detected (html < 5000b or empty text). "
                        + "Will rotate proxy if attempts remain.");
            }

            return result;

        } catch (Infrastructure5xxException e) {
            log.error("[ENGINE] Infrastructure error for job={}: {}",
                    request.getJobId(), e.getMessage());
            markPoisoned(browser);
            return failureResult(e.getMessage());

        } catch (Exception e) {
            log.error("[ENGINE] Unexpected error for job={}", request.getJobId(), e);
            markPoisoned(browser);
            throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                    .message("Unexpected scraper failure for job "
                            + request.getJobId() + ": " + e.getMessage())
                    .uriPath("scrape-engine/job/" + request.getJobId())
                    .subErrors(Set.of())
                    .build();

        } finally {
            closeQuietly(browser);
        }
    }

    private static void markPoisoned(ABrowser browser) {
        if (browser instanceof PlaywrightBrowser pw) {
            pw.markPoisoned();
        }
    }

    private static void closeQuietly(ABrowser browser) {
        if (browser == null) {
            return;
        }
        try {
            browser.close();
        } catch (Exception e) {
            log.warn("[ENGINE] Browser close failed: {}", e.getMessage());
        }
    }

    private boolean isBlockedPage(PipelineResult result) {
        int totalHtmlLength = estimateHtmlLength(result);
        int totalTextLength = estimateTextLength(result);

        return result.records() == null
                || result.records().isEmpty()
                || (totalHtmlLength < MIN_REAL_HTML_LENGTH && totalTextLength == 0)
                || (totalHtmlLength > 50_000 && totalTextLength * 200 < totalHtmlLength);
    }

    private static int estimateTextLength(PipelineResult result) {
        if (result.records() == null) return 0;
        return result.records().stream()
                .mapToInt(r -> {
                    Object v = r.get("text_length");
                    return v instanceof Number num ? num.intValue() : 0;
                })
                .sum();
    }

    private static int estimateHtmlLength(PipelineResult result) {
        if (result.records() == null) return 0;
        return result.records().stream()
                .mapToInt(r -> {
                    Object v = r.get("html_length");
                    return v instanceof Number num ? num.intValue() : 0;
                })
                .sum();
    }

    private PipelineResult runByJobType(InternalScrapeRequest request,
                                        ABrowser browser, BaseExtractor extractor) {
        if (JobType.CRAWLER.equals(request.getJobType())) {
            if (distributedCrawl) {
                log.info("[ENGINE] Routing job={} → DistributedCrawlCoordinator", request.getJobId());
                return distributedCrawlCoordinator.run(request, browser, extractor);
            }
            log.info("[ENGINE] Routing job={} → CrawlPipeline", request.getJobId());
            return new CrawlPipeline(browser, extractor, cbRegistry, robotsGate,
                    new SiteFileDiscoverer(), paginationRegistry, ssrfGuard)
                    .run(request);
        }
        log.info("[ENGINE] Routing job={} → ScrapePipeline", request.getJobId());
        return new ScrapePipeline(browser, extractor, cbRegistry, paginationRegistry, captchaHandler)
                .run(request);
    }

    private static PipelineResult failureResult(String message) {
        return new PipelineResult(
                STATUS_FAILED, List.of(), List.of(message), List.of(),
                0, 0L, UNKNOWN_ENGINE, null, List.of());
    }

    private boolean requiresSeleniumByProbe(String targetUrl) {
        try {
            return evaluateProbeResult(siteProbeService.probe(targetUrl), targetUrl);
        } catch (Infrastructure5xxException e) {
            log.warn("[ENGINE] Probe error for {} — falling back to domain list: {}",
                    targetUrl, e.getMessage());
            return isKnownCloudflareDomain(targetUrl);
        } catch (Exception e) {
            log.warn("[ENGINE] Probe unexpected error for {} — falling back to domain list: {}",
                    targetUrl, e.getMessage());
            return isKnownCloudflareDomain(targetUrl);
        }
    }

    private boolean evaluateProbeResult(SiteProbeResponse probe, String targetUrl) {
        if (!probe.reachable()) {
            log.warn("[ENGINE] Probe unreachable for {} — falling back to domain list", targetUrl);
            return isKnownCloudflareDomain(targetUrl);
        }
        return probe.signals().stream().anyMatch(SELENIUM_SIGNALS::contains);
    }

    private boolean isKnownCloudflareDomain(String url) {
        return cloudflareDomains.contains(extractDomain(url));
    }

    private List<Map<String, Object>> resolveLlmRecords(UUID jobId, PipelineResult result) {
        if (result.rawContent() == null || result.rawContent().isBlank()) {
            return safeRecords(result);
        }
        ExtractionSchema schema = schemaPort.findByJobId(jobId).orElse(null);
        if (!hasValidLlmPrompt(schema)) {
            log.warn("[ENGINE] raw_content received but no LLM prompt for job={}", jobId);
            return safeRecords(result);
        }
        return processLlmPages(result.rawContent(), schema, jobId);
    }

    private boolean hasValidLlmPrompt(ExtractionSchema schema) {
        return schema != null && schema.llmPrompt() != null && !schema.llmPrompt().isBlank();
    }

    private static List<Map<String, Object>> safeRecords(PipelineResult result) {
        return result.records() != null ? result.records() : List.of();
    }

    private List<Map<String, Object>> processLlmPages(String rawContent,
                                                      ExtractionSchema schema, UUID jobId) {
        String[] parts = rawContent.split("(?==== PAGE: )");
        String modelKey = schema.llmModel() != null ? schema.llmModel().name() : null;
        List<Map<String, Object>> allRecords = new ArrayList<>();

        log.info("[ENGINE] LLM extraction: {} page(s) à traiter pour job={}", parts.length, jobId);

        for (String part : parts) {
            parsePageContent(part).ifPresent(
                    content -> processOneLlmPage(content, schema, modelKey, jobId, allRecords));
        }

        log.info("[ENGINE] LLM terminé: {} record(s) total pour job={}", allRecords.size(), jobId);
        return allRecords;
    }

    private static Optional<String> parsePageContent(String part) {
        if (part.isBlank()) return Optional.empty();
        String content = part.contains(PAGE_SEPARATOR)
                ? part.substring(part.indexOf(PAGE_SEPARATOR) + PAGE_SEPARATOR.length()).strip()
                : part.strip();
        return content.isBlank() ? Optional.empty() : Optional.of(content);
    }

    private void processOneLlmPage(String pageContent, ExtractionSchema schema,
                                   String modelKey, UUID jobId,
                                   List<Map<String, Object>> allRecords) {
        log.info("[ENGINE] LLM appel page {} chars pour job={}", pageContent.length(), jobId);

        List<Map<String, Object>> pageRecords = llmExtractionService.extract(
                pageContent, schema.llmPrompt(), schema.llmOutputFormat(), modelKey, jobId, null);

        allRecords.addAll(pageRecords);
        log.info("[ENGINE] LLM → {} record(s) cette page, total={} job={}",
                pageRecords.size(), allRecords.size(), jobId);
    }

    private void persistResult(InternalScrapeRequest request, PipelineResult result,
                               List<Map<String, Object>> finalRecords, String finalStatus) {

        scrapeResultPort.save(ScrapeResult.builder()
                .jobId(request.getJobId())
                .status(finalStatus)
                .records(finalRecords)
                .errors(result.errors())
                .logs(result.logs())
                .pagesCrawled(result.pagesCrawled())
                .durationMs(result.durationMs())
                .engineUsed(result.engineUsed())
                .scrapedAt(Instant.now())
                .discoveredFiles(result.discoveredFiles() != null
                        ? result.discoveredFiles() : List.of())
                .build());

        jobPort.findById(request.getJobId()).ifPresent(job -> {
            updateJobStatus(job, finalStatus);
            sendNotification(job, finalStatus, finalRecords.size(), result);
        });

        if (STATUS_COMPLETED.equals(finalStatus)) {
            jobDispatchPort.dispatchEtl(request.getJobId());
            log.info("[ENGINE] ETL dispatched for job={}", request.getJobId());
        }

    }

    private void updateJobStatus(Job job, String status) {
        JobStatus newStatus = STATUS_COMPLETED.equals(status) ? JobStatus.COMPLETED : JobStatus.FAILED;
        jobPort.update(job.toBuilder().status(newStatus).build());
        log.info("[ENGINE] Job {} status → {}", job.id(), newStatus);
    }

    private void sendNotification(Job job, String status, int recordCount, PipelineResult result) {
        if (job.createdBy() == null) return;

        boolean success = STATUS_COMPLETED.equals(status) && recordCount > 0;
        NotificationType type = success ? NotificationType.JOB_COMPLETED : NotificationType.JOB_FAILED;

        try {
            notificationService.notify(job.createdBy(), job.id(), type,
                    buildNotificationTitle(success, job.name()),
                    buildNotificationMessage(success, recordCount, result));
        } catch (Exception e) {
            log.warn("[ENGINE] Notification failed for job={}: {}", job.id(), e.getMessage());
        }
    }

    private static String buildNotificationTitle(boolean success, String jobName) {
        return (success ? "Job completed: " : "Job failed: ") + jobName;
    }

    private static String buildNotificationMessage(boolean success, int recordCount,
                                                   PipelineResult result) {
        return success
                ? recordCount + " records extracted in " + (result.durationMs() / 1000.0) + "s"
                : result.errors().size() + " error(s) during scraping";
    }

    private static String resolveFinalStatus(String pipelineStatus,
                                             List<Map<String, Object>> records) {
        return records.isEmpty() && STATUS_COMPLETED.equals(pipelineStatus)
                ? STATUS_FAILED : pipelineStatus;
    }

    private static String describeRetry(InternalScrapeRequest request) {
        return request.getRetryPolicy() != null
                ? "maxRetries=" + request.getRetryPolicy().maxRetries()
                : "none";
    }

    private static String describeProxyConfig(ProxyConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return "disabled";
        return "enabled(" + cfg.rotationStrategy() + ")";
    }

    private static String extractDomain(String url) {
        try {
            return URI.create(url).getHost().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private record AttemptOutcome(boolean proxyBlocked, boolean deadProxy) {
    }
}