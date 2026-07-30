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

import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class LoadMorePagination implements PaginationStrategy {

    private static final int WAIT_AFTER_CLICK_MS = 2_000;

    private static final String DEFAULT_SELECTOR =
            "button[data-action='load-more'], " +
                    ".load-more-btn, " +
                    "a.load-more, " +
                    ".show-more";

    private static final Pattern LOAD_MORE_TEXT = Pattern.compile(
            "(?:load|show|view|see)\\s*more"   +
                    "|(?:voir|charger|afficher)\\s*plus" +
                    "|plus\\s*de\\s*r[ée]sultats"        +
                    "|mehr\\s*laden"                      +
                    "|m[áa]s\\s*resultados",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public PaginationType type() {
        return PaginationType.LOAD_MORE;
    }

    @Override
    public boolean hasNextPage(PageSnapshot snapshot, PaginationContext context) {
        if (context.hasReachedMaxPages()) return false;

        Document doc = parse(snapshot);
        String selector = effectiveSelector(context);
        var elements = doc.select(selector);

        if (elements.isEmpty()) {
            log.debug("[Pagination/LOAD_MORE] button absent from DOM — end of list");
            return false;
        }

        boolean disabled = Objects.requireNonNull(elements.first()).hasAttr("disabled");
        if (disabled) {
            log.debug("[Pagination/LOAD_MORE] button is disabled — end of list");
            return false;
        }

        log.debug("[Pagination/LOAD_MORE] button found via '{}' page={}",
                selector, context.getCurrentPage());
        return true;
    }

    @Override
    public void goToNextPage(ABrowser browser, PaginationContext context) {
        String selector = effectiveSelector(context);
        log.info("[Pagination/LOAD_MORE] clicking '{}' (iteration {})",
                selector, context.getCurrentPage());
        browser.click(selector);
        sleep();
    }

    @Override
    public DetectionResult detect(PageSnapshot snapshot) {
        Document doc = parse(snapshot);

        if (!doc.select(DEFAULT_SELECTOR).isEmpty()) {
            return new DetectionResult(
                    PaginationType.LOAD_MORE,
                    0.85,
                    "matched standard load-more selector",
                    null
            );
        }

        Elements clickable = doc.select("button, a[role='button'], a.btn");
        for (Element b : clickable) {
            String txt = b.text().trim();
            if (txt.isEmpty() || txt.length() > 50) continue;
            if (LOAD_MORE_TEXT.matcher(txt).find()) {
                String suggested = buildSuggestedSelector(b);
                return new DetectionResult(
                        PaginationType.LOAD_MORE,
                        0.75,
                        "button text matches: \"" + txt + "\"",
                        suggested
                );
            }
        }

        return DetectionResult.notFound(type());
    }

    private static String buildSuggestedSelector(Element el) {
        if (!el.id().isBlank()) return "#" + el.id();
        for (var attr : el.attributes()) {
            if (attr.getKey().startsWith("data-")) {
                return el.tagName() + "[" + attr.getKey() + "='" + attr.getValue() + "']";
            }
        }
        if (!el.className().isBlank()) {
            String firstClass = el.className().split("\\s+")[0];
            return el.tagName() + "." + firstClass;
        }
        return el.tagName();
    }

    private String effectiveSelector(PaginationContext context) {
        String custom = context.getSelector();
        return (custom != null && !custom.isBlank()) ? custom : DEFAULT_SELECTOR;
    }

    private static Document parse(PageSnapshot snap) {
        String html = snap.getHtml() != null ? snap.getHtml() : "";
        String base = snap.getUrl() != null ? snap.getUrl() : "";
        return Jsoup.parse(html, base);
    }

    private static void sleep() {
        try {
            Thread.sleep(LoadMorePagination.WAIT_AFTER_CLICK_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}