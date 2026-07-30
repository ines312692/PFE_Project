package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmRateLimitException;
import com.elyadata.common.llm.LlmUnavailableException;
import com.elyadata.common.llm.observability.LlmCallContext;
import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmPaginationDetector")
class LlmPaginationDetectorTest {

    @Mock
    LlmChatClient llmChatClient;

    @Mock
    PaginationHtmlSampler sampler;

    LlmCallContext context;
    LlmPaginationDetector detector;

    @BeforeEach
    void setUp() {
        context = new LlmCallContext();
        detector = new LlmPaginationDetector(llmChatClient, sampler, context);
    }

    private PageSnapshot snapshot(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).text("").build();
    }

    @Test
    @DisplayName("returns empty when the sampler finds nothing relevant")
    void samplerReturnsBlank_returnsEmpty() {
        lenient().when(sampler.sample(any())).thenReturn("");

        Optional<PaginationStrategy.DetectionResult> result = detector.detect(snapshot("<html></html>"));

        assertThat(result).isEmpty();
    }

    @Nested
    @DisplayName("LLM failure paths")
    class LlmFailures {

        @Test
        @DisplayName("returns empty when the LLM is rate-limited")
        void rateLimited_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenThrow(new LlmRateLimitException("groq", "slow down", Duration.ofSeconds(1)));

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the LLM is unavailable")
        void unavailable_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenThrow(new LlmUnavailableException("no api key"));

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("returns empty on any unexpected exception")
        void unexpectedException_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any())).thenThrow(new RuntimeException("boom"));

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("clears the LLM call context scope even after a failure")
        void failure_leavesContextClean() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any())).thenThrow(new RuntimeException("boom"));

            detector.detect(snapshot("<html></html>"));

            assertThat(context.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("response interpretation")
    class ResponseInterpretation {

        @Test
        @DisplayName("returns empty when the response has no 'type' field")
        void missingType_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any())).thenReturn("{\"confidence\":0.9}");

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("returns empty when type is an unknown value")
        void unknownType_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"BOGUS\",\"confidence\":0.9}");

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the model reports NONE")
        void noneType_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"NONE\",\"confidence\":0.9}");

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("discards a result below the minimum usable confidence")
        void belowMinConfidence_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"NEXT_BUTTON\",\"confidence\":0.1}");

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }

        @Test
        @DisplayName("caps confidence at the max LLM confidence ceiling")
        void confidenceIsCapped() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"NEXT_BUTTON\",\"confidence\":0.99}");

            Optional<PaginationStrategy.DetectionResult> result =
                    detector.detect(snapshot("<html><a class=\"next\">Next</a></html>"));

            assertThat(result).isPresent();
            assertThat(result.get().confidence()).isEqualTo(0.65);
        }

        @Test
        @DisplayName("verifies the suggested selector against the DOM, dropping it when absent")
        void selectorNotInDom_isDropped() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"NEXT_BUTTON\",\"confidence\":0.5,"
                            + "\"suggestedSelector\":\".does-not-exist\"}");

            Optional<PaginationStrategy.DetectionResult> result =
                    detector.detect(snapshot("<html><a class=\"next\">Next</a></html>"));

            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PaginationType.NEXT_BUTTON);
            assertThat(result.get().suggestedSelector()).isNull();
        }

        @Test
        @DisplayName("keeps a selector that matches an element in the DOM")
        void selectorInDom_isKept() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("{\"type\":\"NEXT_BUTTON\",\"confidence\":0.5,"
                            + "\"suggestedSelector\":\".next\",\"reason\":\"single next control\"}");

            Optional<PaginationStrategy.DetectionResult> result =
                    detector.detect(snapshot("<html><a class=\"next\">Next</a></html>"));

            assertThat(result).isPresent();
            assertThat(result.get().suggestedSelector()).isEqualTo(".next");
            assertThat(result.get().reason()).contains("single next control");
        }

        @Test
        @DisplayName("extracts a JSON object embedded in markdown fences or surrounding prose")
        void jsonEmbeddedInProse_isExtracted() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any()))
                    .thenReturn("Sure, here you go:\n```json\n"
                            + "{\"type\":\"NUMBERED\",\"confidence\":0.5}\n```\nHope that helps!");

            Optional<PaginationStrategy.DetectionResult> result =
                    detector.detect(snapshot("<html></html>"));

            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PaginationType.NUMBERED);
        }

        @Test
        @DisplayName("returns empty when the response is not valid JSON")
        void invalidJson_returnsEmpty() {
            when(sampler.sample(any())).thenReturn("<nav>1 2 3</nav>");
            when(llmChatClient.complete(any(), any())).thenReturn("not json at all");

            assertThat(detector.detect(snapshot("<html></html>"))).isEmpty();
        }
    }
}
