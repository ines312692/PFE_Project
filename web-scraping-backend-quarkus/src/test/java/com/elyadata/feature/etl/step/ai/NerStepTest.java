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
@DisplayName("NerStep")
class NerStepTest {

    @Mock LlmChatClient llm;

    private NerStep step;

    @BeforeEach
    void setUp() {
        step = new NerStep(llm);
    }

    @Test
    @DisplayName("type() returns AI_NER")
    void type_returnsAiNer() {
        assertThat(step.type()).isEqualTo(EtlStepType.AI_NER);
    }

    @Nested
    @DisplayName("merge() behaviour via apply()")
    class MergeBehaviour {

        @Test
        @DisplayName("merges _entities map from LLM response")
        void mergesEntitiesMap() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"persons\":[\"Alice\"],\"organizations\":[\"ACME\"]," +
                            "\"locations\":[],\"dates\":[],\"amounts\":[]}]");

            var records = List.of(Map.<String, Object>of(
                    "text", "Alice works at ACME"));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst()).containsKey("_entities");
            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) result.getFirst().get("_entities");
            assertThat(entities.get("persons")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> persons = (List<String>) entities.get("persons");
            assertThat(persons).contains("Alice");
        }

        @Test
        @DisplayName("original fields are preserved alongside _entities")
        void originalFieldsPreserved() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"persons\":[],\"organizations\":[],\"locations\":[]," +
                            "\"dates\":[],\"amounts\":[]}]");

            var records = List.of(Map.<String, Object>of("text", "Hello world", "id", 42));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst()).containsKey("text");
            assertThat(result.getFirst()).containsKey("id");
            assertThat(result.getFirst()).containsKey("_entities");
        }

        @Test
        @DisplayName("entire enrichment map stored in _entities field")
        void entireEnrichmentStoredAsEntities() {
            when(llm.isAvailable()).thenReturn(true);
            String llmResponse = "[{\"persons\":[\"Bob\"],\"organizations\":[\"Corp\"]," +
                    "\"locations\":[\"Paris\"],\"dates\":[\"2024-01-01\"],\"amounts\":[\"100€\"]}]";
            when(llm.complete(any(), any())).thenReturn(llmResponse);

            var records = List.of(Map.<String, Object>of("text", "Bob at Corp in Paris"));
            var result = step.apply(records, Map.of());

            @SuppressWarnings("unchecked")
            Map<String, Object> entities = (Map<String, Object>) result.getFirst().get("_entities");
            assertThat(entities).containsKey("persons");
            assertThat(entities).containsKey("organizations");
            assertThat(entities).containsKey("locations");
            assertThat(entities).containsKey("dates");
            assertThat(entities).containsKey("amounts");
        }
    }

    @Nested
    @DisplayName("custom fields param")
    class CustomFields {

        @Test
        @DisplayName("'fields' param limits which fields are sent to LLM")
        void fieldsParam_limitsExtraction() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"persons\":[],\"organizations\":[],\"locations\":[]," +
                            "\"dates\":[],\"amounts\":[]}]");

            var records = List.of(Map.<String, Object>of(
                    "title", "Dr. Smith",
                    "description", "Unrelated text",
                    "count", 5
            ));
            step.apply(records, Map.of("fields", List.of("title")));

            verify(llm).complete(
                    any(),
                    argThat(msg -> msg.contains("Dr. Smith") && !msg.contains("Unrelated text"))
            );
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
    @DisplayName("LLM exception → records preserved without _entities field")
    void llmException_recordsPreserved() {
        when(llm.isAvailable()).thenReturn(true);
        when(llm.complete(any(), any())).thenThrow(new RuntimeException("timeout"));

        var records = List.of(Map.<String, Object>of("text", "Alice met Bob"));
        var result = step.apply(records, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).doesNotContainKey("_entities");
    }
}