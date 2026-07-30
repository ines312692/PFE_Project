package com.elyadata.feature.etl.step.ai;

import com.elyadata.common.llm.LlmChatClient;
import com.elyadata.common.llm.LlmUnavailableException;
import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("AiEnrichmentStep / AnomalyDetectionStep")
class AiEnrichmentStepTest {

    @Mock
    LlmChatClient llm;

    private AnomalyDetectionStep step;

    @BeforeEach
    void setUp() {
        step = new AnomalyDetectionStep(llm);
    }


    @Test
    @DisplayName("type() returns AI_DETECT_ANOMALIES")
    void correctType() {
        assertThat(step.type()).isEqualTo(EtlStepType.AI_DETECT_ANOMALIES);
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null records list → returns empty list without calling LLM")
        void nullRecordsReturnsEmpty() {
            List<Map<String, Object>> result = step.apply(null, Map.of());
            assertThat(result).isEmpty();
            verify(llm, never()).complete(any(), any());
        }

        @Test
        @DisplayName("empty records list → returns empty list without calling LLM")
        void emptyRecordsReturnsEmpty() {
            List<Map<String, Object>> result = step.apply(List.of(), Map.of());
            assertThat(result).isEmpty();
            verify(llm, never()).complete(any(), any());
        }

        @Test
        @DisplayName("throws LlmUnavailableException when LLM is not available")
        void llmUnavailableThrows() {
            when(llm.isAvailable()).thenReturn(false);
            List<Map<String, Object>> records = List.of(Map.of("price", -5));

            assertThatThrownBy(() -> step.apply(records, Map.of()))
                    .isInstanceOf(LlmUnavailableException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when records exceed MAX_RECORDS (200)")
        void tooManyRecordsThrows() {
            when(llm.isAvailable()).thenReturn(true);
            List<Map<String, Object>> bigList = new ArrayList<>();
            for (int i = 0; i < 201; i++) bigList.add(Map.of("x", i));

            assertThatThrownBy(() -> step.apply(bigList, Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("201");
        }
    }


    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("clean record — LLM returns empty anomalies → _has_anomaly=false")
        void cleanRecord() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"anomalies\":[]}]");

            List<Map<String, Object>> records = List.of(Map.of("price", 9.99));
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().get("_has_anomaly")).isEqualTo(false);
            assertThat(result.getFirst().get("_anomalies")).isInstanceOf(List.class);
            assertThat(result.getFirst().get("price")).isEqualTo(9.99);
        }

        @Test
        @DisplayName("anomalous record — LLM returns anomaly → _has_anomaly=true")
        void anomalousRecord() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"anomalies\":[{\"field\":\"price\",\"issue\":\"negative price\"}]}]");

            List<Map<String, Object>> records = List.of(Map.of("price", -1));
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().get("_has_anomaly")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<?> anomalies = (List<?>) result.getFirst().get("_anomalies");
            assertThat(anomalies).hasSize(1);
        }

        @Test
        @DisplayName("multiple records all enriched in order")
        void multipleRecordsEnriched() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any()))
                    .thenReturn("[{\"anomalies\":[]},{\"anomalies\":[{\"field\":\"qty\",\"issue\":\"zero\"}]}]");

            List<Map<String, Object>> records = List.of(
                    Map.of("name", "A", "price", 10.0),
                    Map.of("name", "B", "qty", 0)
            );
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).get("_has_anomaly")).isEqualTo(false);
            assertThat(result.get(1).get("_has_anomaly")).isEqualTo(true);
        }

        @Test
        @DisplayName("result is immutable (copyOf)")
        void resultIsImmutable() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"anomalies\":[]}]");

            List<Map<String, Object>> result = step.apply(List.of(Map.of("k", "v")), Map.of());

            assertThatThrownBy(() -> result.add(Map.of("extra", "entry")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }


    @Nested
    @DisplayName("LLM failure resilience")
    class LlmFailureResilience {

        @Test
        @DisplayName("LLM returns mismatched count → batch preserved unchanged")
        void mismatchedEnrichmentCountPreservesBatch() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("[{\"anomalies\":[]}]");

            List<Map<String, Object>> records = List.of(
                    Map.of("a", 1),
                    Map.of("b", 2)
            );
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).doesNotContainKey("_anomalies");
            assertThat(result.get(1)).doesNotContainKey("_anomalies");
        }

        @Test
        @DisplayName("LLM throws exception → batch preserved unchanged")
        void llmExceptionPreservesBatch() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenThrow(new RuntimeException("timeout"));

            List<Map<String, Object>> records = List.of(Map.of("price", 5.0));
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).doesNotContainKey("_anomalies");
        }

        @Test
        @DisplayName("LLM returns malformed JSON → batch preserved unchanged")
        void malformedJsonPreservesBatch() {
            when(llm.isAvailable()).thenReturn(true);
            when(llm.complete(any(), any())).thenReturn("not-json");

            List<Map<String, Object>> records = List.of(Map.of("k", "v"));
            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).doesNotContainKey("_anomalies");
        }
    }


    @Nested
    @DisplayName("batching behaviour")
    class Batching {

        @Test
        @DisplayName("default batch size is 20 — 21 records → 2 LLM calls")
        void defaultBatchSizeTwoCalls() {
            when(llm.isAvailable()).thenReturn(true);
            String batch20 = "[" + "{\"anomalies\":[]},"
                    .repeat(20).replaceAll(",$", "") + "]";
            String batch1 = "[{\"anomalies\":[]}]";
            when(llm.complete(any(), any()))
                    .thenReturn(batch20)
                    .thenReturn(batch1);

            List<Map<String, Object>> records = new ArrayList<>();
            for (int i = 0; i < 21; i++) records.add(Map.of("i", i));

            List<Map<String, Object>> result = step.apply(records, Map.of());

            assertThat(result).hasSize(21);
            verify(llm, times(2)).complete(any(), any());
        }

        @Test
        @DisplayName("custom batchSize param is respected")
        void customBatchSize() {
            when(llm.isAvailable()).thenReturn(true);
            String batch5 = "[" + "{\"anomalies\":[]},"
                    .repeat(5).replaceAll(",$", "") + "]";
            when(llm.complete(any(), any())).thenReturn(batch5);

            List<Map<String, Object>> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) records.add(Map.of("i", i));

            step.apply(records, Map.of("batchSize", 5));

            verify(llm, times(2)).complete(any(), any());
        }
    }
}
