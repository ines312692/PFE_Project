package com.elyadata.feature.scraper.browser.llm;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmResult;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.feature.schema.domain.service.ActionHtmlSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionSelectorResolver")
class ActionSelectorResolverTest {

    @Mock
    LlmChatClient llm;

    @Spy
    LlmCallContext context = new LlmCallContext();

    @Mock
    ActionHtmlSampler actionHtmlSampler;

    @InjectMocks
    ActionSelectorResolver resolver;

    private static final String HTML = "<html><body><button class=\"login\">Login</button></body></html>";

    @BeforeEach
    void setUp() {
        lenient().when(llm.isAvailable()).thenReturn(false);
        lenient().when(actionHtmlSampler.sample(any(), anyInt())).thenReturn(HTML);
    }

    @Nested
    @DisplayName("resolveClickTarget() — early exit paths")
    class EarlyExits {

        @Test
        @DisplayName("returns empty when llm is not available")
        void llmUnavailable_returnsEmpty() {
            Optional<String> result = resolver.resolveClickTarget(HTML, "click login", null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when html is blank")
        void blankHtml_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);

            assertThat(resolver.resolveClickTarget("  ", "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty when instruction is blank")
        void blankInstruction_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);

            assertThat(resolver.resolveClickTarget(HTML, "  ", null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveClickTarget() — LLM resolution")
    class Resolution {

        @Test
        @DisplayName("accepts a candidate selector that matches the DOM above the confidence floor")
        void validCandidate_isAccepted() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\".login\",\"confidence\":0.9,\"rationale\":\"matches button\"}",
                            null, "test-model"));

            Optional<String> result = resolver.resolveClickTarget(HTML, "click login", null);

            assertThat(result).contains(".login");
        }

        @Test
        @DisplayName("rejects a candidate below the confidence floor and eventually gives up")
        void lowConfidence_isRejectedAndExhausted() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\".login\",\"confidence\":0.1}", null, "test-model"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("rejects a generic tag selector")
        void genericTagSelector_isRejected() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\"button\",\"confidence\":0.9}", null, "test-model"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("rejects a selector that matches nothing in the DOM")
        void selectorMatchesNothing_isRejected() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\".does-not-exist\",\"confidence\":0.9}", null, "test-model"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("rejects a selector equal to the container hint itself")
        void selectorEqualsContainerHint_isRejected() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\".login\",\"confidence\":0.9}", null, "test-model"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", ".login")).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the LLM call throws on every attempt")
        void llmThrowsOnEveryAttempt_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenThrow(new RuntimeException("groq down"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the LLM response is not parseable")
        void unparsableResponse_returnsEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult("not json", null, "test-model"));

            assertThat(resolver.resolveClickTarget(HTML, "click login", null)).isEmpty();
        }

        @Test
        @DisplayName("scopes the sample to the container hint when it matches an element")
        void containerHint_scopesSample() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.completeWithUsage(any(), any(), any()))
                    .thenReturn(new LlmResult(
                            "{\"selector\":\".login\",\"confidence\":0.9}", null, "test-model"));

            Optional<String> result = resolver.resolveClickTarget(HTML, "click login", "body");

            assertThat(result).contains(".login");
        }
    }
}
