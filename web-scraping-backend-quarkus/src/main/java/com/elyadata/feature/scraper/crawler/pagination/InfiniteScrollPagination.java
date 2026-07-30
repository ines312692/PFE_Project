package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

@Slf4j
@ApplicationScoped
public class InfiniteScrollPagination implements PaginationStrategy {

    private static final int WAIT_AFTER_SCROLL_MS = 2_000;
    private static final int MIN_CONTENT_DELTA = 200;

    @Override
    public PaginationType type() {
        return PaginationType.INFINITE_SCROLL;
    }

    @Override
    public boolean hasNextPage(PageSnapshot snapshot, PaginationContext context) {
        if (context.hasReachedMaxPages()) {
            log.debug("[Pagination/INFINITE_SCROLL] maxPages={} reached", context.getMaxPages());
            return false;
        }
        if (context.isStale()) {
            log.debug("[Pagination/INFINITE_SCROLL] stale — no new content after last scroll");
            return false;
        }
        return true;
    }

    @Override
    public void goToNextPage(ABrowser browser, PaginationContext context) {
        log.info("[Pagination/INFINITE_SCROLL] scrolling to bottom (iteration {})",
                context.getCurrentPage());
        browser.scroll(true);
        sleep();
    }

    @Override
    public DetectionResult detect(PageSnapshot snapshot) {
        Document doc = parse(snapshot);
        String html = snapshot.getHtml() != null ? snapshot.getHtml() : "";
        String lower = html.toLowerCase(java.util.Locale.ROOT);

        StringBuilder reasons = new StringBuilder();
        int score = scoreDomSignals(doc, reasons)
                + scoreScriptSignals(lower, reasons)
                + scorePageShape(doc, html, lower, reasons);

        if (score >= 35) {
            boolean hasDomEvidence = score >= 60;
            double confidence = hasDomEvidence
                    ? Math.min(0.40 + score / 200.0, 0.80)
                    : Math.min(0.25 + score / 200.0, 0.55);

            log.debug("[Pagination/INFINITE_SCROLL] score={} confidence={} reasons={}",
                    score, confidence, reasons);
            return new DetectionResult(
                    PaginationType.INFINITE_SCROLL,
                    confidence,
                    "score=" + score + " — " + reasons,
                    null
            );
        }

        return DetectionResult.notFound(type());
    }

    private int scoreDomSignals(Document doc, StringBuilder reasons) {
        int score = 0;
        if (!doc.select("[data-infinite-scroll], .infinite-scroll, [data-infinite]").isEmpty()) {
            score += 35;
            reasons.append("DOM infinite-scroll markers; ");
        }
        if (!doc.select("cdk-virtual-scroll-viewport, .cdk-virtual-scroll-viewport").isEmpty()) {
            score += 40;
            reasons.append("Angular CDK virtual scroll component; ");
        }
        if (!doc.select("[data-testid*='infinite'], [data-testid*='load-more']").isEmpty()) {
            score += 25;
            reasons.append("data-testid infinite/load-more; ");
        }
        return score;
    }

    private int scoreScriptSignals(String lower, StringBuilder reasons) {
        int score = 0;
        if (lower.contains("intersectionobserver")) {
            score += 25;
            reasons.append("IntersectionObserver; ");
        }
        if (lower.contains("infinite-scroll") || lower.contains("infinitescroll")
                || lower.contains("react-infinite-scroller")
                || lower.contains("ngx-infinite-scroll")
                || lower.contains("vue-infinite-scroll")
                || lower.contains("svelte-infinite-loading")) {
            score += 40;
            reasons.append("infinite-scroll library reference; ");
        }
        if (lower.contains("virtualscroll") || lower.contains("virtual-scroller")
                || lower.contains("virtual-list") || lower.contains("react-window")
                || lower.contains("react-virtualized") || lower.contains("tanstack-virtual")) {
            score += 35;
            reasons.append("virtual-scroll library; ");
        }
        if (lower.contains("\"hasnextpage\":true") || lower.contains("\"has_next_page\":true")) {
            score += 50;
            reasons.append("embedded state: hasNextPage=true; ");
        }
        if (lower.contains("\"cursor\"") && lower.contains("\"endcursor\"")) {
            score -= 10;
        }
        return score;
    }

    private int scorePageShape(Document doc, String html, String lower, StringBuilder reasons) {
        int score = 0;
        boolean noOtherPaginationSignals = doc.select(
                "a[href*='page='], .pagination, [aria-label='Next'], [rel='next'], .load-more"
        ).isEmpty();

        if (noOtherPaginationSignals && html.length() > 50_000) {
            score += 15;
            reasons.append("large page, no other pagination signals; ");
        }
        if (noOtherPaginationSignals && html.length() < 15_000
                && lower.contains("<script") && lower.contains("bundle")) {
            score += 20;
            reasons.append("SPA shell, no other signals; ");
        }
        return score;
    }

    public PaginationContext refreshContext(PageSnapshot newSnapshot, PaginationContext context) {
        long newLen = newSnapshot.getHtml() != null ? (long) newSnapshot.getHtml().length() : 0L;
        long delta = newLen - context.getLastHtmlLength();
        log.debug("[Pagination/INFINITE_SCROLL] html delta={} chars (prev={} now={})",
                delta, context.getLastHtmlLength(), newLen);
        PaginationContext updated = context.withHtmlLength(newLen);
        return delta < MIN_CONTENT_DELTA ? updated.markStale() : updated;
    }

    private static Document parse(PageSnapshot snap) {
        String html = snap.getHtml() != null ? snap.getHtml() : "";
        String base = snap.getUrl() != null ? snap.getUrl() : "";
        return Jsoup.parse(html, base);
    }

    private static void sleep() {
        try {
            Thread.sleep(InfiniteScrollPagination.WAIT_AFTER_SCROLL_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}