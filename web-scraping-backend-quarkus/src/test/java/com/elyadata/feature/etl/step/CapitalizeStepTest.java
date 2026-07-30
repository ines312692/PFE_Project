package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CapitalizeStep")
class CapitalizeStepTest {

    private CapitalizeStep step;

    @BeforeEach
    void setUp() {
        step = new CapitalizeStep();
    }

    @Test
    @DisplayName("type() returns CAPITALIZE")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.CAPITALIZE);
    }

    @Nested
    @DisplayName("Title case mode (default)")
    class TitleCaseMode {

        @ParameterizedTest(name = "'{0}' → '{1}'")
        @CsvSource({
            "'hello world',    'Hello World'",
            "'JOHN DOE',       'John Doe'",
            "'alice',          'Alice'",
            "'one two three',  'One Two Three'"
        })
        void apply_titleCase(String input, String expected) {
            var records = List.of(Map.<String, Object>of("name", input));
            var params = Map.<String, Object>of("mode", "title");

            var result = step.apply(records, params);

            assertThat(result.getFirst().get("name")).isEqualTo(expected);
        }

        @Test
        @DisplayName("defaults to title mode when mode param is absent")
        void apply_defaultsToTitle() {
            var records = List.of(Map.<String, Object>of("name", "hello world"));

            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().get("name")).isEqualTo("Hello World");
        }
    }

    @Nested
    @DisplayName("Upper case mode")
    class UpperCaseMode {

        @Test
        void apply_upperCase() {
            var records = List.of(Map.<String, Object>of("name", "hello"));
            var params = Map.<String, Object>of("mode", "upper");

            var result = step.apply(records, params);

            assertThat(result.getFirst().get("name")).isEqualTo("HELLO");
        }
    }

    @Nested
    @DisplayName("Lower case mode")
    class LowerCaseMode {

        @Test
        void apply_lowerCase() {
            var records = List.of(Map.<String, Object>of("name", "HELLO WORLD"));
            var params = Map.<String, Object>of("mode", "lower");

            var result = step.apply(records, params);

            assertThat(result.getFirst().get("name")).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("Field filtering")
    class FieldFiltering {

        @Test
        @DisplayName("only transforms specified fields when 'fields' param is set")
        void apply_onlySpecifiedFields() {
            var records = List.of(Map.<String, Object>of(
                    "name", "alice",
                    "city", "paris"));
            var params = Map.of(
                    "fields", List.of("name"),
                    "mode", "upper");

            var result = step.apply(records, params);

            assertThat(result.getFirst())
                    .containsEntry("name", "ALICE")
                    .containsEntry("city", "paris"); // untouched
        }

        @Test
        @DisplayName("transforms all string fields when 'fields' is empty")
        void apply_allFieldsWhenNoFilter() {
            var records = List.of(Map.<String, Object>of(
                    "a", "hello",
                    "b", "world"));
            var params = Map.<String, Object>of("mode", "upper");

            var result = step.apply(records, params);

            assertThat(result.getFirst())
                    .containsEntry("a", "HELLO")
                    .containsEntry("b", "WORLD");
        }
    }

    @Test
    @DisplayName("leaves non-string values unchanged")
    void apply_nonStringValuesUnchanged() {
        var records = List.of(Map.<String, Object>of("score", 100, "active", true));

        var result = step.apply(records, Map.of("mode", "upper"));

        assertThat(result.getFirst())
                .containsEntry("score", 100)
                .containsEntry("active", true);
    }

    @Test
    @DisplayName("skips blank strings")
    void apply_blankStringsSkipped() {
        var records = List.of(Map.<String, Object>of("name", "   "));

        var result = step.apply(records, Map.of("mode", "upper"));

        assertThat(result.getFirst().get("name")).isEqualTo("   ");
    }
}
