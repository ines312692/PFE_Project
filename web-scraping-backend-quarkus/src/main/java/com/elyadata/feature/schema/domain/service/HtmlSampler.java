package com.elyadata.feature.schema.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.Collectors;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.jspecify.annotations.NonNull;


@Slf4j
@ApplicationScoped
public class HtmlSampler {

    private static final int DEFAULT_BUDGET = 12_000;
    private static final int HEAD_BUDGET = 800;
    private static final int MIN_SIBLINGS_FOR_PATTERN = 3;
    private static final int MAX_PATTERNS = 4;
    private static final int MAX_EXAMPLES_PER_PATTERN = 2;

    private static final Set<String> JUNK_TAGS = Set.of(
            "script", "style", "noscript", "svg", "iframe", "link",
            "meta", "template", "picture", "source"
    );

    private static final Set<String> JUNK_ATTR_PREFIXES = Set.of(
            "on",
            "data-react",
            "data-v-",
            "data-n-",
            "ng-",
            "aria-describedby"
    );

    private static final Set<String> JUNK_ATTR_EXACT = Set.of(
            "style", "tabindex", "role"
    );

    public Sample sample(String html) {
        return sample(html, DEFAULT_BUDGET);
    }

    public Sample sample(String html, int budgetChars) {
        if (html == null || html.isBlank()) {
            return new Sample("", List.of());
        }

        Document doc = Parser.htmlParser().parseInput(html, "");
        cleanDocument(doc);

        String headSummary = extractHeadSummary(doc);
        List<RepeatingPattern> patterns = findRepeatingPatterns(doc);

        String assembled = assemble(headSummary, patterns, budgetChars);

        log.debug("[SAMPLER] head={} chars, patterns={}, final={} chars (budget={})",
                headSummary.length(), patterns.size(), assembled.length(), budgetChars);

        return new Sample(assembled, patterns);
    }


    private void cleanDocument(Document doc) {
        for (String tag : JUNK_TAGS) {
            doc.select(tag).remove();
        }

        List<Node> comments = new ArrayList<>();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(@NonNull Node node, int depth) {
                if (node instanceof Comment) comments.add(node);
            }

            @Override
            public void tail(@NonNull Node node, int depth) { /* TODO document why this method is empty */ }
        }, doc);
        comments.forEach(Node::remove);

        for (Element el : doc.getAllElements()) {
            List<String> toRemove = new ArrayList<>();
            for (Attribute attr : el.attributes()) {
                String key = attr.getKey().toLowerCase();
                if (JUNK_ATTR_EXACT.contains(key)
                        || JUNK_ATTR_PREFIXES.stream().anyMatch(key::startsWith)) {
                    toRemove.add(attr.getKey());
                }
            }
            toRemove.forEach(el::removeAttr);
        }
    }

    private String extractHeadSummary(Document doc) {
        StringBuilder sb = new StringBuilder();
        Element title = doc.selectFirst("title");
        if (title != null) {
            sb.append("<title>").append(title.text()).append("</title>\n");
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            sb.append(truncate(h1.outerHtml(), 300)).append("\n");
        }
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null) {
            sb.append(metaDesc.outerHtml()).append("\n");
        }
        return truncate(sb.toString(), HtmlSampler.HEAD_BUDGET);
    }

    private List<RepeatingPattern> findRepeatingPatterns(Document doc) {
        Map<String, List<Element>> groups = new LinkedHashMap<>();

        for (Element parent : doc.select("body *")) {
            Elements children = parent.children();
            if (children.size() < MIN_SIBLINGS_FOR_PATTERN) continue;

            Map<String, List<Element>> bySignature = children.stream()
                    .collect(Collectors.groupingBy(this::signature, LinkedHashMap::new, Collectors.toList()));

            bySignature.forEach((sig, els) -> {
                if (els.size() >= MIN_SIBLINGS_FOR_PATTERN) {
                    groups.merge(sig, els, (a, b) -> a.size() >= b.size() ? a : b);
                }
            });
        }

        return groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Element>>>comparingInt(e -> e.getValue().size()).reversed())
                .limit(MAX_PATTERNS)
                .map(e -> new RepeatingPattern(e.getKey(), e.getValue().size(), e.getValue()))
                .toList();
    }

    private String signature(Element el) {
        String classes = el.classNames().stream().sorted().collect(Collectors.joining("."));
        return el.tagName() + (classes.isEmpty() ? "" : "." + classes);
    }

    private String assemble(String head, List<RepeatingPattern> patterns, int budget) {
        StringBuilder sb = new StringBuilder(budget);
        sb.append("<!-- HEAD SUMMARY -->\n").append(head).append("\n");

        int remaining = budget - sb.length();
        if (patterns.isEmpty() || remaining <= 0) return sb.toString();

        int budgetPerPattern = Math.max(500, remaining / patterns.size());

        for (RepeatingPattern p : patterns) {
            sb.append("\n<!-- REPEATING PATTERN: ").append(p.signature())
                    .append(" (").append(p.count()).append(" occurrences) -->\n");

            int used = 0;
            for (int i = 0; i < Math.min(MAX_EXAMPLES_PER_PATTERN, p.elements().size()); i++) {
                String snippet = p.elements().get(i).outerHtml();
                int allowed = Math.min(snippet.length(), budgetPerPattern - used);
                if (allowed <= 0) break;
                sb.append(truncate(snippet, allowed)).append("\n");
                used += allowed;
            }
            if (sb.length() >= budget) break;
        }
        return sb.length() > budget ? sb.substring(0, budget) : sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record Sample(String html, List<RepeatingPattern> patterns) {
        public boolean hasRepeatingContent() {
            return !patterns.isEmpty();
        }
    }

    public record RepeatingPattern(String signature, int count, List<Element> elements) {
    }
}