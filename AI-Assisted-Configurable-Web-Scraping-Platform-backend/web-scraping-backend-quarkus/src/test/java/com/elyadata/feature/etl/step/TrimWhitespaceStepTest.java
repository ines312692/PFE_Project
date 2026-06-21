package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrimWhitespaceStep")
class TrimWhitespaceStepTest {

    private TrimWhitespaceStep step;

    @BeforeEach
    void setUp() {
        step = new TrimWhitespaceStep();
    }

    @Test
    @DisplayName("type() returns TRIM_WHITESPACE")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.TRIM_WHITESPACE);
    }

    @Test
    @DisplayName("trims leading and trailing whitespace from string values")
    void apply_trimsStringValues() {
        var records = List.of(Map.<String, Object>of("name", "  Alice  ", "city", "\tParis\n"));

        var result = step.apply(records, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).containsEntry("name", "Alice")
                                     .containsEntry("city", "Paris");
    }

    @Test
    @DisplayName("leaves non-string values unchanged")
    void apply_nonStringValuesUntouched() {
        var records = List.of(Map.<String, Object>of("price", 42.0, "count", 3));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst()).containsEntry("price", 42.0)
                                     .containsEntry("count", 3);
    }

    @Test
    @DisplayName("handles empty string without error")
    void apply_emptyStringUnchanged() {
        var records = List.of(Map.<String, Object>of("name", ""));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst()).containsEntry("name", "");
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList_returnsEmpty() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("processes multiple records independently")
    void apply_multipleRecords() {
        var records = List.of(
                Map.<String, Object>of("v", "  a  "),
                Map.<String, Object>of("v", "  b  ")
        );

        var result = step.apply(records, Map.of());

        assertThat(result).extracting(r -> r.get("v"))
                          .containsExactly("a", "b");
    }
}
