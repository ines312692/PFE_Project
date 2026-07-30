package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SummarizationStep")
class SummarizationStepTest {

    @Mock LlmChatClient llm;

    private SummarizationStep step;

    @BeforeEach
    void setUp() {
        step = new SummarizationStep(llm);
    }

    @Test
    @DisplayName("type() returns AI_SUMMARIZE")
    void type_returnsAiSummarize() {
        assertThat(step.type()).isEqualTo(EtlStepType.AI_SUMMARIZE);
    }

    @Nested
    @DisplayName("merge() behaviour via apply()")
    class MergeBehaviour {

        @Test
        @DisplayName("merges _summary field from LLM response")
        void mergesSummaryField() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"summary\":\"A brief summary.\"}]");

            var records = List.of(Map.<String, Object>of(
                    "text", "This is a very long article about something."));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("_summary")).isEqualTo("A brief summary.");
            assertThat(result.getFirst()).containsKey("text");
        }

        @Test
        @DisplayName("missing 'summary' key in LLM response → _summary defaults to empty string")
        void missingSummaryKey_defaultsToEmpty() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{}]");

            var records = List.of(Map.<String, Object>of("text", "something"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("_summary")).isEqualTo("");
        }

        @Test
        @DisplayName("original record fields are preserved after merge")
        void originalFieldsPreserved() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"summary\":\"Short.\"}]");

            var records = List.of(Map.<String, Object>of(
                    "title", "Article", "url", "http://x.com", "text", "Long text"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst()).containsKey("title");
            assertThat(result.getFirst()).containsKey("url");
            assertThat(result.getFirst()).containsKey("text");
        }
    }

    @Nested
    @DisplayName("maxWords param")
    class MaxWordsParam {

        @Test
        @DisplayName("default maxWords (40) used when not specified")
        void defaultMaxWords_inPrompt() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"summary\":\"Short.\"}]");

            var records = List.of(Map.<String, Object>of("text", "long text"));
            step.apply(records, Map.of());

            verify(llm).complete(
                    argThat(prompt -> prompt.contains("40")),
                    any()
            );
        }

        @Test
        @DisplayName("custom maxWords param reflected in system prompt")
        void customMaxWords_inPrompt() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"summary\":\"Brief.\"}]");

            var records = List.of(Map.<String, Object>of("text", "long text here"));
            step.apply(records, Map.of("maxWords", 80));

            verify(llm).complete(
                    argThat(prompt -> prompt.contains("80")),
                    any()
            );
        }

        @Test
        @DisplayName("maxWords is capped at 200 (HARD_MAX_WORDS)")
        void maxWordsCappedAt200() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"summary\":\"ok.\"}]");

            var records = List.of(Map.<String, Object>of("text", "text"));
            step.apply(records, Map.of("maxWords", 999));

            verify(llm).complete(
                    argThat(prompt -> prompt.contains("200") && !prompt.contains("999")),
                    any()
            );
        }

        @Test
        @DisplayName("non-positive maxWords falls back to default (40)")
        void nonPositiveMaxWords_fallbackToDefault() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"summary\":\"ok.\"}]");

            var records = List.of(Map.<String, Object>of("text", "text"));
            step.apply(records, Map.of("maxWords", 0));

            verify(llm).complete(
                    argThat(prompt -> prompt.contains("40")),
                    any()
            );
        }
    }

    @Nested
    @DisplayName("multiple records")
    class MultipleRecords {

        @Test
        @DisplayName("each record gets its own summary")
        void eachRecordSummarized() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"summary\":\"First summary.\"},{\"summary\":\"Second summary.\"}]");

            var records = List.of(
                    Map.<String, Object>of("text", "Long article one"),
                    Map.<String, Object>of("text", "Long article two")
            );
            var result = step.apply(records, Map.of());

            assertThat(result.get(0).get("_summary")).isEqualTo("First summary.");
            assertThat(result.get(1).get("_summary")).isEqualTo("Second summary.");
        }
    }

    @Test
    @DisplayName("empty records → returns empty without calling LLM")
    void emptyRecords_noCalls() {
        var result = step.apply(List.of(), Map.of());

        assertThat(result).isEmpty();
        verify(llm, never()).complete(any(), any());
    }

    @Test
    @DisplayName("LLM exception → records preserved without _summary field")
    void llmException_recordsPreserved() {
        when(llm.isAvailable()).thenReturn(true);
        when(llm.complete(any(), any())).thenThrow(new RuntimeException("timeout"));

        var records = List.of(Map.<String, Object>of("text", "Some text to summarize"));
        var result = step.apply(records, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).doesNotContainKey("_summary");
    }
}