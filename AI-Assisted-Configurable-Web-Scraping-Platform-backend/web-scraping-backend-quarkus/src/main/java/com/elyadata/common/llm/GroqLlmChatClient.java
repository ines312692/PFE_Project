package com.elyadata.common.llm;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class GroqLlmChatClient implements LlmChatClient {

    private static final String PROVIDER = "groq";
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final double DETERMINISTIC = 0.0;
    private static final Pattern RETRY_AFTER = Pattern.compile(
            "try\\s+again\\s+in\\s+(\\d+(?:\\.\\d+)?)\\s*(ms|s)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "llm.groq.model", defaultValue = "llama-3.3-70b-versatile")
    String modelName;

    private final AtomicReference<OpenAiChatModel> chatModelRef = new AtomicReference<>();

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new LlmUnavailableException(
                    "Groq API key is not configured — set the GROQ_API_KEY environment variable");
        }
        try {
            ChatResponse response = model().chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userMessage)))
                    .build());
            return response.aiMessage().text();
        } catch (OpenAiHttpException e) {
            throw translate(e);
        } catch (RuntimeException e) {
            throw new LlmUnavailableException("Groq completion failed: " + e.getMessage(), e);
        }
    }

    private RuntimeException translate(OpenAiHttpException e) {
        String body = e.getMessage() != null ? e.getMessage() : "";
        boolean isRateLimit = body.contains("rate_limit_exceeded")
                || body.contains("Rate limit");

        if (isRateLimit) {
            Duration wait = parseRetryAfter(body).orElse(DEFAULT_RATE_LIMIT_WAIT);
            log.warn("[LLM] Groq rate-limited (retry-after={}ms): {}",
                    wait.toMillis(), trimBody(body));
            return new LlmRateLimitException(PROVIDER,
                    "Groq rate limit — retry after " + wait.toMillis() + "ms",
                    wait);
        }
        return new LlmUnavailableException("Groq HTTP error: " + trimBody(body), e);
    }

    private Optional<Duration> parseRetryAfter(String message) {
        Matcher m = RETRY_AFTER.matcher(message);
        if (!m.find()) return Optional.empty();
        double value = Double.parseDouble(m.group(1));
        String unit = m.group(2).toLowerCase();
        long millis = "ms".equals(unit) ? (long) value : (long) (value * 1000);
        millis = Math.max(millis, 100);
        millis = Math.min(millis, MAX_RETRY_AFTER.toMillis());
        return Optional.of(Duration.ofMillis(millis));
    }

    private static String trimBody(String body) {
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private OpenAiChatModel model() {
        OpenAiChatModel local = chatModelRef.get();
        if (local == null) {
            synchronized (this) {
                local = chatModelRef.get();
                if (local == null) {
                    log.info("[LLM] Initialising Groq chat model '{}'", modelName);
                    local = OpenAiChatModel.builder()
                            .baseUrl(GROQ_BASE_URL)
                            .apiKey(apiKey)
                            .modelName(modelName)
                            .temperature(DETERMINISTIC)
                            .timeout(TIMEOUT)
                            .maxRetries(1)
                            .build();
                    chatModelRef.set(local);
                }
            }
        }
        return local;
    }
}