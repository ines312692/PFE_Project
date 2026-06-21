package com.elyadata.feature.job.domain.service.planner;

import com.elyadata.feature.job.application.dto.ActionPlanRequest;
import com.elyadata.feature.job.application.dto.ActionPlanResponse;
import com.elyadata.feature.job.application.dto.PlannedActionDto;
import com.elyadata.feature.job.domain.model.PageActionType;
import com.elyadata.feature.job.domain.service.port.PageRendererPort;
import com.elyadata.feature.schema.domain.service.ActionHtmlSampler;
import com.elyadata.feature.schema.domain.service.HtmlSampler;
import com.elyadata.feature.schema.domain.service.LlmSecurityGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ActionPlannerService {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final double TEMPERATURE = 0.2;
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private final PageRendererPort pageRenderer;
    private final ActionHtmlSampler actionHtmlSampler;

    private static final int HTML_BUDGET = 10_000;
    private static final int FETCH_TIMEOUT_MS = 8_000;
    private static final int MAX_ACTIONS = 12;

    private static final String FETCH_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String groqApiKey;

    private final HtmlSampler htmlSampler;
    private final LlmSecurityGuard securityGuard;
    private final ObjectMapper mapper = new ObjectMapper();

    public ActionPlanResponse plan(ActionPlanRequest req, UUID userId) {

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.error("[ACTION-PLAN] Groq API key not configured");
            return ActionPlanResponse.error("AI assistant is not configured on the server");
        }

        if (req.instruction() == null || req.instruction().isBlank()) {
            return ActionPlanResponse.error("Please describe the steps you want to perform");
        }

        LlmSecurityGuard.ValidationResult validation =
                securityGuard.validateInput(req.instruction(), null, req.jobId(), userId);
        if (!validation.isAllowed()) {
            log.warn("[ACTION-PLAN] Blocked by security guard: {} - {}",
                    validation.errorCode(), validation.errorMessage());
            return ActionPlanResponse.error(validation.errorMessage());
        }
        String safeInstruction = validation.sanitizedPrompt();

        String sampledHtml = fetchAndSample(req.targetUrl());
        boolean grounded = sampledHtml != null && !sampledHtml.isBlank();

        try {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(GROQ_BASE_URL)
                    .apiKey(groqApiKey)
                    .modelName(MODEL)
                    .temperature(TEMPERATURE)
                    .timeout(TIMEOUT)
                    .build();

            ChatRequest chatReq = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(ActionPlannerPrompt.systemPrompt()),
                            UserMessage.from(ActionPlannerPrompt.userMessage(
                                    safeInstruction, req.targetUrl(), sampledHtml))
                    ))
                    .build();

            log.info("[ACTION-PLAN] model={} grounded={} job={} user={}",
                    MODEL, grounded, req.jobId(), userId);

            ChatResponse response = chatModel.chat(chatReq);
            String raw = response.aiMessage().text();
            log.debug("[ACTION-PLAN] Raw LLM response: {}", raw);

            return parseResponse(raw, grounded);

        } catch (Exception e) {
            log.error("[ACTION-PLAN] LLM call failed: {}", e.getMessage(), e);
            return ActionPlanResponse.error(
                    "The AI planner is temporarily unavailable. Please try again.");
        }
    }

    private static final int MIN_RENDERED_HTML = 20_000;

    private String fetchAndSample(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Optional<String> rendered = pageRenderer.renderHtml(url, true);
        if (rendered.isPresent() && rendered.get().length() >= MIN_RENDERED_HTML) {
            String sampled = actionHtmlSampler.sample(rendered.get(), HTML_BUDGET);
            log.info("[ACTION-PLAN] Sampled {} chars (RENDERED) from {}",
                    sampled.length(), url);
            return sampled;
        }

        log.info("[ACTION-PLAN] Rendered HTML too thin or unavailable for {} — falling back to Jsoup", url);
        return fetchWithJsoup(url);
    }

    private String fetchWithJsoup(String url) {
        try {
            String html = Jsoup.connect(url)
                    .userAgent(FETCH_USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .get()
                    .outerHtml();

            HtmlSampler.Sample sample = htmlSampler.sample(html, HTML_BUDGET);
            log.info("[ACTION-PLAN] Sampled {} chars (JSOUP) from {} (patterns={})",
                    sample.html().length(), url, sample.patterns().size());
            return sample.html();
        } catch (Exception e) {
            log.warn("[ACTION-PLAN] Jsoup fetch failed for '{}' ({}) — planning from instruction alone",
                    url, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ActionPlanResponse parseResponse(String raw, boolean grounded) {
        if (raw == null || raw.isBlank()) {
            return ActionPlanResponse.error("Empty response from AI planner");
        }

        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        if (fence.find()) {
            clean = fence.group(1).strip();
        } else {
            clean = clean.replaceAll("```(?:json)?", "").strip();
        }

        try {
            Map<String, Object> parsed = mapper.readValue(clean, Map.class);
            Object actionsRaw = parsed.get("actions");
            String reasoning = asString(parsed.get("reasoning"));

            if (!(actionsRaw instanceof List<?> list) || list.isEmpty()) {
                return ActionPlanResponse.error("AI returned no usable steps");
            }

            List<PlannedActionDto> actions = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                PlannedActionDto dto = toDto((Map<String, Object>) m);
                if (dto != null) {
                    actions.add(dto);
                }
                if (actions.size() >= MAX_ACTIONS) {
                    break;
                }
            }

            if (actions.isEmpty()) {
                return ActionPlanResponse.error("AI returned no valid steps");
            }
            return ActionPlanResponse.ok(actions, reasoning, MODEL, grounded);

        } catch (Exception e) {
            log.warn("[ACTION-PLAN] Failed to parse JSON response: {}", e.getMessage());
            return ActionPlanResponse.error("Could not understand the AI planner response");
        }
    }

    private PlannedActionDto toDto(Map<String, Object> m) {
        PageActionType type = parseType(asString(m.get("type")));
        if (type == null) {
            return null;
        }
        return PlannedActionDto.builder()
                .type(type)
                .selector(asString(m.get("selector")))
                .value(asString(m.get("value")))
                .timeoutMs(asInt(m.get("timeoutMs")))
                .optional(asBool(m.get("optional")))
                .label(asString(m.get("label")))
                .build();
    }

    private static PageActionType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PageActionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[ACTION-PLAN] Unknown action type from LLM: '{}'", raw);
            return null;
        }
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        String s = o instanceof String str ? str : o.toString();
        return s.isBlank() || "null".equalsIgnoreCase(s.trim()) ? null : s.strip();
    }

    private static Integer asInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean asBool(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(o.toString().trim());
    }
}