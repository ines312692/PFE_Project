package com.elyadata.common.llm;

import com.elyadata.common.llm.observability.LlmCallRecorder;
import dev.ai4j.openai4j.OpenAiHttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroqVisionLlmClient")
class GroqVisionLlmClientTest {

    @Mock
    LlmCallRecorder recorder;

    GroqVisionLlmClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new GroqVisionLlmClient(recorder);
        setField("apiKey", "");
        setField("visionModel", "meta-llama/llama-4-scout-17b-16e-instruct");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = GroqVisionLlmClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(client, value);
    }

    private Object invokeStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = GroqVisionLlmClient.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("false when apiKey is blank")
        void blankApiKey_notAvailable() {
            assertThat(client.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("true when apiKey is set")
        void setApiKey_isAvailable() throws Exception {
            setField("apiKey", "sk-test");
            assertThat(client.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("analyzeImage()/analyzeImageWithUsage()")
    class AnalyzeImage {

        @Test
        @DisplayName("throws LlmUnavailableException when apiKey is not configured")
        void notAvailable_throws() {
            assertThatThrownBy(() -> client.analyzeImageWithUsage("prompt", "base64img", "image/png"))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("analyzeImage() delegates and surfaces the same failure")
        void analyzeImage_delegates() {
            assertThatThrownBy(() -> client.analyzeImage("prompt", "base64img", "image/png"))
                    .isInstanceOf(LlmUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("isRateLimit() — private static helper")
    class IsRateLimit {

        @Test
        @DisplayName("true when message contains rate_limit_exceeded")
        void rateLimitMessage_true() throws Exception {
            OpenAiHttpException e = new OpenAiHttpException(429, "rate_limit_exceeded: slow down");

            Object result = invokeStatic("isRateLimit", new Class<?>[]{OpenAiHttpException.class}, e);

            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("false for an unrelated error message")
        void otherMessage_false() throws Exception {
            OpenAiHttpException e = new OpenAiHttpException(500, "internal server error");

            Object result = invokeStatic("isRateLimit", new Class<?>[]{OpenAiHttpException.class}, e);

            assertThat(result).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("parseRetryAfter() — private static helper")
    class ParseRetryAfter {

        @Test
        @DisplayName("parses seconds and adds a 500ms buffer")
        void parsesSecondsWithBuffer() throws Exception {
            Object result = invokeStatic("parseRetryAfter", new Class<?>[]{String.class},
                    "try again in 2s");

            assertThat(result).isEqualTo(Duration.ofMillis(2500));
        }

        @Test
        @DisplayName("returns the default wait when the message is null")
        void nullMessage_returnsDefault() throws Exception {
            Object result = invokeStatic("parseRetryAfter", new Class<?>[]{String.class}, new Object[]{null});

            assertThat(result).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("returns the default wait when the message does not match")
        void noMatch_returnsDefault() throws Exception {
            Object result = invokeStatic("parseRetryAfter", new Class<?>[]{String.class},
                    "no retry info here");

            assertThat(result).isEqualTo(Duration.ofSeconds(5));
        }
    }
}
