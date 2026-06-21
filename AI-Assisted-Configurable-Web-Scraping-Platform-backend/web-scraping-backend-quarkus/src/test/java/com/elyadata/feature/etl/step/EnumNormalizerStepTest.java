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

@DisplayName("EnumNormalizerStep")
class EnumNormalizerStepTest {

    private EnumNormalizerStep step;

    @BeforeEach
    void setUp() {
        step = new EnumNormalizerStep();
    }

    @Test
    @DisplayName("type() returns NORMALIZE_ENUM")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.NORMALIZE_ENUM);
    }

    @ParameterizedTest(name = "'{0}' → '{1}'")
    @CsvSource({
        "'ACTIVE',          'active'",
        "'In Progress',     'in_progress'",
        "'in-progress',     'in_progress'",
        "'  PENDING  ',     'pending'",
        "'multiple  spaces','multiple_spaces'",
        "'A--B',            'a_b'",
        "'already_ok',      'already_ok'"
    })
    @DisplayName("normalizes various enum-like strings to snake_case")
    void apply_normalizesToSnakeCase(String input, String expected) {
        var records = List.of(Map.<String, Object>of("status", input));
        var params = Map.<String, Object>of("fields", List.of("status"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("status")).isEqualTo(expected);
    }

    @Test
    @DisplayName("auto-detects enum fields by name pattern (status, type, category...)")
    void apply_autoDetectsEnumFields() {
        var records = List.of(Map.<String, Object>of(
                "job_status", "IN PROGRESS",
                "name", "Alice"          // should NOT be touched
        ));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("job_status")).isEqualTo("in_progress");
        assertThat(result.getFirst().get("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("auto-detects 'type' and 'category' fields")
    void apply_autoDetectsTypeAndCategory() {
        var records = List.of(Map.<String, Object>of(
                "item_type", "FULL-TIME",
                "main_category", "E-Commerce"
        ));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("item_type")).isEqualTo("full_time");
        assertThat(result.getFirst().get("main_category")).isEqualTo("e_commerce");
    }

    @Test
    @DisplayName("only normalizes specified fields, leaves others untouched")
    void apply_onlyTargetedFields() {
        var records = List.of(Map.<String, Object>of(
                "status", "ACTIVE",
                "category", "E-Commerce"
        ));
        var params = Map.<String, Object>of("fields", List.of("status"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("status")).isEqualTo("active");
        assertThat(result.getFirst().get("category")).isEqualTo("E-Commerce"); // untouched
    }

    @Test
    @DisplayName("skips blank strings without error")
    void apply_blankString_skipped() {
        var records = List.of(Map.<String, Object>of("status", "   "));
        var params = Map.<String, Object>of("fields", List.of("status"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("status")).isEqualTo("   ");
    }

    @Test
    @DisplayName("leaves non-string values unchanged")
    void apply_nonStringValues_unchanged() {
        var records = List.of(Map.<String, Object>of("status", 42));
        var params = Map.<String, Object>of("fields", List.of("status"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("status")).isEqualTo(42);
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }
}
