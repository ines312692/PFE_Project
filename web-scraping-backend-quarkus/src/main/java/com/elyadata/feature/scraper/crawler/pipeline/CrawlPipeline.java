package com.elyadata.feature.scraper.crawler.pipeline;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.crawler.discovery.SiteFileDiscoverer;
import com.elyadata.feature.scraper.crawler.frontier.CrawlNode;
import com.elyadata.feature.scraper.crawler.frontier.UrlFrontier;
import com.elyadata.feature.scraper.crawler.pagination.PaginationStrategyRegistry;
import com.elyadata.feature.scraper.crawler.robots.RobotsTxtGate;
import com.elyadata.feature.scraper.crawler.url.LinkHarvester;
import com.elyadata.feature.scraper.crawler.url.UrlFilter;
import com.elyadata.feature.scraper.crawler.url.UrlNormalizer;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractionOutput;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.pipeline.PipelineResult;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.resilience.DomainCircuitBreaker;
import com.elyadata.feature.scraper.resilience.RetryExecutor;
import com.elyadata.feature.scraper.security.SsrfGuard;
import com.elyadata.feature.scraperesult.domain.model.DiscoveredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class CrawlPipeline {

    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    private static final String LOG_INFO = "[INFO] ";
    private static final String LOG_WARNING = "[WARNING] ";
    private static final String LOG_ERROR = "[ERROR] ";
    private static final String CTX_CRAWL = "crawl:";
    private static final String RAW_CONTENT_SUFFIX = " chars";
    private static final String NONE_DESC = "none";
    private static final String LOG_CRAWL = "[CRAWL] {}";
    private static final String LOG_CRAWL_RAW = "[CRAWL][RAW] depth={} url={} → {}" + RAW_CONTENT_SUFFIX + " capturés pour LLM";
    private static final String LOG_CRAWL_EXTRACT = "[CRAWL][EXTRACT] depth={} url={} → {} record(s) | rawContent=null";
    private static final String LOG_CRAWL_SEED = "[CRAWL][SEED] targetUrl={} offered={}";
    private static final String LOG_CRAWL_SEED_EXTRA = "[CRAWL][SEED] extraSeed={} offered={}";

    private final ABrowser browser;
    private final BaseExtractor extractor;
    private final CircuitBreakerRegistry cbRegistry;
    private final RobotsTxtGate robotsGate;
    private final SiteFileDiscoverer siteFileDiscoverer;
    private final PaginationStrategyRegistry paginationRegistry;
    private final SsrfGuard ssrfGuard;

    public PipelineResult run(InternalScrapeRequest request) {
        long startMs = System.currentTimeMillis();
        CrawlAccumulator acc = new CrawlAccumulator(resolveBrowserName());
        UrlFrontier frontier = new UrlFrontier(request.getMaxDepth());
        UrlFilter filter = new UrlFilter(request, ssrfGuard);

        seed(request, frontier);
        logCrawlStart(request, acc, frontier);

        if (frontier.isEmpty()) return earlyFailure(request, acc);

        browser.start();
        try {
            runCrawlLoop(request, frontier, filter, acc);
        } finally {
            browser.close();
        }

        List<DiscoveredFile> discoveredFiles = siteFileDiscoverer.discover(request.getTargetUrl());
        acc.logs.add(String.format("[INFO] Site files discovered: %d — %s",
                discoveredFiles.size(),
                discoveredFiles.stream().map(DiscoveredFile::fileType).toList()));

        return buildResult(request, acc, startMs, discoveredFiles);
    }

    private void runCrawlLoop(InternalScrapeRequest request, UrlFrontier frontier,
                              UrlFilter filter, CrawlAccumulator acc) {
        while (!frontier.isEmpty() && acc.pages < request.getMaxPages()) {
            CrawlNode node = frontier.poll().orElseThrow();
            processNode(node, request, frontier, filter, acc);
        }
    }

    private void processNode(CrawlNode node, InternalScrapeRequest request,
                             UrlFrontier frontier, UrlFilter filter, CrawlAccumulator acc) {
        if (isBlockedByRobots(node, request, acc)) return;
        if (!visitOne(node, request, frontier, filter, acc)) return;
        acc.pages++;

        if (acc.pages < request.getMaxPages()) sleep(request.getDelayMs());
    }

    private boolean visitOne(CrawlNode node, InternalScrapeRequest request,
                             UrlFrontier frontier, UrlFilter filter, CrawlAccumulator acc) {

        if (ssrfGuard != null && !ssrfGuard.isAllowed(node.url())) {
            String msg = "SSRF blocked: " + node.url();
            log.warn(LOG_CRAWL, msg);
            acc.errors.add(msg);
            acc.logs.add(LOG_WARNING + msg);
            return false;
        }

        DomainCircuitBreaker cb = resolveCircuitBreaker(node, request);
        if (cb != null && !cb.allowRequest()) {
            recordCircuitBreakerBlock(node, acc);
            return false;
        }

        try {
            PageSnapshot snapshot = fetchSnapshot(node, request);
            if (cb != null) cb.recordSuccess();
            processSnapshot(snapshot, node, request, frontier, filter, acc);
            return true;
        } catch (Infrastructure5xxException e) {
            recordVisitFailure(node, e, cb, acc);
            return false;
        }
    }

    private PageSnapshot fetchSnapshot(CrawlNode node, InternalScrapeRequest request) {
        return RetryExecutor.execute(
                () -> {
                    try {
                        browser.navigate(node.url());
                        browser.dismissCookies();
                        if (request.getWaitForSelector() != null)
                            browser.waitForSelector(request.getWaitForSelector());
                        if (request.isScrollToBottom()) browser.scroll(true);
                        return browser.snapshot();
                    } catch (Infrastructure5xxException e) {
                        throw e;
                    } catch (Exception e) {
                        throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                                .message("Browser error at " + node.url() + ": " + e.getMessage())
                                .uriPath(node.url())
                                .subErrors(Set.of())
                                .build();
                    }
                },
                request.getRetryPolicy(), CTX_CRAWL + node.url(), log);
    }

    private void processSnapshot(PageSnapshot snapshot, CrawlNode node,
                                 InternalScrapeRequest request, UrlFrontier frontier,
                                 UrlFilter filter, CrawlAccumulator acc) {
        ExtractionOutput out = extractor.extract(snapshot, request);
        acc.records.addAll(out.records());
        logExtraction(node, out, acc);

        if (node.depth() < request.getMaxDepth()) {
            enqueueDiscoveredLinks(snapshot, node, frontier, filter, acc);
        }
    }

    private void logExtraction(CrawlNode node, ExtractionOutput out, CrawlAccumulator acc) {
        if (out.rawContent() != null && !out.rawContent().isBlank()) {
            log.info(LOG_CRAWL_RAW, node.depth(), node.url(), out.rawContent().length());
            acc.logs.add(String.format(LOG_INFO + "depth=%d | url=%s | rawContent=%d" + RAW_CONTENT_SUFFIX + " (LLM mode)",
                    node.depth(), node.url(), out.rawContent().length()));
            acc.rawBuilder.append("=== PAGE: ").append(node.url()).append(" ===\n")
                    .append(out.rawContent()).append("\n\n");
        } else {
            log.info(LOG_CRAWL_EXTRACT, node.depth(), node.url(), out.records().size());
            acc.logs.add(String.format(LOG_INFO + "depth=%d | url=%s | records=%d",
                    node.depth(), node.url(), out.records().size()));
        }
    }

    private void enqueueDiscoveredLinks(PageSnapshot snapshot, CrawlNode parent,
                                        UrlFrontier frontier, UrlFilter filter,
                                        CrawlAccumulator acc) {
        int discovered = harvestAndEnqueue(snapshot, parent, frontier, filter);
        log.debug("[CRAWL] depth={} url={} → {} new URL(s) enqueued",
                parent.depth(), parent.url(), discovered);
        if (discovered > 0) {
            acc.logs.add(String.format(LOG_INFO + "depth=%d | url=%s | discovered=%d new URLs",
                    parent.depth(), parent.url(), discovered));
        }
    }

    private boolean isBlockedByRobots(CrawlNode node, InternalScrapeRequest request,
                                      CrawlAccumulator acc) {
        if (!request.isRespectRobotsTxt() || robotsGate.isAllowed(node.url())) return false;
        String msg = "robots.txt blocked: " + node.url();
        log.debug(LOG_CRAWL, msg);
        acc.logs.add(LOG_WARNING + msg);
        return true;
    }

    private DomainCircuitBreaker resolveCircuitBreaker(CrawlNode node,
                                                       InternalScrapeRequest request) {
        boolean cbEnabled = request.getRetryPolicy() != null
                && Boolean.TRUE.equals(request.getRetryPolicy().circuitBreakerEnabled());
        return cbEnabled ? cbRegistry.getOrCreate(node.url(), request.getRetryPolicy()) : null;
    }

    private void recordCircuitBreakerBlock(CrawlNode node, CrawlAccumulator acc) {
        String msg = "Circuit breaker OPEN — skipping " + node.url();
        log.warn(LOG_CRAWL, msg);
        acc.errors.add(msg);
        acc.logs.add(LOG_WARNING + msg);
    }

    private void recordVisitFailure(CrawlNode node, Infrastructure5xxException e,
                                    DomainCircuitBreaker cb, CrawlAccumulator acc) {
        String msg = "Crawl failed at " + node.url() + ": " + e.getMessage();
        log.error(LOG_CRAWL, msg);
        acc.errors.add(msg);
        acc.logs.add(LOG_ERROR + msg);
        if (cb != null) cb.recordFailure();
    }

    private void seed(InternalScrapeRequest request, UrlFrontier frontier) {
        UrlNormalizer.normalize(request.getTargetUrl(), request.getTargetUrl())
                .ifPresentOrElse(
                        url -> offerSeed(url, frontier, LOG_CRAWL_SEED),
                        () -> log.error("[CRAWL][SEED] targetUrl normalization échouée: {}",
                                request.getTargetUrl())
                );

        if (request.getSeedUrls() != null) {
            for (String seed : request.getSeedUrls()) {
                UrlNormalizer.normalize(seed, request.getTargetUrl())
                        .ifPresent(url -> offerSeed(url, frontier, LOG_CRAWL_SEED_EXTRA));
            }
        }
    }

    private void offerSeed(String url, UrlFrontier frontier, String logTemplate) {
        if (ssrfGuard != null && !ssrfGuard.isAllowed(url)) {
            log.warn("[CRAWL][SEED] SSRF blocked seed url={}", url);
            return;
        }
        boolean offered = frontier.offer(new CrawlNode(url, 0, null));
        log.info(logTemplate, url, offered);
    }

    private PipelineResult earlyFailure(InternalScrapeRequest request, CrawlAccumulator acc) {
        String msg = "Seed URL could not be normalized: " + request.getTargetUrl();
        log.error("[CRAWL] Frontier vide après seed");
        acc.logs.add(LOG_ERROR + msg);
        return new PipelineResult(STATUS_FAILED, List.of(), List.of(msg),
                acc.logs, 0, 0L, acc.browserName, null, List.of());
    }

    private PipelineResult buildResult(InternalScrapeRequest request,
                                       CrawlAccumulator acc, long startMs,
                                       List<DiscoveredFile> discoveredFiles) {
        long durationMs = System.currentTimeMillis() - startMs;
        String rawContent = acc.rawBuilder.isEmpty() ? null : acc.rawBuilder.toString();
        String status = determineCrawlStatus(rawContent, acc);

        acc.logs.add(String.format(
                LOG_INFO + "Crawl finished — status=%s pages=%d visited=%d records=%d rawContent=%s duration=%dms",
                status, acc.pages, request.getMaxPages(), acc.records.size(),
                describeRawContent(rawContent), durationMs));

        log.info("[CRAWL] Finished job={} status={} pages={} records={} rawContent={} duration={}ms",
                request.getJobId(), status, acc.pages, acc.records.size(),
                describeRawContent(rawContent), durationMs);

        return new PipelineResult(status, acc.records, acc.errors, acc.logs,
                acc.pages, durationMs, acc.browserName, rawContent, discoveredFiles);
    }

    private String determineCrawlStatus(String rawContent, CrawlAccumulator acc) {
        if (!acc.records.isEmpty() || rawContent != null) return STATUS_COMPLETED;
        String msg = "Crawl produced no records — check selectors / URL filters / depth";
        acc.errors.add(msg);
        acc.logs.add(LOG_ERROR + msg);
        return STATUS_FAILED;
    }

    private void logCrawlStart(InternalScrapeRequest request, CrawlAccumulator acc,
                               UrlFrontier frontier) {
        log.info("[CRAWL] job={} root={} maxPages={} maxDepth={} respectRobots={} frontierSize={}",
                request.getJobId(), request.getTargetUrl(),
                request.getMaxPages(), request.getMaxDepth(),
                request.isRespectRobotsTxt(), frontier.queuedCount());

        acc.logs.add(String.format(LOG_INFO + "Crawl started — root=%s maxPages=%d maxDepth=%d browser=%s",
                request.getTargetUrl(), request.getMaxPages(),
                request.getMaxDepth(), acc.browserName));
    }

    private String resolveBrowserName() {
        return browser.getClass().getSimpleName().replace("Browser", "").toLowerCase();
    }

    private static String describeRawContent(String rawContent) {
        return rawContent == null ? NONE_DESC : rawContent.length() + RAW_CONTENT_SUFFIX;
    }

    private int harvestAndEnqueue(PageSnapshot snapshot, CrawlNode parent,
                                  UrlFrontier frontier, UrlFilter filter) {
        int added = 0;
        for (String link : LinkHarvester.harvest(snapshot)) {
            if (filter.accepts(link) && frontier.offer(parent.child(link))) added++;
        }
        return added;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static class CrawlAccumulator {
        final List<Map<String, Object>> records = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final StringBuilder rawBuilder = new StringBuilder();
        final String browserName;
        int pages = 0;

        CrawlAccumulator(String browserName) {
            this.browserName = browserName;
        }
    }
}