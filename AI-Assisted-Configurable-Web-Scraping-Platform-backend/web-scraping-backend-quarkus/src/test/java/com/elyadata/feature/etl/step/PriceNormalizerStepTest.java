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

@DisplayName("PriceNormalizerStep")
class PriceNormalizerStepTest {

    private PriceNormalizerStep step;

    @BeforeEach
    void setUp() {
        step = new PriceNormalizerStep();
    }

    @Test
    @DisplayName("type() returns NORMALIZE_PRICE")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.NORMALIZE_PRICE);
    }

    @ParameterizedTest(name = "'{0}' → '{1}'")
    @CsvSource({
        "'19.99',       '19.99'",
        "'19,99',       '19.99'",
        "'1,234.56',    '1234.56'",
        "'1.234,56',    '1234.56'",
        "'$99.00',      '99.00'",
        "'€1.234,56',   '1234.56'",
        "'100',         '100.00'"
    })
    @DisplayName("normalizes various price formats to standard decimal")
    void apply_normalizesPriceFormats(String input, String expected) {
        var records = List.of(Map.<String, Object>of("price", input));
        var params = Map.<String, Object>of("fields", List.of("price"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("price")).isEqualTo(expected);
    }

    @Test
    @DisplayName("leaves unparseable price string unchanged")
    void apply_invalidPrice_leavesUnchanged() {
        var records = List.of(Map.<String, Object>of("price", "free"));
        var params = Map.<String, Object>of("fields", List.of("price"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("price")).isEqualTo("free");
    }

    @Test
    @DisplayName("auto-detects price fields by name pattern")
    void apply_autoDetectsPriceFields() {
        var records = List.of(Map.<String, Object>of(
                "unit_price", "1,234.56",
                "name", "Widget"
        ));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("unit_price")).isEqualTo("1234.56");
        assertThat(result.getFirst().get("name")).isEqualTo("Widget");
    }

    @Test
    @DisplayName("auto-detects 'amount' field")
    void apply_autoDetectsAmountField() {
        var records = List.of(Map.<String, Object>of("total_amount", "99,00"));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("total_amount")).isEqualTo("99.00");
    }

    @Test
    @DisplayName("only normalizes specified fields, leaves others untouched")
    void apply_onlyTargetedFields() {
        var records = List.of(Map.<String, Object>of(
                "price", "1,234.56",
                "cost", "9,99"
        ));
        var params = Map.<String, Object>of("fields", List.of("price"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("price")).isEqualTo("1234.56");
        assertThat(result.getFirst().get("cost")).isEqualTo("9,99"); // untouched
    }

    @Test
    @DisplayName("skips blank price string without error")
    void apply_blankString_skipped() {
        var records = List.of(Map.<String, Object>of("price", "  "));
        var params = Map.<String, Object>of("fields", List.of("price"));

        var result = step.apply(records, params);

        assertThat(result.getFirst().get("price")).isEqualTo("  ");
    }

    @Test
    @DisplayName("handles empty record list")
    void apply_emptyList() {
        assertThat(step.apply(List.of(), Map.of())).isEmpty();
    }
}
