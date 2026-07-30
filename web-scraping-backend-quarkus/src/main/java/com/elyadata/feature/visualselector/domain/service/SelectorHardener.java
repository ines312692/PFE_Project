package com.elyadata.feature.visualselector.domain.service;

import com.elyadata.feature.visualselector.application.dto.HardenSelectorRequest;
import com.elyadata.feature.visualselector.domain.model.HardenedSelector;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@ApplicationScoped
public class SelectorHardener {

    private static final int MAX_PREVIEW_VALUES = 5;
    private static final int PREVIEW_VALUE_MAX_LEN = 120;
    private static final int TOO_BROAD_THRESHOLD = 200;

    private static final Set<String> FORBIDDEN_GENERIC = Set.of(
            "html", "head", "body", "div", "span", "p", "a", "ul", "li",
            "main", "article", "section", "header", "footer", "*"
    );

    private static final Pattern UNSTABLE_CLASS = Pattern.compile(
            "(?i)\\b(?:[a-z]+-[0-9a-f]{4,}|css-[0-9a-z]+|jsx-[0-9]+|sc-[0-9a-z]+|_[0-9a-z]{5,})\\b");

    private static final List<String> STABLE_DATA_ATTRS = List.of(
            "data-testid", "data-test", "data-qa", "data-id", "itemprop");

    public HardenedSelector harden(HardenSelectorRequest request) {
        Document doc = Jsoup.parse(request.html() != null ? request.html() : "",
                request.url() != null ? request.url() : "");

        Element target = locateTarget(doc, request.candidateSelector());
        if (target == null) {
            log.warn("[VISUAL] Candidate matched nothing: '{}'", request.candidateSelector());
            return HardenedSelector.builder()
                    .cssSelector(request.candidateSelector())
                    .xpath(toXPath(request.candidateSelector()))
                    .matchCount(0)
                    .unique(false)
                    .previewValues(List.of())
                    .suggestedName(suggestName(request.candidateSelector(), null))
                    .warning("The clicked element could not be located in the snapshot. "
                            + "The selector is returned as-is — verify it manually.")
                    .build();
        }

        String hardened = buildRobustSelector(doc, target, request.expectList());
        Elements matches = safeSelect(doc, hardened);
        int count = matches.size();
        boolean unique = count == 1;

        String warning = buildWarning(count, request.expectList());
        List<String> preview = previewValues(matches);

        log.info("[VISUAL] Hardened '{}' → '{}' (matches={}, expectList={})",
                request.candidateSelector(), hardened, count, request.expectList());

        return HardenedSelector.builder()
                .cssSelector(hardened)
                .xpath(toXPath(hardened))
                .matchCount(count)
                .unique(unique)
                .previewValues(preview)
                .suggestedName(suggestName(hardened, target))
                .warning(warning)
                .build();
    }

    private Element locateTarget(Document doc, String candidate) {
        Elements matched = safeSelect(doc, candidate);
        return matched.isEmpty() ? null : matched.first();
    }

    private String buildRobustSelector(Document doc, Element el, boolean expectList) {
        if (!expectList) {
            String byId = uniqueIdSelector(doc, el);
            if (byId != null) return byId;

            String byData = uniqueDataSelector(doc, el);
            if (byData != null) return byData;
        }

        String tagClass = tagWithStableClass(el);
        if (tagClass != null) {
            Elements m = safeSelect(doc, tagClass);
            if (expectList && m.size() > 1) return tagClass;
            if (!expectList && m.size() == 1) return tagClass;
        }

        String anchored = ancestorAnchoredPath(doc, el);
        if (anchored != null) {
            Elements m = safeSelect(doc, anchored);
            if (!m.isEmpty()) return anchored;
        }

        return el.cssSelector();
    }

    private String uniqueIdSelector(Document doc, Element el) {
        String id = el.id();
        if (id == null || id.isBlank() || UNSTABLE_CLASS.matcher(id).find()) return null;
        String sel = el.tagName() + "#" + cssEscape(id);
        return safeSelect(doc, sel).size() == 1 ? sel : null;
    }

    private String uniqueDataSelector(Document doc, Element el) {
        for (String attr : STABLE_DATA_ATTRS) {
            if (el.hasAttr(attr)) {
                String val = el.attr(attr);
                if (val.isBlank()) continue;
                String sel = el.tagName() + "[" + attr + "=\"" + val + "\"]";
                if (safeSelect(doc, sel).size() == 1) return sel;
            }
        }
        return null;
    }

    private String tagWithStableClass(Element el) {
        List<String> stable = stableClasses(el);
        if (stable.isEmpty()) return null;
        return el.tagName() + "." + String.join(".", stable.stream().map(this::cssEscape).toList());
    }

    private String ancestorAnchoredPath(Document doc, Element el) {
        Element anchor = el;
        List<String> tail = new ArrayList<>();
        int depth = 0;
        while (anchor != null && depth < 6) {
            anchor.id();
            String idSel = !anchor.id().isBlank() && !UNSTABLE_CLASS.matcher(anchor.id()).find()
                    ? "#" + cssEscape(anchor.id()) : null;
            if (idSel != null && safeSelect(doc, idSel).size() == 1) {
                String path = idSel + (tail.isEmpty() ? "" : " " + String.join(" ", tail));
                if (!safeSelect(doc, path).isEmpty()) return path;
            }
            tail.add(0, segmentFor(anchor));
            anchor = anchor.parent() instanceof Element p ? p : null;
            depth++;
        }
        return null;
    }

