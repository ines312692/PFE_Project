package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class HtmlToRecordsStep implements EtlStep {

    private static final int MIN_REPEATED_BLOCKS = 3;
    private static final String DEFAULT_HTML_FIELD = "html";
    private static final Set<String> DROPPED_PARENT_FIELDS = Set.of("html", "html_length");

    @Override
    public EtlStepType type() {
        return EtlStepType.PARSE_HTML;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> records,
                                           Map<String, Object> params) {
        String htmlField = params.get("htmlField") instanceof String s && !s.isBlank()
                ? s
                : DEFAULT_HTML_FIELD;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> rec : records) {
            Object raw = rec.get(htmlField);
            if (!(raw instanceof String html) || html.isBlank()) {
                out.add(rec);
                continue;
            }
            Map<String, Object> inherited = buildInherited(rec, htmlField);
            String baseUri = rec.get("url") instanceof String u ? u : "";
            for (Map<String, Object> row : parse(html, baseUri)) {
                Map<String, Object> merged = new LinkedHashMap<>(inherited);
                merged.putAll(row);
                out.add(merged);
            }
        }

        log.info("[ETL] PARSE_HTML — {} record(s) → {} row(s)", records.size(), out.size());
        return out;
    }

    private Map<String, Object> buildInherited(Map<String, Object> rec, String htmlField) {
        Map<String, Object> inherited = new LinkedHashMap<>();
        rec.forEach((k, v) -> {
            if (!DROPPED_PARENT_FIELDS.contains(k) && !k.equals(htmlField)) {
                inherited.put(k, v);
            }
        });
        return inherited;
    }

    private List<Map<String, Object>> parse(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        doc.select("script, style, noscript, template, svg").remove();

        List<Map<String, Object>> rows = extractTables(doc);
        if (rows.isEmpty()) rows = extractRepeatedBlocks(doc);
        if (rows.isEmpty()) rows = fallbackText(doc);
        return rows;
    }

    private List<Map<String, Object>> extractTables(Document doc) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Element table : doc.select("table")) {
            List<String> headers = readHeaders(table);
            Elements bodyRows = table.select("tbody tr");
            if (bodyRows.isEmpty()) {
                bodyRows = table.select("tr");
            }
            rows.addAll(extractTableRows(bodyRows, headers));
        }
        return rows;
    }

    private List<Map<String, Object>> extractTableRows(Elements bodyRows, List<String> headers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Element tr : bodyRows) {
            Elements cells = tr.select("th, td");
            boolean isHeaderRow = !headers.isEmpty() && tr.select("th").size() == cells.size();
            if (!cells.isEmpty() && !isHeaderRow) {
                Map<String, Object> entry = new LinkedHashMap<>();
                for (int i = 0; i < cells.size(); i++) {
                    String key = (i < headers.size() && !headers.get(i).isBlank())
                            ? headers.get(i)
                            : "col_" + (i + 1);
                    entry.put(uniqueKey(entry, key), cells.get(i).text().strip());
                }
                rows.add(entry);
            }
        }
        return rows;
    }

    private List<String> readHeaders(Element table) {
        Elements ths = table.select("thead th");
        if (ths.isEmpty()) {
            Element firstRow = table.selectFirst("tr");
            if (firstRow != null) ths = firstRow.select("th");
        }
        return ths.stream().map(e -> e.text().strip()).toList();
    }

    private List<Map<String, Object>> extractRepeatedBlocks(Document doc) {
        BestBlock best = findBestRepeatedBlock(doc);
        if (best == null) return List.of();
        return collectBlockRows(best.parent(), best.signature());
    }

    private BestBlock findBestRepeatedBlock(Document doc) {
        Element bestParent = null;
        String bestSignature = null;
        int bestCount = 0;

        for (Element parent : doc.select("body *")) {
            for (Map.Entry<String, Long> e : groupChildrenBySignature(parent).entrySet()) {
                if (e.getValue() > bestCount && e.getValue() >= MIN_REPEATED_BLOCKS) {
                    bestCount = e.getValue().intValue();
                    bestParent = parent;
                    bestSignature = e.getKey();
                }
            }
        }
        return bestParent == null ? null : new BestBlock(bestParent, bestSignature);
    }

    private Map<String, Long> groupChildrenBySignature(Element parent) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Element child : parent.children()) {
            counts.merge(signature(child), 1L, Long::sum);
        }
        return counts;
    }

    private List<Map<String, Object>> collectBlockRows(Element bestParent, String bestSignature) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Element child : bestParent.children()) {
            String text = child.text().strip();
            if (signature(child).equals(bestSignature) && !text.isBlank()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("text", text);
                Element link = child.selectFirst("a[href]");
                if (link != null) entry.put("link", link.absUrl("href"));
                rows.add(entry);
            }
        }
        return rows;
    }

    private record BestBlock(Element parent, String signature) {
    }

    private String signature(Element el) {
        String firstClass = el.classNames().isEmpty() ? "" : el.classNames().iterator().next();
        return el.tagName() + "." + firstClass;
    }

    private List<Map<String, Object>> fallbackText(Document doc) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", doc.title());
        entry.put("text", doc.body().text().strip());
        return List.of(entry);
    }

    private String uniqueKey(Map<String, Object> entry, String key) {
        if (!entry.containsKey(key)) return key;
        int n = 2;
        while (entry.containsKey(key + "_" + n)) n++;
        return key + "_" + n;
    }
}