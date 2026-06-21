package com.elyadata.feature.scraper.crawler.url;

import com.elyadata.feature.scraper.browser.PageSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Jsoup;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public final class LinkHarvester {

    private static final int MAX_LINKS_PER_PAGE = 200;

    private static final List<String> CONTENT_ZONE_SELECTORS = List.of(
            "main a[href]",
            "[role='main'] a[href]",
            "article a[href]",
            ".product-card a[href], .product-list a[href], [data-testid*='product'] a[href]",
            ".listing a[href], .results a[href], .grid a[href]"
    );

    private LinkHarvester() {
    }

    public static Set<String> harvest(PageSnapshot snapshot) {
        if (snapshot.getHtml() == null || snapshot.getHtml().isBlank()) {
            return Set.of();
        }

        Document doc = Jsoup.parse(snapshot.getHtml(), snapshot.getUrl());
        Elements anchors = selectContentAnchors(doc);
        int total = doc.select("a[href]").size();

        Set<String> links = collectLinks(anchors, snapshot.getUrl());

        log.debug("[HARVEST] {}/{} link(s) kept on {} (ratio={}%)",
                links.size(), total, snapshot.getUrl(),
                total == 0 ? 0 : (links.size() * 100 / total));

        return links;
    }

    private static Elements selectContentAnchors(Document doc) {
        for (String selector : CONTENT_ZONE_SELECTORS) {
            Elements found = doc.select(selector);
            if (!found.isEmpty()) {
                log.trace("[HARVEST] Using content zone selector: '{}'", selector);
                return found;
            }
        }
        return doc.select("a[href]");
    }

    private static Set<String> collectLinks(Elements anchors, String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        for (Element a : anchors) {
            if (links.size() >= MAX_LINKS_PER_PAGE) {
                log.debug("[HARVEST] Reached MAX_LINKS_PER_PAGE={} cap — stopping", MAX_LINKS_PER_PAGE);
                break;
            }
            String raw = a.attr("href");
            if (!isUnnavigable(raw)) {
                UrlNormalizer.normalize(raw, baseUrl).ifPresent(links::add);
            }
        }
        return links;
    }

    private static boolean isUnnavigable(String href) {
        if (href == null || href.isBlank()) return true;
        String trimmed = href.trim().toLowerCase();
        return trimmed.startsWith("mailto:")
                || trimmed.startsWith("tel:")
                || trimmed.startsWith("javascript:")
                || trimmed.startsWith("sms:")
                || trimmed.equals("#")
                || (trimmed.startsWith("#") && trimmed.length() > 1);
    }
}