package com.elyadata.feature.scraper.browser.llm;

import com.elyadata.common.llm.LlmCallOptions;
import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.common.llm.observability.LlmFeature;
import com.elyadata.feature.schema.domain.service.ActionHtmlSampler;
import com.elyadata.feature.scraper.browser.llm.ActionResolverPrompt.Rejection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ActionSelectorResolver {

    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final double TEMPERATURE = 0.1;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final double MIN_CONFIDENCE = 0.6;
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_FRAGMENT_CHARS = 15_000;
    private static final int MAX_MATCHES = 50;

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private static final Set<String> FORBIDDEN_GENERIC = Set.of(
            "html", "body", "head", "div", "span", "p", "a", "button",
            "ul", "ol", "li", "main", "article", "section", "*"
    );

    private final LlmChatClient llm;
    private final LlmCallContext context;
    private final ActionHtmlSampler actionHtmlSampler;
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<String> resolveClickTarget(String fullHtml, String instruction, String containerHint) {
        if (!llm.isAvailable()) {
            log.warn("[ACTION-RESOLVE] Groq API key not configured — cannot resolve '{}'", instruction);
            return Optional.empty();
        }
        if (fullHtml == null || fullHtml.isBlank() || instruction == null || instruction.isBlank()) {
            return Optional.empty();
        }

        Document doc = Jsoup.parse(fullHtml);
        Element scope = resolveScope(doc, containerHint);

        String rawScope = (scope != null ? scope.outerHtml() : fullHtml);
        String fragment = actionHtmlSampler.sample(rawScope, MAX_FRAGMENT_CHARS);

        log.info("[ACTION-RESOLVE] instruction='{}' container='{}' fragment={} chars",
                instruction, containerHint != null ? containerHint : "(none)", fragment.length());

        List<Rejection> rejections = new ArrayList<>();
        try (var ignored = context.open(LlmFeature.ACTION_RESOLVE, null, null, null)) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                Candidate c = askLlm(fragment, instruction, containerHint, rejections);
                if (c == null) {
                    log.warn("[ACTION-RESOLVE] no candidate on attempt {}/{}", attempt, MAX_ATTEMPTS);
                    continue;
                }
                String reason = reject(c, containerHint, doc);
                if (reason == null) {
                    log.info("[ACTION-RESOLVE] accepted '{}' (conf={}, why={})",
                            c.selector(), c.confidence(), c.rationale());
                    return Optional.of(c.selector().trim());
                }
                log.warn("[ACTION-RESOLVE] rejected '{}' attempt {}/{}: {}",
                        c.selector(), attempt, MAX_ATTEMPTS, reason);
                rejections.add(new Rejection(c.selector(), reason));
            }
        }
        return Optional.empty();
    }

    private Element resolveScope(Document doc, String containerHint) {
        if (containerHint == null || containerHint.isBlank()) {
            return null;
        }
        try {
            return doc.select(containerHint).first();
        } catch (Exception e) {
            log.warn("[ACTION-RESOLVE] container hint '{}' is not valid CSS: {}",
                    containerHint, e.getMessage());
            return null;
        }
    }

    private Candidate askLlm(String fragment, String instruction,
                             String containerHint, List<Rejection> rejections) {
        try {
            LlmResult result = llm.completeWithUsage(
                    ActionResolverPrompt.systemPrompt(),
                    ActionResolverPrompt.userMessage(fragment, instruction, containerHint, rejections),
                    LlmCallOptions.of(MODEL, TEMPERATURE, TIMEOUT));

            return parse(result.text());
        } catch (Exception e) {
            log.error("[ACTION-RESOLVE] LLM call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Candidate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        clean = fence.find() ? fence.group(1).strip() : clean.replaceAll("```(?:json)?|```", "").strip();

        try {
            Map<String, Object> parsed = mapper.readValue(clean, Map.class);
            String selector = asString(parsed.get("selector"));
            Double conf = asDouble(parsed.get("confidence"));
            String rationale = asString(parsed.get("rationale"));
            if (selector == null || conf == null) {
                return null;
            }
            return new Candidate(selector, conf, rationale);
        } catch (Exception e) {
            log.warn("[ACTION-RESOLVE] failed to parse LLM response: {}", e.getMessage());
            return null;
        }
    }

    private String reject(Candidate c, String containerHint, Document doc) {
        if (c.confidence() < MIN_CONFIDENCE) {
            return "confidence " + c.confidence() + " below " + MIN_CONFIDENCE;
        }
        String selector = c.selector().trim();
        String lower = selector.toLowerCase();
        if (FORBIDDEN_GENERIC.contains(lower)) {
            return "generic tag selector";
        }
        if (containerHint != null && selector.equalsIgnoreCase(containerHint.trim())) {
            return "selector points at the container itself, not an item inside it";
        }
        try {
            Elements matches = doc.select(selector);
            if (matches.isEmpty()) {
                return "matches 0 elements on the page";
            }
            if (matches.size() > MAX_MATCHES) {
                return "matches " + matches.size() + " elements (too broad)";
            }
            return null;
        } catch (Exception e) {
            return "invalid CSS: " + e.getMessage();
        }
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        String s = o instanceof String str ? str : o.toString();
        return s.isBlank() ? null : s.strip();
    }

    private static Double asDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Candidate(String selector, double confidence, String rationale) {
    }
}