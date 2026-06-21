package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateNormalizerStep")
class DateNormalizerStepTest {

    private DateNormalizerStep step;

    @BeforeEach
    void setUp() {
        step = new DateNormalizerStep();
    }

    @Test
    @DisplayName("type() returns NORMALIZE_DATE")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.NORMALIZE_DATE);
    }

    @ParameterizedTest(name = "parses '{0}' → '2024-03-15'")
    @CsvSource({
        "2024-03-15",
        "15/03/2024",
        "03/15/2024",
        "15 Mar 2024",
        "Mar 15 2024",
        "2024/03/15",
        "15-03-2024",
        "03-15-2024",
        "2024.03.15",
        "15.03.2024"
    })
    @DisplayName("normalizes various date formats to ISO 8601")
    void apply_normalizesFormats(String input) {
        var records = List.of(Map.<String, Object>of("date", input));
        var params = Map.<String, Object>of("fields", List.of("date"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("date")).isEqualTo("2024-03-15");
    }

    @Test
    @DisplayName("leaves unparseable date string unchanged")
    void apply_invalidDate_leavesUnchanged() {
        var records = List.of(Map.<String, Object>of("date", "not-a-date"));
        var params = Map.<String, Object>of("fields", List.of("date"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("date")).isEqualTo("not-a-date");
    }

    @Test
    @DisplayName("leaves blank date field unchanged")
    void apply_blankDate_leavesUnchanged() {
        var records = List.of(Map.<String, Object>of("date", "  "));
        var params = Map.<String, Object>of("fields", List.of("date"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("date")).isEqualTo("  ");
    }

    @Test
    @DisplayName("auto-detects date fields by name pattern when no fields param")
    void apply_autoDetectsDateFields() {
        var records = List.of(Map.<String, Object>of(
                "created_at", "15/03/2024",
                "name", "Alice"          // should NOT be touched
        ));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("created_at")).isEqualTo("2024-03-15");
        assertThat(result.getFirst().get("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("does not auto-detect non-date field names")
    void apply_autoDetect_ignoresNonDateFields() {
        var records = List.of(Map.<String, Object>of(
                "title", "15/03/2024"
        ));

        var result = step.apply(records, Map.of());

        // "title" doesn't match the date pattern → unchanged
        assertThat(result.getFirst().get("title")).isEqualTo("15/03/2024");
    }

    @Test
    @DisplayName("only normalizes specified fields, leaves others untouched")
    void apply_onlyTargetedFields() {
        var records = List.of(Map.<String, Object>of(
                "start_date", "15/03/2024",
                "end_date", "16/03/2024"
        ));
        var params = Map.<String, Object>of("fields", List.of("start_date"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("start_date")).isEqualTo("2024-03-15");
        assertThat(result.getFirst().get("end_date")).isEqualTo("16/03/2024");
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }
}