    private String segmentFor(Element el) {
        String tagClass = tagWithStableClass(el);
        return tagClass != null ? tagClass : el.tagName();
    }

    private List<String> stableClasses(Element el) {
        return el.classNames().stream()
                .filter(c -> !c.isBlank())
                .filter(c -> !UNSTABLE_CLASS.matcher(c).find())
                .limit(2)
                .collect(Collectors.toList());
    }

    private List<String> previewValues(Elements matches) {
        List<String> out = new ArrayList<>();
        for (Element el : matches) {
            String text = el.text();
            if (text == null || text.isBlank()) {
                // fall back to a meaningful attribute for media/links
                text = firstNonBlank(el.attr("href"), el.attr("src"), el.attr("alt"));
            }
            if (text != null && !text.isBlank()) {
                out.add(truncate(text.strip()));
            }
            if (out.size() >= MAX_PREVIEW_VALUES) break;
        }
        return out;
    }

    private String buildWarning(int count, boolean expectList) {
        if (count == 0) {
            return "Selector matches no elements — it may be too specific.";
        }
        if (count > TOO_BROAD_THRESHOLD) {
            return "Selector matches " + count + " elements — likely too broad, consider clicking a more specific element.";
        }
        if (!expectList && count > 1) {
            return "Selector matches " + count + " elements but a single value was expected. "
                    + "Only the first will be used, or switch to list mode.";
        }
        return null;
    }

    private String toXPath(String css) {
        if (css == null || css.isBlank()) return "";
        String[] parts = css.trim().split("\\s+");
        StringBuilder xpath = new StringBuilder();
        for (String part : parts) {
            xpath.append("//").append(segmentToXPath(part));
        }
        return xpath.toString();
    }

    private String segmentToXPath(String segment) {
        String tag = "*";
        List<String> predicates = new ArrayList<>();

        // id: tag#id
        int hash = segment.indexOf('#');
        int dot = segment.indexOf('.');
        int bracket = segment.indexOf('[');

        int firstSpecial = minPositive(hash, dot, bracket);
        if (firstSpecial > 0) {
            tag = segment.substring(0, firstSpecial);
        } else if (firstSpecial == -1) {
            tag = segment.isEmpty() ? "*" : segment;
        }

        if (hash >= 0) {
            int end = nextSpecial(segment, hash + 1);
            String id = segment.substring(hash + 1, end);
            predicates.add("@id='" + id + "'");
        }
        int searchFrom = 0;
        while ((dot = segment.indexOf('.', searchFrom)) >= 0) {
            int end = nextSpecial(segment, dot + 1);
            String cls = segment.substring(dot + 1, end);
            predicates.add("contains(concat(' ',normalize-space(@class),' '),' " + cls + " ')");
            searchFrom = end;
        }
        if (bracket >= 0) {
            int close = segment.indexOf(']', bracket);
            if (close > bracket) {
                String attrExpr = segment.substring(bracket + 1, close);
                predicates.add(attrToXPath(attrExpr));
            }
        }

        String pred = predicates.isEmpty() ? "" : "[" + String.join(" and ", predicates) + "]";
        return tag + pred;
    }

    private String attrToXPath(String attrExpr) {
        int eq = attrExpr.indexOf('=');
        if (eq < 0) return "@" + attrExpr;
        String name = attrExpr.substring(0, eq).trim();
        String val = attrExpr.substring(eq + 1).trim().replaceAll("^[\"']|[\"']$", "");
        return "@" + name + "='" + val + "'";
    }

    private String suggestName(String selector, Element el) {
        if (el != null) {
            for (String attr : STABLE_DATA_ATTRS) {
                if (el.hasAttr(attr) && !el.attr(attr).isBlank()) {
                    return toFieldName(el.attr(attr));
                }
            }
            if (el.id() != null && !el.id().isBlank()) return toFieldName(el.id());
            List<String> classes = stableClasses(el);
            if (!classes.isEmpty()) return toFieldName(classes.getFirst());
            return toFieldName(el.tagName());
        }
        return "field";
    }

    private String toFieldName(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "field" : cleaned;
    }

    private Elements safeSelect(Document doc, String selector) {
        try {
            if (selector == null || selector.isBlank()
                    || FORBIDDEN_GENERIC.contains(selector.trim().toLowerCase())) {
                return new Elements();
            }
            return doc.select(selector);
        } catch (Exception e) {
            log.debug("[VISUAL] Invalid selector '{}': {}", selector, e.getMessage());
            return new Elements();
        }
    }

    private String cssEscape(String value) {
        // Escape characters that are syntactically significant in a CSS identifier.
        return value.replaceAll("([!\"#$%&'()*+,./:;<=>?@\\[\\]^`{|}~])", "\\\\$1");
    }

    private String truncate(String s) {
        return s.length() <= PREVIEW_VALUE_MAX_LEN ? s : s.substring(0, PREVIEW_VALUE_MAX_LEN) + "…";
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private int minPositive(int... values) {
        int min = -1;
        for (int v : values) {
            if (v >= 0 && (min == -1 || v < min)) min = v;
        }
        return min;
    }

    private int nextSpecial(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == '.' || c == '[' || c == ' ') return i;
        }
        return s.length();
    }
}