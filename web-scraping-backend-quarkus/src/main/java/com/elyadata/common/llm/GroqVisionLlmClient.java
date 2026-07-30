package com.elyadata.common.llm;

import com.elyadata.common.llm.observability.LlmCallOutcome;
import com.elyadata.common.llm.observability.LlmCallRecorder;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class GroqVisionLlmClient implements VisionLlmClient {

    private static final String PROVIDER = "groq-vision";
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private static final Pattern RETRY_AFTER_PATTERN =
            Pattern.compile("try again in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);

    @ConfigProperty(name = "llm.groq.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "llm.groq.vision-model",
            defaultValue = "meta-llama/llama-4-scout-17b-16e-instruct")
    String visionModel;

    private final LlmCallRecorder recorder;
    private final AtomicReference<OpenAiChatModel> modelRef = new AtomicReference<>();

    public GroqVisionLlmClient(LlmCallRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String analyzeImage(String prompt, String imageBase64, String mimeType) {
        return analyzeImageWithUsage(prompt, imageBase64, mimeType).text();
    }

    @Override
    public LlmResult analyzeImageWithUsage(String prompt, String imageBase64, String mimeType) {
        if (!isAvailable()) {
            throw new LlmUnavailableException("Groq API key not configured — set llm.groq.api-key");
        }

        int promptChars = prompt == null ? 0 : prompt.length();
        long t0 = System.nanoTime();

        try {
            UserMessage message = UserMessage.from(
                    TextContent.from(prompt),
                    ImageContent.from(imageBase64, mimeType));

            ChatResponse response = model().chat(
                    ChatRequest.builder().messages(List.of(message)).build());

            String text = response.aiMessage().text();
            LlmResult result = new LlmResult(text, response.tokenUsage(), visionModel);

            recorder.record(PROVIDER, visionModel, elapsedMs(t0), LlmCallOutcome.SUCCESS,
                    result.promptTokens(), result.completionTokens(), result.totalTokens(),
                    promptChars, text == null ? 0 : text.length(), null);

            return result;

        } catch (OpenAiHttpException e) {
            boolean rateLimit = isRateLimit(e);
            recorder.record(PROVIDER, visionModel, elapsedMs(t0),
                    rateLimit ? LlmCallOutcome.RATE_LIMITED : LlmCallOutcome.UNAVAILABLE,
                    null, null, null, promptChars, 0, e.getMessage());

            if (rateLimit) {
                throw new LlmRateLimitException(PROVIDER,
                        "Groq vision TPM limit reached", parseRetryAfter(e.getMessage()));
            }
            throw new LlmUnavailableException("Groq vision call failed: " + e.getMessage(), e);

        } catch (Exception e) {
            recorder.record(PROVIDER, visionModel, elapsedMs(t0), LlmCallOutcome.ERROR,
                    null, null, null, promptChars, 0, e.getMessage());
            throw new LlmUnavailableException("Groq vision call failed: " + e.getMessage(), e);
        }
    }

    private OpenAiChatModel model() {
        OpenAiChatModel local = modelRef.get();
        if (local == null) {
            synchronized (this) {
                local = modelRef.get();
                if (local == null) {
                    log.info("[LLM-VISION] Initialising Groq vision model '{}'", visionModel);
                    local = OpenAiChatModel.builder()
                            .baseUrl(GROQ_BASE_URL)
                            .apiKey(apiKey)
                            .modelName(visionModel)
                            .temperature(0.0)
                            .timeout(TIMEOUT)
                            .maxRetries(1)
                            .build();
                    modelRef.set(local);
                }
            }
        }
        return local;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static boolean isRateLimit(OpenAiHttpException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("rate_limit_exceeded");
    }

    private static Duration parseRetryAfter(String message) {
        if (message == null) return DEFAULT_RETRY_AFTER;
        Matcher m = RETRY_AFTER_PATTERN.matcher(message);
        if (m.find()) {
            try {
                double secs = Double.parseDouble(m.group(1));
                return Duration.ofMillis((long) Math.ceil(secs * 1000) + 500);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_RETRY_AFTER;
    }
}