package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.scraper.browser.PageSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.LinkedHashSet;
import java.util.Set;


@Slf4j
@ApplicationScoped
public class PaginationHtmlSampler {
    private static final int MAX_SAMPLE_CHARS = 32_000;
    private static final int MAX_SCRIPT_CHARS = 4_000;

    private static final String PAGINATION_REGIONS = String.join(", ",
            ".pagination", ".pager", ".paging", "[class*='pagination']", "[class*='pager']",
            "nav[aria-label*='pag' i]", "nav[aria-label*='page' i]",
            "[role='navigation'][aria-label*='page' i]",
            "a[rel='next']", "a[rel='prev']", "[aria-label*='next' i]", "[aria-label*='suivant' i]",
            "[data-testid*='pagination']", "[data-testid*='next']", "[data-page]",
            "[class*='load-more']", "[class*='show-more']", "[data-infinite]",
            "[class*='infinite']", "cdk-virtual-scroll-viewport",
            "nav", "footer"
    );

    private static final String PAGINATION_ANCHORS =
            "a[href*='page='], a[href*='?p='], a[href*='&p='], a[href*='offset='], "
                    + "a[href*='start='], a[href*='/page/']";

    public String sample(PageSnapshot snapshot) {
        String html = snapshot != null && snapshot.getHtml() != null ? snapshot.getHtml() : "";
        if (html.isEmpty()) return "";

        String base = snapshot.getUrl() != null ? snapshot.getUrl() : "";
        Document doc = Jsoup.parse(html, base);
        Set<String> fragments = new LinkedHashSet<>();

        appendHead(doc, fragments);
        appendMatches(doc.select(PAGINATION_REGIONS), fragments);
        appendMatches(doc.select(PAGINATION_ANCHORS), fragments);
        appendStateScripts(doc, fragments);

        if (fragments.isEmpty()) {
            log.debug("[PaginationSampler] no pagination-relevant regions found");
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (String fragment : fragments) {
            if (out.length() + fragment.length() > MAX_SAMPLE_CHARS) {
                log.debug("[PaginationSampler] hit char cap ({}), truncating sample", MAX_SAMPLE_CHARS);
                break;
            }
            out.append(fragment).append('\n');
        }

        String result = out.toString();
        log.info("[PaginationSampler] reduced {} chars → {} chars ({} fragments)",
                html.length(), result.length(), fragments.size());
        return result;
    }

    private void appendHead(Document doc, Set<String> fragments) {
        Elements links = doc.select("head link[rel=next], head link[rel=prev], head link[rel='canonical']");
        for (Element link : links) {
            fragments.add(link.outerHtml());
        }
    }

    private void appendMatches(Elements elements, Set<String> fragments) {
        for (Element el : elements) {
            String frag = el.outerHtml();
            if (frag.length() <= MAX_SCRIPT_CHARS) {
                fragments.add(frag);
            } else {
                fragments.add(shallowTag(el));
            }
        }
    }

    private void appendStateScripts(Document doc, Set<String> fragments) {
        Elements scripts = doc.select(
                "script#__NEXT_DATA__, script[id*='state'], script[type='application/json']");
        for (Element script : scripts) {
            String data = script.data();
            if (data.isBlank()) continue;
            String slice = data.length() > MAX_SCRIPT_CHARS
                    ? data.substring(0, MAX_SCRIPT_CHARS) + " /*…truncated…*/"
                    : data;
            fragments.add("<script id='" + script.id() + "'>" + slice + "</script>");
        }
    }

    private static String shallowTag(Element el) {
        StringBuilder sb = new StringBuilder("<").append(el.tagName());
        el.attributes().forEach(a -> sb.append(' ')
                .append(a.getKey()).append("=\"").append(a.getValue()).append('"'));
        return sb.append(">…</").append(el.tagName()).append('>').toString();
    }
}