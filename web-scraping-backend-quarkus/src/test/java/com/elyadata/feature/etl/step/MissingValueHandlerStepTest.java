package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MissingValueHandlerStep")
class MissingValueHandlerStepTest {

    private MissingValueHandlerStep step;

    @BeforeEach
    void setUp() {
        step = new MissingValueHandlerStep();
    }

    @Test
    @DisplayName("type() returns MISSING_VALUE_HANDLER")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.MISSING_VALUE_HANDLER);
    }

    @Nested
    @DisplayName("fill strategy (default)")
    class FillStrategy {

        @Test
        @DisplayName("replaces null values with fallback 'N/A'")
        void apply_replacesNullWithDefault() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", null);
            record.put("city", "Paris");

            var result = step.apply(List.of(record), Map.of("strategy", "fill"));

            assertThat(result.getFirst())
                    .containsEntry("name", "N/A")
                    .containsEntry("city", "Paris");
        }

        @Test
        @DisplayName("replaces blank strings with fallback")
        void apply_replacesBlankWithFallback() {
            var records = List.of(Map.<String, Object>of("name", "  ", "city", "Tunis"));

            var result = step.apply(records, Map.of("strategy", "fill"));

            assertThat(result.getFirst()).containsEntry("name", "N/A");
        }

        @Test
        @DisplayName("uses custom fallback value from params")
        void apply_customFallback() {
            Map<String, Object> record = new HashMap<>();
            record.put("name", null);

            var result = step.apply(List.of(record),
                    Map.of("strategy", "fill", "fallback", "UNKNOWN"));

            assertThat(result.getFirst()).containsEntry("name", "UNKNOWN");
        }

        @Test
        @DisplayName("does not modify non-null, non-blank values")
        void apply_leavesValidValuesUnchanged() {
            var records = List.of(Map.<String, Object>of("name", "Alice", "score", 42));

            var result = step.apply(records, Map.of("strategy", "fill"));

            assertThat(result.getFirst())
                    .containsEntry("name", "Alice")
                    .containsEntry("score", 42);
        }

        @Test
        @DisplayName("defaults to fill strategy when strategy param is absent")
        void apply_defaultsFillWhenNoStrategy() {
            Map<String, Object> record = new HashMap<>();
            record.put("value", null);

            var result = step.apply(List.of(record), Map.of());

            assertThat(result.getFirst()).containsEntry("value", "N/A");
        }
    }

    @Nested
    @DisplayName("drop_row strategy")
    class DropRowStrategy {

        @Test
        @DisplayName("drops records containing any null value")
        void apply_dropsRowWithNull() {
            Map<String, Object> incomplete = new HashMap<>();
            incomplete.put("name", null);
            incomplete.put("city", "Paris");

            var complete = Map.<String, Object>of("name", "Bob", "city", "Lyon");
            var params = Map.<String, Object>of("strategy", "drop_row");

            var result = step.apply(List.of(incomplete, complete), params);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("name", "Bob");
        }

        @Test
        @DisplayName("drops records containing blank strings")
        void apply_dropsRowWithBlankString() {
            var incomplete = Map.<String, Object>of("name", "", "city", "Paris");
            var complete = Map.<String, Object>of("name", "Alice", "city", "Lyon");
            var params = Map.<String, Object>of("strategy", "drop_row");

            var result = step.apply(List.of(incomplete, complete), params);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("keeps all records when none have missing values")
        void apply_keepsAllComplete() {
            var records = List.of(
                    Map.<String, Object>of("name", "Alice"),
                    Map.<String, Object>of("name", "Bob")
            );
            var params = Map.<String, Object>of("strategy", "drop_row");

            var result = step.apply(records, params);

            assertThat(result).hasSize(2);
        }
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }
}
