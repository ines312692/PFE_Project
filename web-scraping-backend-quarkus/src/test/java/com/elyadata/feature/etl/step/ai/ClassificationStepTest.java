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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassificationStep")
class ClassificationStepTest {

    @Mock LlmChatClient llm;

    private ClassificationStep step;

    @BeforeEach
    void setUp() {
        step = new ClassificationStep(llm);
    }

    @Test
    @DisplayName("type() returns AI_CLASSIFY")
    void type_returnsAiClassify() {
        assertThat(step.type()).isEqualTo(EtlStepType.AI_CLASSIFY);
    }

    @Nested
    @DisplayName("merge() behaviour via apply()")
    class MergeBehaviour {

        @Test
        @DisplayName("merges _category and _category_confidence from LLM response")
        void mergesCategoryAndConfidence() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"category\":\"product\",\"confidence\":0.95}]");

            var records = List.of(Map.<String, Object>of("title", "Laptop 16GB RAM"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("_category")).isEqualTo("product");
            assertThat(result.getFirst().get("_category_confidence")).isEqualTo(0.95);
            assertThat(result.getFirst()).containsKey("title");
        }

        @Test
        @DisplayName("missing category in LLM response defaults to 'other'")
        void missingCategory_defaultsToOther() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"confidence\":0.1}]");

            var records = List.of(Map.<String, Object>of("text", "something"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("_category")).isEqualTo("other");
        }

        @Test
        @DisplayName("original record fields are preserved after merge")
        void originalFieldsPreserved() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"category\":\"article\",\"confidence\":0.8}]");

            var records = List.of(Map.<String, Object>of("title", "News", "url", "http://x.com"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst()).containsKey("title");
            assertThat(result.getFirst()).containsKey("url");
        }
    }

    @Nested
    @DisplayName("custom labels param")
    class CustomLabels {

        @Test
        @DisplayName("system prompt contains custom labels when 'labels' param provided")
        void customLabels_inSystemPrompt() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"category\":\"news\",\"confidence\":0.9}]");

            var records = List.of(Map.<String, Object>of("text", "Breaking news"));
            step.apply(records, Map.of("labels", List.of("news", "sports", "tech")));

            verify(llm).complete(
                    argThat(prompt -> prompt.contains("news") && prompt.contains("sports")),
                    any()
            );
        }
    }

    @Nested
    @DisplayName("multiple records")
    class MultipleRecords {

        @Test
        @DisplayName("each record gets its own category and confidence")
        void eachRecordClassified() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"category\":\"product\",\"confidence\":0.9}," +
                            "{\"category\":\"review\",\"confidence\":0.7}]");

            var records = List.of(
                    Map.<String, Object>of("text", "Buy now!"),
                    Map.<String, Object>of("text", "Great product")
            );
            var result = step.apply(records, Map.of());

            assertThat(result.get(0).get("_category")).isEqualTo("product");
            assertThat(result.get(1).get("_category")).isEqualTo("review");
        }
    }

    @Test
    @DisplayName("empty records → returns empty without calling LLM")
    void emptyRecords_noCalls() {
        var result = step.apply(List.of(), Map.of());

        assertThat(result).isEmpty();
        verify(llm, never()).complete(any(), any());
    }
}