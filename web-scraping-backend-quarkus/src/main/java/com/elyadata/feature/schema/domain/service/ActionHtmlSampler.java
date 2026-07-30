package com.elyadata.feature.schema.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@ApplicationScoped
public class ActionHtmlSampler {

    private static final int DEFAULT_BUDGET = 18_000;


    private static final String INTERACTIVE_SELECTOR = String.join(",",
            "button", "a[href]", "input", "select", "textarea", "form", "label",
            "[role=button]", "[role=link]", "[role=tab]", "[role=option]",
            "[role=menuitem]", "[role=combobox]", "[role=searchbox]",
            "[onclick]", "[data-testid]", "[autotrack-event-action]",
            "[contenteditable=true]"
    );


    private static final Set<String> KEEP_ATTRS = Set.of(
            "id", "class", "name", "type", "placeholder", "value",
            "href", "title", "alt", "for"
    );


    private static final List<String> KEEP_ATTR_PREFIXES = List.of(
            "data-", "aria-", "autotrack-"
    );

    private static final int MAX_TEXT_LEN = 60;
    private static final int MAX_ELEMENTS = 400;

    public String sample(String html) {
        return sample(html, DEFAULT_BUDGET);
    }

    public String sample(String html, int budgetChars) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript,svg,iframe,link,meta,template,picture,source").remove();

        List<String> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int count = 0;

        for (Element el : doc.select(INTERACTIVE_SELECTOR)) {
            if (count >= MAX_ELEMENTS) break;

            String line = renderElement(el);
            if (line.isBlank()) continue;

            if (!seen.add(line)) continue;

            lines.add(line);
            count++;
        }

        String head = headSummary(doc);
        String body = String.join("\n", lines);
        String assembled = head + "\n<!-- INTERACTIVE ELEMENTS -->\n" + body;

        if (assembled.length() > budgetChars) {
            assembled = assembled.substring(0, budgetChars);
        }

        log.info("[ACTION-SAMPLER] {} interactive element(s) kept, final={} chars (budget={})",
                count, assembled.length(), budgetChars);

        return assembled;
    }


    private String renderElement(Element el) {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(el.tagName());

        el.attributes().forEach(attr -> {
            String key = attr.getKey().toLowerCase();
            boolean keep = KEEP_ATTRS.contains(key)
                    || KEEP_ATTR_PREFIXES.stream().anyMatch(key::startsWith);
            if (keep) {
                String val = attr.getValue();
                if (val.length() > 120) {
                    val = val.substring(0, 120);
                }
                sb.append(' ').append(attr.getKey()).append("=\"").append(val).append('"');
            }
        });

        sb.append('>');

        String text = el.ownText().strip();
        if (text.isBlank()) {
            text = el.text().strip();
        }
        if (!text.isBlank()) {
            if (text.length() > MAX_TEXT_LEN) {
                text = text.substring(0, MAX_TEXT_LEN);
            }
            sb.append(text);
        }
        sb.append("</").append(el.tagName()).append('>');

        return sb.toString();
    }

    private String headSummary(Document doc) {
        StringBuilder sb = new StringBuilder("<!-- HEAD SUMMARY -->\n");
        Element title = doc.selectFirst("title");
        if (title != null) {
            sb.append("<title>").append(title.text()).append("</title>\n");
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            String t = h1.text().strip();
            sb.append("<h1>").append(t.length() > 120 ? t.substring(0, 120) : t).append("</h1>\n");
        }
        return sb.toString();
    }
}