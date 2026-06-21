package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@ApplicationScoped
public class SpaHydrationAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FRAMEWORK_NEXTJS = "next.js";
    private static final String PAGE_INFO_KEY = "pageInfo";
    private static final Pattern WINDOW_STATE = Pattern.compile(
            "window\\.(__NUXT__|__APOLLO_STATE__|__INITIAL_STATE__|__PRELOADED_STATE__)\\s*=\\s*(\\{[^}]*+})\\s*(?:;|</script>|\\Z)",
            Pattern.DOTALL
    );

    private static final int MAX_NODES_WALKED = 5_000;

    public Optional<Finding> analyze(String html) {
        if (html == null || html.isEmpty()) return Optional.empty();

        Optional<Finding> nextData = tryNextData(html);
        if (nextData.isPresent()) return nextData;

        return tryWindowState(html);
    }

    private Optional<Finding> tryNextData(String html) {
        Document doc = Jsoup.parse(html);
        Element script = doc.selectFirst("script#__NEXT_DATA__");
        if (script == null) return Optional.empty();

        try {
            JsonNode root = MAPPER.readTree(script.data());
            JsonNode query = root.path("query");
            for (String p : new String[]{"page", "p", "pg", "offset"}) {
                if (query.has(p) && query.get(p).asText().matches("\\d+")) {
                    return Optional.of(new Finding(
                            PaginationType.URL_PARAM, 0.97,
                            "__NEXT_DATA__: query.'" + p + "' is set",
                            p, FRAMEWORK_NEXTJS
                    ));
                }
            }
            JsonNode pageProps = root.path("props").path("pageProps");
            Optional<Finding> walked = walkForPagination(pageProps, "__NEXT_DATA__.pageProps", FRAMEWORK_NEXTJS);
            if (walked.isPresent()) return walked;

            return walkForPagination(root, "__NEXT_DATA__", FRAMEWORK_NEXTJS);
        } catch (Exception e) {
            log.debug("[SpaHydration] __NEXT_DATA__ parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Finding> tryWindowState(String html) {
        Matcher m = WINDOW_STATE.matcher(html);
        while (m.find()) {
            String key = m.group(1);
            String json = m.group(2);
            try {
                JsonNode root = MAPPER.readTree(json);
                String framework = resolveFramework(key);
                Optional<Finding> walked = walkForPagination(root, "window." + key, framework);
                if (walked.isPresent()) return walked;
            } catch (Exception e) {
                log.debug("[SpaHydration] {} parse failed: {}", key, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static String resolveFramework(String key) {
        if ("__NUXT__".equals(key)) return "nuxt";
        if ("__APOLLO_STATE__".equals(key)) return "apollo";
        return "generic-spa";
    }

    private Optional<Finding> walkForPagination(JsonNode root, String path, String framework) {
        if (root == null || root.isMissingNode() || root.isNull()) return Optional.empty();

        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(root);
        int visited = 0;

        Finding cursor = null;
        Finding numbered = null;

        while (!stack.isEmpty() && visited++ < MAX_NODES_WALKED) {
            JsonNode node = stack.pop();
            if (node.isObject()) {
                if (cursor == null) {
                    cursor = detectRelayPagination(node, path, framework);
                }
                if (numbered == null) {
                    numbered = detectNumberedPagination(node, path, framework);
                }
                pushObjectChildren(stack, node);
            } else if (node.isArray()) {
                node.forEach(stack::push);
            }
        }

        if (cursor != null) return Optional.of(cursor);
        if (numbered != null) return Optional.of(numbered);
        return Optional.empty();
    }

    private Finding detectRelayPagination(JsonNode node, String path, String framework) {
        if (!node.has(PAGE_INFO_KEY) || !node.path(PAGE_INFO_KEY).has("hasNextPage")) {
            return null;
        }
        boolean hasNext = node.path(PAGE_INFO_KEY).path("hasNextPage").asBoolean(false);
        return new Finding(
                PaginationType.CURSOR,
                hasNext ? 0.92 : 0.85,
                path + ": " + PAGE_INFO_KEY + ".hasNextPage=" + hasNext + " (Relay)",
                null, framework
        );
    }

    private Finding detectNumberedPagination(JsonNode node, String path, String framework) {
        int score = 0;
        if (hasIntField(node, "totalPages", "total_pages", "pageCount", "page_count", "numPages")) score += 60;
        if (hasIntField(node, "currentPage", "current_page", "page", "pageNumber")) score += 30;
        if (hasIntField(node, "perPage", "per_page", "pageSize", "page_size", "limit")) score += 10;
        if (score < 60) return null;
        int total = readInt(node);
        return new Finding(
                PaginationType.NUMBERED, 0.85,
                path + ": numbered keys (totalPages=" + total + ")",
                null, framework
        );
    }

    private static void pushObjectChildren(Deque<JsonNode> stack, JsonNode node) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            stack.push(entry.getValue());
        }
    }

    private static boolean hasIntField(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && (v.isInt() || v.isLong()
                    || (v.isTextual() && v.asText().matches("\\d+")))) return true;
        }
        return false;
    }

    private static int readInt(JsonNode node) {
        for (String k : new String[]{"totalPages", "total_pages", "pageCount", "page_count", "numPages"}) {
            JsonNode v = node.get(k);
            if (v != null && v.canConvertToInt()) return v.asInt();
        }
        return -1;
    }

    public record Finding(
            PaginationType type,
            double confidence,
            String reason,
            String suggestedSelector,
            String framework
    ) {
    }
}