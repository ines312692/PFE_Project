package com.elyadata.feature.visualselector.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Slf4j
@ApplicationScoped
public class HtmlSanitizer {

    public String sanitize(String rawHtml, String finalUrl) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(rawHtml, finalUrl != null ? finalUrl : "");
        doc.outputSettings().prettyPrint(false);

        absolutiseUrls(doc);
        stripScripts(doc);
        stripInlineHandlers(doc);
        stripMetaCsp(doc);
        doc.select("base").remove();

        return doc.outerHtml();
    }

    private void absolutiseUrls(Document doc) {
        for (Element el : doc.select("[href]")) {
            String abs = el.absUrl("href");
            if (!abs.isBlank()) el.attr("href", abs);
        }
        for (Element el : doc.select("[src]")) {
            String abs = el.absUrl("src");
            if (!abs.isBlank()) el.attr("src", abs);
        }
        for (String attr : new String[]{"data-src", "data-lazy-src", "data-original"}) {
            for (Element el : doc.select("[" + attr + "]")) {
                String abs = el.absUrl(attr);
                if (!abs.isBlank()) el.attr("src", abs);
            }
        }
    }

    private void stripScripts(Document doc) {
        doc.select("script, noscript").remove();
        for (Element el : doc.select("[href], [action]")) {
            stripJavascriptUrl(el, "href");
            stripJavascriptUrl(el, "action");
        }
    }

    private void stripJavascriptUrl(Element el, String attr) {
        String v = el.attr(attr);
        if (v != null && v.trim().toLowerCase().startsWith("javascript:")) {
            el.removeAttr(attr);
        }
    }

    private void stripInlineHandlers(Document doc) {
        for (Element el : doc.getAllElements()) {
            for (var attr : el.attributes().asList()) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on")) {
                    el.removeAttr(attr.getKey());
                }
            }
        }
    }

    private void stripMetaCsp(Document doc) {
        Elements metas = doc.select("meta[http-equiv]");
        for (Element meta : metas) {
            String httpEquiv = meta.attr("http-equiv").toLowerCase();
            if (httpEquiv.contains("content-security-policy")) {
                meta.remove();
            }
        }
    }
}