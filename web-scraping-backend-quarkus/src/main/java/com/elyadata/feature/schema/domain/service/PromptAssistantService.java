package com.elyadata.feature.schema.domain.service;

import com.elyadata.common.llm.LlmCallOptions;
import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.common.llm.observability.LlmFeature;
import com.elyadata.feature.schema.application.dto.PromptAssistAction;
import com.elyadata.feature.schema.application.dto.PromptAssistRequest;
import com.elyadata.feature.schema.application.dto.PromptAssistResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PromptAssistantService {

    private static final String FAST_MODEL = "llama-3.1-8b-instant";
    private static final double TEMPERATURE = 0.2;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmChatClient llm;
    private final LlmCallContext context;
    private final LlmSecurityGuard securityGuard;

    public PromptAssistResponse assist(PromptAssistRequest req, UUID userId) {

        if (!llm.isAvailable()) {
            log.error("[ASSIST] Groq API key not configured");
            return PromptAssistResponse.error("AI assistant is not configured on the server");
        }

        String userInput = pickUserInput(req);
        if (userInput != null && !userInput.isBlank()) {
            LlmSecurityGuard.ValidationResult validation =
                    securityGuard.validateInput(userInput, null, req.jobId(), userId);
            if (!validation.isAllowed()) {
                log.warn("[ASSIST] Blocked by security guard: {} - {}",
                        validation.errorCode(), validation.errorMessage());
                return PromptAssistResponse.error(validation.errorMessage());
            }
        }

        if (!isActionValid(req)) {
            return PromptAssistResponse.error(
                    "Missing required field for action " + req.action());
        }

        try (var ignored = context.open(LlmFeature.SCHEMA_ASSIST, req.jobId(), null, userId)) {
            log.info("[ASSIST] action={} model={} job={} user={}",
                    req.action(), FAST_MODEL, req.jobId(), userId);

            LlmResult result = llm.completeWithUsage(
                    systemPrompt(req.action()),
                    userMessage(req),
                    LlmCallOptions.of(FAST_MODEL, TEMPERATURE, TIMEOUT));

            log.debug("[ASSIST] Raw LLM response: {}", result.text());
            return parseResponse(result.text());

        } catch (Exception e) {
            log.error("[ASSIST] LLM call failed: {}", e.getMessage(), e);
            return PromptAssistResponse.error(
                    "The AI assistant is temporarily unavailable. Please try again.");
        }
    }

    private static String systemPrompt(PromptAssistAction action) {
        return switch (action) {
            case GENERATE -> """
                    You are an expert at writing precise extraction prompts for an automated web scraping system.
                    
                    A user describes in plain language what data they want extracted from a webpage.
                    Convert their description into:
                    1. A structured extraction prompt — clear, typed, edge-case-aware.
                    2. A JSON example showing the exact expected output structure.
                    3. A short one-line reasoning.
                    
                    Rules for the prompt:
                    - Start with "Extract [main entity] from the page."
                    - List every field with its type: name (string), price (number), rating (number 0-5), date (ISO 8601 string), url (string), available (boolean).
                    - State explicitly: missing fields should be returned as null.
                    - State whether the output is a single object or a list of objects.
                    - Maximum 150 words.
                    
                    Rules for the output format:
                    - Concrete JSON example, with realistic placeholder values matching the types.
                    - If list, show an array with ONE example object.
                    - Maximum 25 lines.
                    
                    YOU MUST RETURN A SINGLE VALID JSON OBJECT, NOTHING ELSE.
                    NO markdown fences. NO preamble. NO trailing text.
                    
                    Response shape:
                    {"prompt":"<the prompt>","outputFormat":"<the JSON example as a string>","reasoning":"<one line>"}
                    """;

            case IMPROVE -> """
                    You are an expert at refining extraction prompts for a web scraping LLM.
                    
                    The user has written a draft. Improve it for:
                    - Clarity: remove ambiguity.
                    - Specificity: add explicit types (string, number, boolean, ISO 8601 date, URL).
                    - Robustness: explicit handling of missing values (return null).
                    - Output shape: state whether result is a single object or a list.
                    
                    HARD CONSTRAINT: do NOT add fields the user did not request. Stay strictly within their scope.
                    Keep the improved prompt under 150 words.
                    
                    Also produce a JSON output-format example matching the improved prompt.
                    
                    YOU MUST RETURN A SINGLE VALID JSON OBJECT, NOTHING ELSE.
                    NO markdown fences. NO preamble.
                    
                    Response shape:
                    {"prompt":"<the improved prompt>","outputFormat":"<JSON example as a string>","reasoning":"<one to two sentences describing what you changed>"}
                    """;

            case SUGGEST_FORMAT -> """
                    You are an expert at JSON schemas for web data extraction.
                    
                    Given an extraction prompt, produce a concrete JSON example showing the expected
                    output structure with realistic placeholder values.
                    
                    Rules:
                    - Use proper types: string, number, boolean, null.
                    - Match the prompt's intent EXACTLY — do not add fields it does not mention.
                    - If the prompt describes multiple items, return an array with one example object.
                    - Maximum 30 lines.
                    
                    YOU MUST RETURN A SINGLE VALID JSON OBJECT, NOTHING ELSE.
                    
                    Response shape:
                    {"prompt":"","outputFormat":"<the JSON example as a string>","reasoning":"<one line>"}
                    """;

            case SUGGEST_FIELDS -> """
                    You are an expert at identifying extractable data from common website types.
                    
                    Given a URL, infer what kind of page this likely is, then propose the most useful
                    fields to extract. Reference patterns:
                    - E-commerce product page: name, brand, price, currency, rating, reviews_count, availability, description, image_urls.
                    - Product listing: array of {name, price, url, image_url, rating}.
                    - News article: title, author, published_date (ISO 8601), body, tags, source.
                    - Job listing: title, company, location, remote (boolean), salary_range, description, posted_date.
                    - Real estate listing: price, surface_m2, rooms, bedrooms, location, photos, description.
                    - Restaurant: name, address, rating, cuisine_type, price_range, opening_hours.
                    
                    Produce a complete extraction prompt AND a JSON example matching it.
                    
                    YOU MUST RETURN A SINGLE VALID JSON OBJECT, NOTHING ELSE.
                    
                    Response shape:
                    {"prompt":"<extraction prompt>","outputFormat":"<JSON example as a string>","reasoning":"<page type detected + why these fields>"}
                    """;
        };
    }

    private static String userMessage(PromptAssistRequest req) {
        StringBuilder sb = new StringBuilder();

        if (req.targetUrl() != null && !req.targetUrl().isBlank()) {
            sb.append("TARGET URL:\n").append(req.targetUrl()).append("\n\n");
        }

        switch (req.action()) {
            case GENERATE -> sb.append("USER DESCRIPTION:\n").append(req.description());
            case IMPROVE -> {
                sb.append("CURRENT PROMPT:\n").append(req.currentPrompt());
                if (req.currentOutputFormat() != null && !req.currentOutputFormat().isBlank()) {
                    sb.append("\n\nCURRENT OUTPUT FORMAT:\n").append(req.currentOutputFormat());
                }
            }
            case SUGGEST_FORMAT -> sb.append("CURRENT PROMPT:\n").append(req.currentPrompt());
            case SUGGEST_FIELDS -> sb.append("Analyse the target URL above and propose extractable fields.");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private PromptAssistResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PromptAssistResponse.error("Empty response from AI assistant");
        }

        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        if (fence.find()) {
            clean = fence.group(1).strip();
        } else {
            clean = clean.replaceAll("```(?:json)?", "").strip();
        }

        try {
            Map<String, Object> parsed = MAPPER.readValue(clean, Map.class);

            String prompt = asString(parsed.get("prompt"));
            String format = asString(parsed.get("outputFormat"));
            String reasoning = asString(parsed.get("reasoning"));

            if ((prompt == null || prompt.isBlank()) && (format == null || format.isBlank())) {
                return PromptAssistResponse.error("AI returned no usable suggestion");
            }
            return PromptAssistResponse.ok(prompt, format, reasoning, FAST_MODEL);

        } catch (Exception e) {
            log.warn("[ASSIST] Failed to parse JSON response: {}", e.getMessage());
            return PromptAssistResponse.ok(clean, null,
                    "Returned as plain text (JSON parse failed)", FAST_MODEL);
        }
    }

    private static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s.isBlank() ? null : s;
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return o.toString();
        }
    }

    private static String pickUserInput(PromptAssistRequest req) {
        return switch (req.action()) {
            case GENERATE -> req.description();
            case IMPROVE, SUGGEST_FORMAT -> req.currentPrompt();
            case SUGGEST_FIELDS -> null;
        };
    }

    private static boolean isActionValid(PromptAssistRequest req) {
        return switch (req.action()) {
            case GENERATE -> notBlank(req.description());
            case IMPROVE, SUGGEST_FORMAT -> notBlank(req.currentPrompt());
            case SUGGEST_FIELDS -> notBlank(req.targetUrl());
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}