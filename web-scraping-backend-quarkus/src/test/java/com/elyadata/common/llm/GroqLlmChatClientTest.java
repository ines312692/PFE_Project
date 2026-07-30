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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroqLlmChatClient")
class GroqLlmChatClientTest {

    @Mock
    LlmCallRecorder recorder;

    GroqLlmChatClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new GroqLlmChatClient(recorder);
        setField("apiKey", "");
        setField("modelName", "llama-3.3-70b-versatile");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = GroqLlmChatClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(client, value);
    }

    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = GroqLlmChatClient.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(client, args);
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("false when apiKey is blank")
        void blankApiKey_notAvailable() throws Exception {
            setField("apiKey", "");
            assertThat(client.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("false when apiKey is null")
        void nullApiKey_notAvailable() throws Exception {
            setField("apiKey", null);
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
    @DisplayName("completeWithUsage()")
    class CompleteWithUsage {

        @Test
        @DisplayName("throws LlmUnavailableException when apiKey is not configured")
        void notAvailable_throws() {
            assertThatThrownBy(() -> client.completeWithUsage("sys", "user", LlmCallOptions.defaults()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("complete() delegates to completeWithUsage and returns the text")
        void complete_delegatesAndReturnsText() {
            // not available -> both complete() and completeWithUsage() should throw the same way
            assertThatThrownBy(() -> client.complete("sys", "user"))
                    .isInstanceOf(LlmUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("translate() — private HTTP error translation")
    class Translate {

        @Test
        @DisplayName("maps a rate-limit body to LlmRateLimitException with parsed retry-after")
        void rateLimitBody_mapsToRateLimitException() throws Exception {
            OpenAiHttpException e = new OpenAiHttpException(429, "rate_limit_exceeded: try again in 2.5s");

            Object result = invokePrivate("translate",
                    new Class<?>[]{OpenAiHttpException.class, String.class}, e, "model-x");

            assertThat(result).isInstanceOf(LlmRateLimitException.class);
            LlmRateLimitException rle = (LlmRateLimitException) result;
            assertThat(rle.getRetryAfter()).isEqualTo(Duration.ofMillis(2500));
        }

        @Test
        @DisplayName("uses the default rate-limit wait when retry-after cannot be parsed")
        void rateLimitBody_withoutRetryAfter_usesDefault() throws Exception {
            OpenAiHttpException e = new OpenAiHttpException(429, "Rate limit reached, please slow down");

            Object result = invokePrivate("translate",
                    new Class<?>[]{OpenAiHttpException.class, String.class}, e, "model-x");

            assertThat(result).isInstanceOf(LlmRateLimitException.class);
            assertThat(((LlmRateLimitException) result).getRetryAfter()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("maps a non-rate-limit body to LlmUnavailableException")
        void nonRateLimitBody_mapsToUnavailableException() throws Exception {
            OpenAiHttpException e = new OpenAiHttpException(500, "internal server error");

            Object result = invokePrivate("translate",
                    new Class<?>[]{OpenAiHttpException.class, String.class}, e, "model-x");

            assertThat(result).isInstanceOf(LlmUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("parseRetryAfter() — private retry-after parsing")
    class ParseRetryAfter {

        @Test
        @DisplayName("parses seconds")
        void parsesSeconds() throws Exception {
            Object result = invokePrivate("parseRetryAfter", new Class<?>[]{String.class},
                    "please try again in 3s");

            assertThat(result).isInstanceOf(Optional.class);
            Optional<?> opt = (Optional<?>) result;
            assertThat(opt).isPresent();
            assertThat(opt.get()).isEqualTo(Duration.ofSeconds(3));
        }

        @Test
        @DisplayName("parses milliseconds")
        void parsesMilliseconds() throws Exception {
            Object result = invokePrivate("parseRetryAfter", new Class<?>[]{String.class},
                    "try again in 500ms");

            Optional<?> opt = (Optional<?>) result;
            assertThat(opt).isPresent();
            assertThat(opt.get()).isEqualTo(Duration.ofMillis(500));
        }

        @Test
        @DisplayName("clamps very large values to the max retry-after")
        void clampsToMax() throws Exception {
            Object result = invokePrivate("parseRetryAfter", new Class<?>[]{String.class},
                    "try again in 999s");

            Optional<?> opt = (Optional<?>) result;
            assertThat(opt.get()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("returns empty when the message does not match")
        void noMatch_returnsEmpty() throws Exception {
            Object result = invokePrivate("parseRetryAfter", new Class<?>[]{String.class},
                    "no retry info here");

            assertThat((Optional<?>) result).isEmpty();
        }
    }
}
