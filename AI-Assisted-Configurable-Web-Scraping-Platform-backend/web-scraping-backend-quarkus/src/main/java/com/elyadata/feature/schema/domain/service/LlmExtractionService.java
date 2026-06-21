package com.elyadata.feature.schema.domain.service;

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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class LlmExtractionService {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    private static final int MAX_CHARS = 8_000;

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(\\[.*?]|\\{.*?})\\s*```", Pattern.DOTALL);

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String groqApiKey;

    @ConfigProperty(name = "llm.groq.model", defaultValue = DEFAULT_MODEL)
    String modelName;

    private final LlmSecurityGuard securityGuard;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Map<String, Object>> extract(
            String rawContent,
            String prompt,
            String outputFormat,
            String llmModel,
            UUID jobId,
            UUID userId) {

        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.error("[LLM] Groq API key not configured — set GROQ_API_KEY");
            return List.of();
        }
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("[LLM] Raw content is empty — nothing to extract");
            return List.of();
        }

        LlmSecurityGuard.ValidationResult validation =
                securityGuard.validateInput(prompt, outputFormat, jobId, userId);

        if (!validation.isAllowed()) {
            log.warn("[LLM] Request blocked by security guard: code={} reason=\"{}\" job={}",
                    validation.errorCode(), validation.errorMessage(), jobId);
            return List.of();
        }

        String safePrompt = validation.sanitizedPrompt();
        String content = rawContent.length() > MAX_CHARS
                ? rawContent.substring(0, MAX_CHARS)
                : rawContent;
        String model = resolveModel(llmModel);

        log.info("[LLM] Calling Groq model={} content_len={} job={}", model, content.length(), jobId);

        try {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(GROQ_BASE_URL)
                    .apiKey(groqApiKey)
                    .modelName(model)
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(60))
                    .build();

            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(buildSystemPrompt(outputFormat)),
                            UserMessage.from(buildUserMessage(content, safePrompt))
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String raw = response.aiMessage().text();
            log.debug("[LLM] Raw response: {}", raw);

            List<Map<String, Object>> records = parseResponse(raw);

            List<Map<String, Object>> safeRecords = securityGuard.validateOutput(records, jobId);
            log.info("[LLM] Extracted {} record(s) after security validation, job={}", safeRecords.size(), jobId);
            return safeRecords;

        } catch (Exception e) {
            log.error("[LLM] Extraction failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private static String buildSystemPrompt(String outputFormat) {
        String base = """
                You are a precise web data extraction assistant.
                Extract structured data from the provided web page content.
                Return ONLY valid JSON no markdown, no explanation, no preamble.
                Always return a JSON array of objects, even for a single result.
                """;
        if (outputFormat != null && !outputFormat.isBlank()) {
            base += "\nRequired output schema:\n" + outputFormat;
        }
        return base;
    }

    private static String buildUserMessage(String content, String prompt) {
        return "PAGE CONTENT:\n" + content + "\n\n"
                + "EXTRACTION TASK:\n" + prompt + "\n\n"
                + "Return ONLY a JSON array. "
                + "Example: [{\"field1\": \"value1\", \"field2\": \"value2\"}]";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResponse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String clean = raw.strip();
        Matcher fence = JSON_FENCE.matcher(clean);
        if (fence.find()) {
            clean = fence.group(1).strip();
        } else {
            clean = clean.replaceAll("```(?:json)?|```", "").strip();
        }
        try {
            Object parsed = mapper.readValue(clean, Object.class);
            if (parsed instanceof List<?> list) return (List<Map<String, Object>>) list;
            if (parsed instanceof Map<?, ?> map) {
                for (String key : List.of("data", "results", "items", "products", "records")) {
                    if (map.get(key) instanceof List<?> inner)
                        return (List<Map<String, Object>>) inner;
                }
                return List.of((Map<String, Object>) map);
            }
        } catch (Exception e) {
            log.error("[LLM] JSON parse failed: {}", e.getMessage());
        }
        return List.of();
    }

    private String resolveModel(String key) {
        if (key == null || key.isBlank()) return modelName;
        return switch (key) {
            case "CLAUDE_3_5_SONNET", "CLAUDE_3_OPUS", "GPT_4O", "GPT_4_TURBO" -> DEFAULT_MODEL;
            case "CLAUDE_3_5_HAIKU", "GPT_4O_MINI", "GPT_3_5" -> "llama-3.1-8b-instant";
            default -> modelName;
        };
    }
}