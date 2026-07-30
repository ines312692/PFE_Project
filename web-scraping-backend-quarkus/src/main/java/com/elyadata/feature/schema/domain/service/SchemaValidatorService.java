package com.elyadata.feature.schema.domain.service;

import com.elyadata.common.llm.LlmCallOptions;
import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.common.llm.observability.LlmFeature;
import com.elyadata.feature.schema.application.dto.SchemaValidationRequest;
import com.elyadata.feature.schema.application.dto.SchemaValidationRequest.FieldToValidate;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse.FieldStatus;
import com.elyadata.feature.schema.application.dto.SchemaValidationResponse.FieldValidationResult;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.browser.PlaywrightBrowser;
import com.elyadata.feature.scraper.browser.humanizer.Humanizer;
import com.elyadata.feature.scraper.browser.humanizer.HumanizerLevel;
import com.elyadata.feature.scraper.browser.stealth.ConsistencyProfileFactory;
import com.elyadata.feature.scraper.browser.stealth.StealthProfile;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SchemaValidatorService {

    private static final String VALIDATION_MODEL = "llama-3.3-70b-versatile";
    private static final double VALIDATION_TEMPERATURE = 0.1;
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(60);

    private static final int PREVIEW_VALUES_COUNT = 3;
    private static final int PREVIEW_VALUE_MAX_LENGTH = 120;
    private static final int MAX_REASONABLE_MATCHES = 200;
    private static final double MIN_ACCEPTABLE_CONFIDENCE = 0.6;
    private static final int BROWSER_TIMEOUT_MS = 45_000;

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private static final Set<String> FORBIDDEN_GENERIC = Set.of(
            "title", "body", "html", "head", "div", "span", "p", "a",
            "h1", "h2", "h3", "h4", "*", "main", "article", "section"
    );

    private final LlmChatClient llm;
    private final LlmCallContext context;
    private final HtmlSampler htmlSampler;
    private final StealthProfile stealthProfile;
    private final ConsistencyProfileFactory consistencyProfileFactory;
    private final Humanizer humanizer;
    private final ObjectMapper mapper = new ObjectMapper();

    public SchemaValidationResponse validate(SchemaValidationRequest request) {
        log.info("[VALIDATE] url={} fields={}", request.targetUrl(), request.fields().size());

        String html;
        try {
            html = fetchPageHtml(request.targetUrl());
        } catch (Exception e) {
            log.error("[VALIDATE] Failed to load page {}: {}", request.targetUrl(), e.getMessage());
            return SchemaValidationResponse.error(
                    "Could not load the target page. Check the URL and try again.");
        }

        if (html == null || html.isBlank()) {
            return SchemaValidationResponse.error("Target page returned empty content.");
        }

        Document doc = Jsoup.parse(html);

        List<FieldValidationResult> results = new ArrayList<>();
        List<FieldToValidate> brokenFields = new ArrayList<>();

        for (FieldToValidate field : request.fields()) {
            FieldValidationResult quickResult = evaluateField(field, doc);
            if (quickResult.status() == FieldStatus.OK) {
                results.add(quickResult);
            } else {
                brokenFields.add(field);
                results.add(quickResult);
            }
        }

        if (!brokenFields.isEmpty()) {
            Map<String, SuggestedSelector> suggestions = askLlmForSuggestions(html, brokenFields);
            results = mergeSuggestions(results, suggestions, doc);
        }

        int validCount = (int) results.stream().filter(r -> r.status() == FieldStatus.OK).count();
        int brokenCount = results.size() - validCount;

        return SchemaValidationResponse.builder()
                .success(true)
                .totalFields(results.size())
                .validFields(validCount)
                .brokenFields(brokenCount)
                .results(results)
                .build();
    }

    private String fetchPageHtml(String url) {
        InternalScrapeRequest probeRequest = InternalScrapeRequest.builder()
                .targetUrl(url)
                .stealthMode(true)
                .jsEnabled(true)
                .timeoutMs(BROWSER_TIMEOUT_MS)
                .delayMs(500)
                .scrollToBottom(false)
                .build();

        PlaywrightBrowser browser = new PlaywrightBrowser(
                probeRequest,
                stealthProfile,
                consistencyProfileFactory.buildFor(null),
                humanizer,
                HumanizerLevel.REALISTIC
        );

        try {
            browser.start();
            browser.navigate(url);
            browser.dismissCookies();
            PageSnapshot snapshot = browser.snapshot();
            return snapshot != null ? snapshot.getHtml() : null;
        } finally {
            try {
                browser.close();
            } catch (Exception ignored) {
            }
        }
    }

    private FieldValidationResult evaluateField(FieldToValidate field, Document doc) {
        try {
            Elements matches = doc.select(field.selector());

            if (matches.isEmpty()) {
                return FieldValidationResult.builder()
                        .fieldName(field.name())
                        .currentSelector(field.selector())
                        .status(FieldStatus.BROKEN)
                        .matchCount(0)
                        .errorReason("Selector matches 0 elements on the page")
                        .previewValues(List.of())
                        .build();
            }

            List<String> preview = matches.stream()
                    .limit(PREVIEW_VALUES_COUNT)
                    .map(el -> extractPreviewValue(el, field))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            if (preview.isEmpty()) {
                return FieldValidationResult.builder()
                        .fieldName(field.name())
                        .currentSelector(field.selector())
                        .status(FieldStatus.BROKEN)
                        .matchCount(matches.size())
                        .errorReason("Matched " + matches.size()
                                + " element(s) but all values are empty")
                        .previewValues(List.of())
                        .build();
            }

            return FieldValidationResult.builder()
                    .fieldName(field.name())
                    .currentSelector(field.selector())
                    .status(FieldStatus.OK)
                    .matchCount(matches.size())
                    .previewValues(preview)
                    .build();

        } catch (Exception e) {
            return FieldValidationResult.builder()
                    .fieldName(field.name())
                    .currentSelector(field.selector())
                    .status(FieldStatus.BROKEN)
                    .matchCount(0)
                    .errorReason("Invalid selector: " + e.getMessage())
                    .previewValues(List.of())
                    .build();
        }
    }

    private String extractPreviewValue(Element el, FieldToValidate field) {
        String raw = switch (field.type()) {
            case ATTRIBUTE -> field.attribute() != null ? el.attr(field.attribute()) : "";
            case IMAGE -> firstNonBlank(el.attr("src"), el.attr("data-src"), el.attr("data-lazy-src"));
            case URL -> el.attr("href");
            case HTML -> el.html();
            default -> el.text();
        };
        if (raw == null) return null;
        String cleaned = raw.replaceAll("\\s+", " ").strip();
        return cleaned.length() > PREVIEW_VALUE_MAX_LENGTH
                ? cleaned.substring(0, PREVIEW_VALUE_MAX_LENGTH) + "…"
                : cleaned;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private Map<String, SuggestedSelector> askLlmForSuggestions(
            String html, List<FieldToValidate> brokenFields) {

        if (!llm.isAvailable()) {
            log.warn("[VALIDATE] Groq API key not configured — skipping suggestions");
            return Map.of();
        }

        try (var ignored = context.open(LlmFeature.SCHEMA_VALIDATE, null, null, null)) {
            HtmlSampler.Sample sample = htmlSampler.sample(html);

            LlmResult result = llm.completeWithUsage(
                    SchemaValidatorPrompt.systemPrompt(),
                    SchemaValidatorPrompt.userMessage(sample.html(), brokenFields),
                    LlmCallOptions.of(VALIDATION_MODEL, VALIDATION_TEMPERATURE, VALIDATION_TIMEOUT));

            return parseLlmResponse(result.text());

        } catch (Exception e) {
            log.error("[VALIDATE] LLM call failed: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, SuggestedSelector> parseLlmResponse(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        clean = fence.find() ? fence.group(1).strip() : clean.replaceAll("```(?:json)?|```", "").strip();

        try {
            Map<String, Object> parsed = mapper.readValue(clean, Map.class);
            if (!(parsed.get("suggestions") instanceof List<?> list)) return Map.of();

            Map<String, SuggestedSelector> result = new LinkedHashMap<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    String name = asString(entry.get("fieldName"));
                    String selector = asString(entry.get("newSelector"));
                    Double conf = asDouble(entry.get("confidence"));
                    String rationale = asString(entry.get("rationale"));

                    if (name != null && selector != null && conf != null) {
                        result.put(name, new SuggestedSelector(selector, conf, rationale));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[VALIDATE] Failed to parse LLM response: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<FieldValidationResult> mergeSuggestions(
            List<FieldValidationResult> results,
            Map<String, SuggestedSelector> suggestions,
            Document doc) {

        List<FieldValidationResult> merged = new ArrayList<>(results.size());
        for (FieldValidationResult r : results) {
            if (r.status() == FieldStatus.OK) {
                merged.add(r);
                continue;
            }
            SuggestedSelector suggestion = suggestions.get(r.fieldName());
            if (suggestion == null) {
                merged.add(r);
                continue;
            }

            String rejection = rejectionReason(r.fieldName(), suggestion, doc);
            if (rejection != null) {
                log.warn("[VALIDATE] Suggestion rejected for '{}': {} (selector='{}')",
                        r.fieldName(), rejection, suggestion.selector());
                merged.add(r);
                continue;
            }

            merged.add(FieldValidationResult.builder()
                    .fieldName(r.fieldName())
                    .currentSelector(r.currentSelector())
                    .status(FieldStatus.SUGGESTED)
                    .matchCount(r.matchCount())
                    .previewValues(r.previewValues())
                    .errorReason(r.errorReason())
                    .suggestedSelector(suggestion.selector())
                    .suggestionConfidence(suggestion.confidence())
                    .suggestionRationale(suggestion.rationale())
                    .build());
        }
        return merged;
    }

    private String rejectionReason(String fieldName, SuggestedSelector s, Document doc) {
        if (s.confidence() < MIN_ACCEPTABLE_CONFIDENCE) {
            return "confidence " + s.confidence() + " below threshold " + MIN_ACCEPTABLE_CONFIDENCE;
        }
        String trimmed = s.selector().trim();
        String lower = trimmed.toLowerCase();
        if (FORBIDDEN_GENERIC.contains(lower)) {
            return "selector is a generic tag";
        }
        if (lower.equalsIgnoreCase(fieldName)) {
            return "selector identical to field name";
        }
        try {
            Elements matches = doc.select(trimmed);
            if (matches.isEmpty()) return "suggested selector matches 0 elements";
            if (matches.size() > MAX_REASONABLE_MATCHES) {
                return "suggested selector matches too many elements (" + matches.size() + ")";
            }
            return null;
        } catch (Exception e) {
            return "invalid CSS: " + e.getMessage();
        }
    }

    private static String asString(Object o) {
        if (o == null) return null;
        String s = o instanceof String str ? str : o.toString();
        return s.isBlank() ? null : s.strip();
    }

    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record SuggestedSelector(String selector, double confidence, String rationale) {
    }
}