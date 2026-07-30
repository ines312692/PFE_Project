package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Slf4j
@ApplicationScoped
public class NextButtonPagination implements PaginationStrategy {

    private static final int WAIT_AFTER_CLICK_MS = 3_000;
    private static final String[] SELECTORS = {
            "a[rel='next']:not([disabled]):not([aria-disabled='true'])",
            "button.next-page:not([disabled])",
            ".btn-next:not([disabled])",
            "[aria-label='Next']:not([disabled]):not([aria-disabled='true'])",
            "[aria-label='Next page']:not([disabled])",
            ".pagination__arrow--right:not([disabled])",
            "a.next:not([disabled])",
            ".pagination-next a:not([disabled])",
            "li.next:not(.disabled) a"
    };

    @Override
    public PaginationType type() {
        return PaginationType.NEXT_BUTTON;
    }

    @Override
    public boolean hasNextPage(PageSnapshot snapshot, PaginationContext context) {
        if (context.hasReachedMaxPages() || context.isStale()) return false;

        Document doc = parse(snapshot);
        String selector = effectiveSelector(context);

        Element next = doc.selectFirst(selector);
        if (next == null) {
            log.debug("[Pagination/NEXT_BUTTON] selector='{}' no element found page={}",
                    selector, context.getCurrentPage());
            return false;
        }
        if (isDisabled(next)) {
            log.debug("[Pagination/NEXT_BUTTON] selector='{}' element is disabled page={}",
                    selector, context.getCurrentPage());
            return false;
        }

        log.debug("[Pagination/NEXT_BUTTON] selector='{}' next button ready page={}",
                selector, context.getCurrentPage());
        return true;
    }

    @Override
    public void goToNextPage(ABrowser browser, PaginationContext context) {
        String selector = effectiveSelector(context);
        log.info("[Pagination/NEXT_BUTTON] clicking '{}' → page {}",
                selector, context.getCurrentPage() + 1);
        browser.click(selector);
        sleep();
    }

    @Override
    public DetectionResult detect(PageSnapshot snapshot) {
        Document doc = parse(snapshot);
        for (String sel : SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null && !isDisabled(el)) {
                return new DetectionResult(
                        PaginationType.NEXT_BUTTON,
                        0.85,
                        "matched selector: " + sel,
                        sel
                );
            }
        }
        return DetectionResult.notFound(type());
    }

    private static boolean isDisabled(Element el) {
        if (el.hasAttr("disabled")) return true;
        String aria = el.attr("aria-disabled");
        if ("true".equalsIgnoreCase(aria)) return true;
        String cls = el.className().toLowerCase();
        return cls.contains("disabled");
    }

    private String effectiveSelector(PaginationContext context) {
        String custom = context.getSelector();
        if (custom != null && !custom.isBlank()) return custom;
        return String.join(", ", SELECTORS);
    }

    private static Document parse(PageSnapshot snap) {
        String html = snap.getHtml() != null ? snap.getHtml() : "";
        String base = snap.getUrl() != null ? snap.getUrl() : "";
        return Jsoup.parse(html, base);
    }

    private static void sleep() {
        try {
            Thread.sleep(NextButtonPagination.WAIT_AFTER_CLICK_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}