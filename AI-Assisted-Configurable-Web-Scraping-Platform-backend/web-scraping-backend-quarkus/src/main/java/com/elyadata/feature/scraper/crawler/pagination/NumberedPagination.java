package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class NumberedPagination implements PaginationStrategy {

    private static final int WAIT_AFTER_CLICK_MS = 1_200;

    private static final List<String> NEXT_FALLBACKS = List.of(
            "a[rel='next']:not([disabled])",
            "[aria-label='Next page']:not([disabled]):not([aria-disabled='true'])",
            "[aria-label='Next']:not([disabled]):not([aria-disabled='true'])",
            "[aria-label='Page suivante']:not([disabled]):not([aria-disabled='true'])",
            "[aria-label='Suivant']:not([disabled])",
            "[aria-label='Nächste Seite']:not([disabled])",
            "[aria-label='Siguiente']:not([disabled])",
            "[aria-label='Successivo']:not([disabled])",
            "[aria-label='التالي']:not([disabled])",
            "[data-page='next']:not([disabled])",
            "[data-testid='pagination-next']:not([disabled])",
            "[data-testid='next-page']:not([disabled])",
            "[data-qa-id='pagination-next']:not([disabled])",
            "[data-qa='pagination-next']:not([disabled])",
            "[data-cy='pagination-next']:not([disabled])",
            ".pagination-next a:not([disabled])",
            ".pagination__next:not([disabled])",
            ".pagination__next a:not([disabled])",
            ".pagination-next:not(.disabled):not([disabled])",
            ".pager-next a:not([disabled])",
            "li.next:not(.disabled) a",
            ".btn-next:not([disabled])",
            "button.next-page:not([disabled])",
            "a.next:not([disabled])",
            ".pagination__arrow--right:not([disabled])",
            ".paginationNext:not([disabled])",
            ".pagination-arrow-right:not([disabled])",
            "button[title='Next page']:not([disabled])",
            "button[title='Page suivante']:not([disabled])",
            "a[title='Next']:not([disabled])",
            "a[title='Suivant']:not([disabled])"
    );

    private static final Pattern NEXT_TEXT_PATTERN = Pattern.compile(
            "^(next|suivant|weiter|siguiente|successivo|التالي|›|»|>|next\\s*page|page\\s*suivante)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public PaginationType type() {
        return PaginationType.NUMBERED;
    }

    @Override
    public boolean hasNextPage(PageSnapshot snapshot, PaginationContext context) {
        if (context.hasReachedMaxPages()) return false;

        Document doc = parse(snapshot);
        int nextPage = context.getCurrentPage() + 1;
        if (context.getSelector() != null && !context.getSelector().isBlank()) {
            boolean found = !doc.select(context.getSelector()).isEmpty();
            log.debug("[Pagination/NUMBERED] custom selector='{}' found={}", context.getSelector(), found);
            return found;
        }
        boolean byHref = !doc.select(
                String.format("a[href*='page=%d'], a[href*='p=%d'], a[data-page='%d']",
                        nextPage, nextPage, nextPage)
        ).isEmpty();
        if (byHref) {
            log.debug("[Pagination/NUMBERED] found href-based link for page {}", nextPage);
            return true;
        }

        for (String sel : NEXT_FALLBACKS) {
            Element el = doc.selectFirst(sel);
            if (el != null && isClickable(el)) {
                log.debug("[Pagination/NUMBERED] fallback selector='{}' matched page={}", sel, context.getCurrentPage());
                return true;
            }
        }
        Element byText = findNextByText(doc);
        if (byText != null) {
            log.debug("[Pagination/NUMBERED] text-match '{}' found at page={}", byText.text().trim(), context.getCurrentPage());
            return true;
        }

        log.debug("[Pagination/NUMBERED] no next-page element found at page {}", context.getCurrentPage());
        return false;
    }

    @Override
    public void goToNextPage(ABrowser browser, PaginationContext context) {
        String selector = resolveClickSelector(context);
        log.info("[Pagination/NUMBERED] clicking '{}' → page {}", selector, context.getCurrentPage() + 1);
        browser.click(selector);
        sleep();
    }

    @Override
    public DetectionResult detect(PageSnapshot snapshot) {
        Document doc = parse(snapshot);
        Elements numberedHref = doc.select("a[href*='page='], a[href*='?p='], a[data-page]");
        long numericCount = numberedHref.stream()
                .filter(a -> {
                    String txt = a.text().trim();
                    return txt.matches("\\d+") || a.hasAttr("data-page");
                })
                .count();

        if (numericCount >= 2) {
            String suggested = findSuggestedSelector(doc);
            return new DetectionResult(
                    PaginationType.NUMBERED,
                    0.90,
                    numericCount + " numbered links found",
                    suggested
            );
        }
        Elements containers = doc.select(
                ".pagination, .pager, nav[aria-label*='pagination' i], nav[aria-label*='Pagination'], " +
                        "nav[aria-label*='page' i], [role='navigation'][aria-label*='page' i]"
        );
        if (!containers.isEmpty() && !Objects.requireNonNull(containers.first()).select("a, button").isEmpty()) {
            String suggested = findSuggestedSelector(doc);
            return new DetectionResult(
                    PaginationType.NUMBERED,
                    0.70,
                    "pagination container detected",
                    suggested
            );
        }
        for (String sel : NEXT_FALLBACKS) {
            Element el = doc.selectFirst(sel);
            if (el != null && isClickable(el)) {
                return new DetectionResult(
                        PaginationType.NUMBERED,
                        0.75,
                        "next-button selector matched: " + sel,
                        sel
                );
            }
        }

        return DetectionResult.notFound(type());
    }

    private String resolveClickSelector(PaginationContext context) {
        if (context.getSelector() != null && !context.getSelector().isBlank()) {
            return context.getSelector();
        }
        int nextPage = context.getCurrentPage() + 1;
        return String.format(
                "a[href*='page=%d'], a[href*='p=%d'], a[data-page='%d'], %s",
                nextPage, nextPage, nextPage,
                String.join(", ", NEXT_FALLBACKS));
    }

    private String findSuggestedSelector(Document doc) {
        for (String sel : NEXT_FALLBACKS) {
            Element el = doc.selectFirst(sel);
            if (el != null && isClickable(el)) {
                return sel;
            }
        }
        return null;
    }

    private Element findNextByText(Document doc) {
        for (Element el : doc.select("a, button")) {
            String text = el.text().trim();
            if (!text.isEmpty() && text.length() <= 30 && isClickable(el) && NEXT_TEXT_PATTERN.matcher(text).matches()) {
                return el;
            }
        }
        return null;
    }

    private static boolean isClickable(Element el) {
        if (el.hasAttr("disabled")) return false;
        if ("true".equalsIgnoreCase(el.attr("aria-disabled"))) return false;
        String cls = el.className().toLowerCase();
        return !cls.contains("disabled") && !cls.contains("inactive");
    }

    private static Document parse(PageSnapshot snap) {
        String html = snap.getHtml() != null ? snap.getHtml() : "";
        String base = snap.getUrl() != null ? snap.getUrl() : "";
        return Jsoup.parse(html, base);
    }

    private static void sleep() {
        try {
            Thread.sleep(NumberedPagination.WAIT_AFTER_CLICK_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}