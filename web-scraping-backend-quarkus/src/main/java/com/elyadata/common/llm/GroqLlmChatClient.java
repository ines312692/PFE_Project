package com.elyadata.common.llm;

import com.elyadata.common.llm.observability.LlmCallOutcome;
import com.elyadata.common.llm.observability.LlmCallRecorder;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class GroqLlmChatClient implements LlmChatClient {

    private static final String PROVIDER = "groq";
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final int MAX_RETRIES = 1;

    private static final Pattern RETRY_AFTER = Pattern.compile(
            "try\\s+again\\s+in\\s+(\\d+(?:\\.\\d+)?)\\s*(ms|s)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "llm.groq.model", defaultValue = "llama-3.3-70b-versatile")
    String modelName;

    private final LlmCallRecorder recorder;

    private final Map<String, OpenAiChatModel> models = new ConcurrentHashMap<>();

    public GroqLlmChatClient(LlmCallRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        return completeWithUsage(systemPrompt, userMessage, LlmCallOptions.defaults()).text();
    }

    @Override
    public LlmResult completeWithUsage(String systemPrompt, String userMessage, LlmCallOptions options) {
        if (!isAvailable()) {
            throw new LlmUnavailableException(
                    "Groq API key is not configured — set the GROQ_API_KEY environment variable");
        }

        LlmCallOptions opts = options != null ? options : LlmCallOptions.defaults();
        String model = opts.resolveModel(modelName);
        int promptChars = length(systemPrompt) + length(userMessage);
        long t0 = System.nanoTime();

        try {
            ChatResponse response = model(opts).chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userMessage)))
                    .build());

            String text = response.aiMessage().text();
            LlmResult result = new LlmResult(text, response.tokenUsage(), model);

            recorder.record(PROVIDER, model, elapsedMs(t0), LlmCallOutcome.SUCCESS,
                    result.promptTokens(), result.completionTokens(), result.totalTokens(),
                    promptChars, length(text), null);

            return result;

        } catch (OpenAiHttpException e) {
            RuntimeException translated = translate(e, model);
            boolean rateLimited = translated instanceof LlmRateLimitException;
            recorder.record(PROVIDER, model, elapsedMs(t0),
                    rateLimited ? LlmCallOutcome.RATE_LIMITED : LlmCallOutcome.UNAVAILABLE,
                    null, null, null, promptChars, 0, translated.getMessage());
            throw translated;

        } catch (RuntimeException e) {
            recorder.record(PROVIDER, model, elapsedMs(t0), LlmCallOutcome.ERROR,
                    null, null, null, promptChars, 0, e.getMessage());
            throw new LlmUnavailableException("Groq completion failed: " + e.getMessage(), e);
        }
    }

    private OpenAiChatModel model(LlmCallOptions opts) {
        return models.computeIfAbsent(opts.cacheKey(modelName), key -> {
            String resolved = opts.resolveModel(modelName);
            log.info("[LLM] Initialising Groq chat model '{}' (temp={}, timeout={}s)",
                    resolved, opts.temperature(), opts.timeout().toSeconds());
            return OpenAiChatModel.builder()
                    .baseUrl(GROQ_BASE_URL)
                    .apiKey(apiKey)
                    .modelName(resolved)
                    .temperature(opts.temperature())
                    .timeout(opts.timeout())
                    .maxRetries(MAX_RETRIES)
                    .build();
        });
    }

    private RuntimeException translate(OpenAiHttpException e, String model) {
        String body = e.getMessage() != null ? e.getMessage() : "";
        boolean isRateLimit = body.contains("rate_limit_exceeded") || body.contains("Rate limit");

        if (isRateLimit) {
            Duration wait = parseRetryAfter(body).orElse(DEFAULT_RATE_LIMIT_WAIT);
            log.warn("[LLM] Groq rate-limited model={} (retry-after={}ms): {}",
                    model, wait.toMillis(), trimBody(body));
            return new LlmRateLimitException(PROVIDER,
                    "Groq rate limit — retry after " + wait.toMillis() + "ms", wait);
        }
        return new LlmUnavailableException("Groq HTTP error: " + trimBody(body), e);
    }

    private Optional<Duration> parseRetryAfter(String message) {
        Matcher m = RETRY_AFTER.matcher(message);
        if (!m.find()) return Optional.empty();
        double value = Double.parseDouble(m.group(1));
        long millis = "ms".equalsIgnoreCase(m.group(2)) ? (long) value : (long) (value * 1000);
        millis = Math.clamp(millis, 100L, MAX_RETRY_AFTER.toMillis());
        return Optional.of(Duration.ofMillis(millis));
    }

    private static String trimBody(String body) {
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}