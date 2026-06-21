package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeduplicateStep")
class DeduplicateStepTest {

    private DeduplicateStep step;

    @BeforeEach
    void setUp() {
        step = new DeduplicateStep();
    }

    @Test
    @DisplayName("type() returns DEDUPLICATE")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.DEDUPLICATE);
    }

    @Test
    @DisplayName("removes exact duplicate records when no keys specified")
    void apply_removesExactDuplicates_noKeys() {
        Map<String, Object> rec = Map.of("id", "1", "name", "Alice");
        List<Map<String, Object>> records = new ArrayList<>(List.of(rec, rec, Map.of("id", "2", "name", "Bob")));

        var result = step.apply(records, Map.of());

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("deduplicates by specified key field")
    void apply_deduplicatesByKey() {
        var records = List.of(
                Map.<String, Object>of("id", "1", "name", "Alice"),
                Map.<String, Object>of("id", "1", "name", "Alice Duplicate"),
                Map.<String, Object>of("id", "2", "name", "Bob")
        );
        var params = Map.<String, Object>of("keys", List.of("id"));

        var result = step.apply(records, params);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("name", "Alice"); // first occurrence kept
        assertThat(result.get(1)).containsEntry("name", "Bob");
    }

    @Test
    @DisplayName("deduplicates by composite key")
    void apply_deduplicatesByCompositeKey() {
        var records = List.of(
                Map.<String, Object>of("first", "John", "last", "Doe", "age", 30),
                Map.<String, Object>of("first", "John", "last", "Doe", "age", 31), // same name, different age
                Map.<String, Object>of("first", "Jane", "last", "Doe", "age", 25)
        );
        var params = Map.<String, Object>of("keys", List.of("first", "last"));

        var result = step.apply(records, params);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("preserves order — keeps first occurrence")
    void apply_preservesFirstOccurrence() {
        var records = List.of(
                Map.<String, Object>of("id", "1", "value", "first"),
                Map.<String, Object>of("id", "1", "value", "second")
        );
        var params = Map.<String, Object>of("keys", List.of("id"));

        var result = step.apply(records, params);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).containsEntry("value", "first");
    }

    @Test
    @DisplayName("returns all records when there are no duplicates")
    void apply_noDuplicates_returnsSame() {
        var records = List.of(
                Map.<String, Object>of("id", "1"),
                Map.<String, Object>of("id", "2"),
                Map.<String, Object>of("id", "3")
        );
        var params = Map.<String, Object>of("keys", List.of("id"));

        var result = step.apply(records, params);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("handles missing key field — treats as empty string")
    void apply_missingKeyField_treatedAsEmpty() {
        var records = List.of(
                Map.<String, Object>of("name", "Alice"),   // no "id"
                Map.<String, Object>of("name", "Bob")      // no "id"
        );
        var params = Map.<String, Object>of("keys", List.of("id"));

        // Both have id="" → only one kept
        var result = step.apply(records, params);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }
}
