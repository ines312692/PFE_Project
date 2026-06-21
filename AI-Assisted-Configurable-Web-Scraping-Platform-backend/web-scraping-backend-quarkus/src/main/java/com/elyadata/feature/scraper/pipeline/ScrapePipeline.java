package com.elyadata.feature.scraper.pipeline;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.domain.model.*;
import com.elyadata.feature.scraper.antibot.captcha.orchestration.CaptchaHandler;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.crawler.pagination.PaginationContext;
import com.elyadata.feature.scraper.crawler.pagination.PaginationStrategyRegistry;
import com.elyadata.feature.scraper.extractor.BaseExtractor;
import com.elyadata.feature.scraper.extractor.ExtractionOutput;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.resilience.CircuitBreakerRegistry;
import com.elyadata.feature.scraper.resilience.DomainCircuitBreaker;
import com.elyadata.feature.scraper.resilience.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class ScrapePipeline {

    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    private static final String LOG_INFO = "[INFO] ";
    private static final String LOG_WARNING = "[WARNING] ";
    private static final String LOG_ERROR = "[ERROR] ";
    private static final String CTX_NAVIGATE = "navigate:";
    private static final String CTX_EXTRACT = "extract:";
    private static final String RAW_CONTENT_SUFFIX = " chars";
    private static final String LOG_PIPELINE = "[PIPELINE] {}";

    private final ABrowser browser;
    private final BaseExtractor extractor;
    private final CircuitBreakerRegistry cbRegistry;
    private final PaginationStrategyRegistry paginationRegistry;
    private final CaptchaHandler captchaHandler;

    public PipelineResult run(InternalScrapeRequest request) {
        long startMs = System.currentTimeMillis();
        CrawlAccumulator acc = new CrawlAccumulator();
        RetryPolicy retryPolicy = request.getRetryPolicy();
        boolean cbEnabled = retryPolicy != null && retryPolicy.circuitBreakerEnabled();
        String browserName = resolveBrowserName();

        logPipelineStart(request, browserName, retryPolicy, cbEnabled, acc);

        browser.start();
        try {
            log.info("[PIPELINE] sessionConfig present={} usable={}",
                    request.getSessionConfig() != null,
                    request.getSessionConfig() != null && request.getSessionConfig().isUsable());
            if (request.getSessionConfig() != null && request.getSessionConfig().isUsable()) {
                try {
                    browser.authenticate(request.getSessionConfig());
                    acc.logLines.add(LOG_INFO + "Authenticated session (mode="
                            + request.getSessionConfig().mode() + ")");
                } catch (Exception e) {
                    String msg = "Login failed: " + e.getMessage();
                    acc.errors.add(msg);
                    acc.logLines.add(LOG_ERROR + msg);
                    long dur = System.currentTimeMillis() - startMs;
                    return new PipelineResult(STATUS_FAILED, acc.records, acc.errors, acc.logLines,
                            acc.pagesCrawled, dur, browserName, acc.rawContent, List.of());
                }
            }

            crawlUrls(request, retryPolicy, cbEnabled, acc);
            acc.exportedStorageState = safeExportSession();
        } finally {
            browser.close();
        }


        String finalStatus = determineStatus(request, acc);
        long durationMs = System.currentTimeMillis() - startMs;

        logPipelineEnd(request, finalStatus, durationMs, acc);

        return new PipelineResult(finalStatus, acc.records, acc.errors, acc.logLines,
                acc.pagesCrawled, durationMs, browserName, acc.rawContent, List.of());
    }


    private void crawlUrls(InternalScrapeRequest request, RetryPolicy policy,
                           boolean cbEnabled, CrawlAccumulator acc) {
        for (String url : buildUrlList(request)) {
            if (processUrl(url, request, policy, cbEnabled, acc) == UrlSignal.STOP) break;
        }
    }

    private UrlSignal processUrl(String url, InternalScrapeRequest request,
                                 RetryPolicy policy, boolean cbEnabled,
                                 CrawlAccumulator acc) {

        if (isMaxPagesReached(request, acc)) return UrlSignal.STOP;
        if (cbEnabled && isCircuitBreakerBlocked(url, policy, acc)) return UrlSignal.CONTINUE;
        if (!navigateWithRetry(url, policy, cbEnabled, acc)) return UrlSignal.CONTINUE;

        acc.pagesCrawled++;
        acc.logLines.add(String.format(LOG_INFO + "Navigated → %s (page %d)", url, acc.pagesCrawled));

        applyPageInteractions(request);

        String finalUrl = browser.snapshot().getUrl();
        if (request.getSessionConfig() != null
                && request.getSessionConfig().isUsable()
                && finalUrl != null
                && finalUrl.contains("/login")) {
            String msg = "Session rejected — redirected to login: " + finalUrl;
            log.error(LOG_PIPELINE, msg);
            acc.errors.add(msg);
            acc.logLines.add(LOG_ERROR + msg);
            return UrlSignal.STOP;
        }

        boolean bypassed = captchaHandler.tryHandleIfPresent(browser.snapshot(), request.getJobId());
        if (bypassed) {
            acc.logLines.add("[INFO] CAPTCHA detected and bypassed before extraction");
            log.info("[PIPELINE] CAPTCHA bypassed for url={} job={}", url, request.getJobId());
        }

        collectExtraction(url, request, policy, cbEnabled, acc);

        runPaginationLoop(url, request, policy, cbEnabled, acc);

        if (request.isSinglePageMode()) {
            acc.logLines.add(LOG_INFO + "Single page mode — stopping after first page");
            return UrlSignal.STOP;
        }

        sleepBetweenPages(request, acc);
        return UrlSignal.CONTINUE;
    }

    private void runPaginationLoop(String url, InternalScrapeRequest request,
                                   RetryPolicy policy, boolean cbEnabled,
                                   CrawlAccumulator acc) {

        if (!paginationRegistry.isPaginationEnabled(request)) return;
        if (request.isSinglePageMode()) return;

        PageSnapshot firstSnapshot = browser.snapshot();
        var resolution = paginationRegistry.resolve(request, firstSnapshot);
        PaginationType paginationType = resolution.type();
        if ("AUTO".equalsIgnoreCase(request.getPaginationType())) {
            String autoMsg = String.format(
                    "Pagination auto-detected on real HTML: type=%s suggestedSelector=%s",
                    paginationType,
                    resolution.suggestedSelector() != null ? resolution.suggestedSelector() : "none"
            );
            log.info("[PIPELINE][PAGINATION][AUTO] {}", autoMsg);
            acc.logLines.add(LOG_INFO + autoMsg);
        }

        if (paginationType == PaginationType.NONE) {
            log.info("[PIPELINE][PAGINATION] Resolved type=NONE — skipping pagination loop");
            acc.logLines.add(LOG_INFO + "Pagination resolved to NONE — no pagination performed");
            return;
        }
        PaginationContext ctx = paginationRegistry.buildContext(request, resolution.suggestedSelector());

        log.info("[PIPELINE][PAGINATION] Starting — type={} maxPaginationPages={} url={} suggestedSelector={}",
                paginationType, request.getMaxPaginationPages(), url, resolution.suggestedSelector());
        acc.logLines.add(String.format(LOG_INFO + "Pagination loop started — type=%s url=%s",
                paginationType, url));

        while (acc.pagesCrawled < request.getMaxPages()) {

            PageSnapshot currentSnapshot = browser.snapshot();

            PaginationContext nextCtx = paginationRegistry.advance(
                    browser, currentSnapshot, ctx, paginationType);

            if (nextCtx == null) {
                log.info("[PIPELINE][PAGINATION] Exhausted at page {} for {}", ctx.getCurrentPage(), url);
                acc.logLines.add(String.format(LOG_INFO + "Pagination exhausted at page=%d url=%s",
                        ctx.getCurrentPage(), url));
                break;
            }

            ctx = nextCtx;

            acc.pagesCrawled++;
            acc.logLines.add(String.format(LOG_INFO + "Paginated → %s page=%d",
                    url, ctx.getCurrentPage()));

            applyPageInteractions(request);
            PageSnapshot afterNav = browser.snapshot();
            String paginatedUrl = afterNav.getUrl() != null ? afterNav.getUrl() : url;

            boolean bypassed = captchaHandler.tryHandleIfPresent(afterNav, request.getJobId());
            if (bypassed) {
                acc.logLines.add("[INFO] CAPTCHA detected and bypassed on paginated page");
            }

            collectExtraction(paginatedUrl, request, policy, cbEnabled, acc);
            acc.logLines.add(String.format(LOG_INFO + "Pagination page=%d | url=%s | records=%d",
                    ctx.getCurrentPage(), url, acc.records.size()));

            sleep(request.getDelayMs());
        }

        log.info("[PIPELINE][PAGINATION] Done — totalPagPages={} totalRecords={} url={}",
                ctx.getCurrentPage(), acc.records.size(), url);
    }


    private void applyPageInteractions(InternalScrapeRequest request) {
        browser.dismissCookies();
        if (request.getWaitForSelector() != null) {
            browser.waitForSelector(request.getWaitForSelector());
        }
        if (request.getClickBefore() != null) {
            browser.click(request.getClickBefore());
        }
        applyPreExtractActions(request);

        browser.scroll(request.isScrollToBottom());
    }

    private void applyPreExtractActions(InternalScrapeRequest request) {
        List<PageAction> actions = request.getPreExtractActions();
        if (actions == null || actions.isEmpty()) {
            return;
        }
        log.info("[PIPELINE][ACTIONS] Executing {} pre-extract action(s) for job={}",
                actions.size(), request.getJobId());

        int idx = 0;
        for (PageAction action : actions) {
            idx++;
            boolean ok = browser.performAction(action);
            if (!ok) {
                log.warn("[PIPELINE][ACTIONS] Required action #{} failed — halting sequence: {}",
                        idx, action != null ? action.describe() : "null");
                break;
            }
        }
        log.info("[PIPELINE][ACTIONS] Pre-extract sequence done for job={}", request.getJobId());
    }

    private void collectExtraction(String url, InternalScrapeRequest request,
                                   RetryPolicy policy, boolean cbEnabled,
                                   CrawlAccumulator acc) {

        PageProcessingResult pResult = extractWithRetry(url, request, policy, cbEnabled, acc);
        if (pResult == null) return;
        if (pResult.rawContent() != null && !pResult.rawContent().isBlank()) {
            String pageMarker = "=== PAGE: " + url + " ===\n";
            if (acc.rawContent == null || acc.rawContent.isBlank()) {
                acc.rawContent = pageMarker + pResult.rawContent();
            } else {
                acc.rawContent = acc.rawContent + "\n\n" + pageMarker + pResult.rawContent();
            }
        }

        acc.records.addAll(pResult.records());

        if (pResult.records().isEmpty() && acc.rawContent == null) {
            PageSnapshot snap = browser.snapshot();
            if (snap.getHtml() != null) {
                acc.rawContent = snap.getHtml();
            }
        }

        log.info("[PIPELINE] URL {}: {} record(s) extracted from {}",
                acc.pagesCrawled, pResult.records().size(), url);

        acc.logLines.add(buildExtractionLogLine(url, pResult));
    }

    private String buildExtractionLogLine(String url, PageProcessingResult pResult) {
        if (pResult.rawContent() != null && !pResult.rawContent().isBlank()) {
            return String.format(LOG_INFO + "url=%s | rawContent=%d" + RAW_CONTENT_SUFFIX + " (LLM mode)",
                    url, pResult.rawContent().length());
        }
        return String.format(LOG_INFO + "url=%s | records=%d", url, pResult.records().size());
    }

    private String determineStatus(InternalScrapeRequest request, CrawlAccumulator acc) {
        boolean isLlm = ExtractionMode.LLM.equals(request.getExtractionMode());
        if (!acc.records.isEmpty() || isLlm) return STATUS_COMPLETED;

        String msg = "No records extracted — check selectors / regex pattern";
        acc.errors.add(msg);
        acc.logLines.add(LOG_ERROR + msg);
        return STATUS_FAILED;
    }

    private boolean isMaxPagesReached(InternalScrapeRequest request, CrawlAccumulator acc) {
        if (acc.pagesCrawled < request.getMaxPages()) return false;
        String msg = "max_pages=" + request.getMaxPages() + " reached — stopping";
        log.info(LOG_PIPELINE, msg);
        acc.logLines.add(LOG_INFO + msg);
        return true;
    }

    private boolean isCircuitBreakerBlocked(String url, RetryPolicy policy, CrawlAccumulator acc) {
        DomainCircuitBreaker cb = cbRegistry.getOrCreate(url, policy);
        if (cb.allowRequest()) return false;
        String msg = "Circuit breaker OPEN for domain — skipping: " + url;
        log.warn(LOG_PIPELINE, msg);
        acc.errors.add(msg);
        acc.logLines.add(LOG_WARNING + msg);
        return true;
    }

    private boolean navigateWithRetry(String url, RetryPolicy policy,
                                      boolean cbEnabled, CrawlAccumulator acc) {

        DomainCircuitBreaker cb = cbEnabled ? cbRegistry.getOrCreate(url, policy) : null;

        try {
            RetryExecutor.run(
                    () -> {
                        try {
                            browser.navigate(url);
                        } catch (Infrastructure5xxException e) {
                            throw e;
                        } catch (Exception e) {
                            throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                                    .message("Browser navigation error for " + url + ": " + e.getMessage())
                                    .uriPath(url)
                                    .subErrors(Set.of())
                                    .build();
                        }
                    },
                    policy, CTX_NAVIGATE + url, log);

            if (cb != null) cb.recordSuccess();
            return true;

        } catch (Infrastructure5xxException e) {
            String msg = "Navigation failed for " + url + ": " + e.getMessage();
            log.error(LOG_PIPELINE, msg);
            acc.errors.add(msg);
            acc.logLines.add(LOG_ERROR + msg);
            if (cb != null) cb.recordFailure();
            return false;
        }
    }

    private PageProcessingResult extractWithRetry(String url, InternalScrapeRequest request,
                                                  RetryPolicy policy, boolean cbEnabled,
                                                  CrawlAccumulator acc) {

        DomainCircuitBreaker cb = cbEnabled ? cbRegistry.getOrCreate(url, policy) : null;

        try {
            PageProcessingResult result = RetryExecutor.execute(
                    () -> {
                        try {
                            PageSnapshot snapshot = browser.snapshot();
                            ExtractionOutput output = extractor.extract(snapshot, request);
                            return new PageProcessingResult(output.records(), output.rawContent());
                        } catch (Infrastructure5xxException e) {
                            throw e;
                        } catch (Exception e) {
                            throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                                    .message("Extraction error for " + url + ": " + e.getMessage())
                                    .uriPath(url)
                                    .subErrors(Set.of())
                                    .build();
                        }
                    },
                    policy, CTX_EXTRACT + url, log);

            if (cb != null) cb.recordSuccess();
            return result;

        } catch (Infrastructure5xxException e) {
            String msg = "Extraction failed for " + url + ": " + e.getMessage();
            log.error(LOG_PIPELINE, msg, e);
            acc.errors.add(msg);
            acc.logLines.add(LOG_ERROR + msg);
            if (cb != null) cb.recordFailure();
            return null;
        }
    }

    private void logPipelineStart(InternalScrapeRequest request, String browserName,
                                  RetryPolicy retryPolicy, boolean cbEnabled,
                                  CrawlAccumulator acc) {
        log.info("[PIPELINE] Starting job={} mode={} browser={} retry={} cb={}",
                request.getJobId(), request.getExtractionMode(), browserName,
                retryPolicy != null ? retryPolicy.maxRetries() : 0, cbEnabled);

        acc.logLines.add(String.format(LOG_INFO + "Scrape started — url=%s mode=%s browser=%s",
                request.getTargetUrl(), request.getExtractionMode(), browserName));
    }

    private void logPipelineEnd(InternalScrapeRequest request, String status,
                                long durationMs, CrawlAccumulator acc) {
        String rawDesc = acc.rawContent != null
                ? acc.rawContent.length() + RAW_CONTENT_SUFFIX : "none";

        acc.logLines.add(String.format(
                LOG_INFO + "Scrape finished — status=%s pages=%d records=%d rawContent=%s duration=%dms",
                status, acc.pagesCrawled, acc.records.size(), rawDesc, durationMs));

        log.info("[PIPELINE] Finished job={} status={} records={} pages={} duration={}ms",
                request.getJobId(), status, acc.records.size(), acc.pagesCrawled, durationMs);
    }

    private String resolveBrowserName() {
        return browser.getClass().getSimpleName().replace("Browser", "").toLowerCase();
    }

    private static void sleepBetweenPages(InternalScrapeRequest request, CrawlAccumulator acc) {
        if (acc.pagesCrawled < request.getMaxPages()) {
            sleep(request.getDelayMs());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> buildUrlList(InternalScrapeRequest request) {
        List<String> urls = new ArrayList<>();
        urls.add(request.getTargetUrl());
        if (JobType.CRAWLER.equals(request.getJobType())
                && request.getSeedUrls() != null
                && !request.isSinglePageMode()) {
            urls.addAll(request.getSeedUrls());
        }
        return urls;
    }

    private enum UrlSignal {CONTINUE, STOP}

    private static class CrawlAccumulator {
        final List<Map<String, Object>> records = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> logLines = new ArrayList<>();
        String exportedStorageState = null;
        int pagesCrawled = 0;
        String rawContent = null;
    }

    private record PageProcessingResult(
            List<Map<String, Object>> records,
            String rawContent
    ) {
    }

    private String safeExportSession() {
        try {
            return browser.exportSessionState();
        } catch (Exception e) {
            log.warn("[PIPELINE] session export failed: {}", e.getMessage());
            return null;
        }
    }
}