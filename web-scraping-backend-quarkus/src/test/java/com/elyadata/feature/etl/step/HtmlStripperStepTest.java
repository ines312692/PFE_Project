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

@DisplayName("HtmlStripperStep")
class HtmlStripperStepTest {

    private HtmlStripperStep step;

    @BeforeEach
    void setUp() {
        step = new HtmlStripperStep();
    }

    @Test
    @DisplayName("type() returns STRIP_HTML")
    void type_returnsCorrectEnum() {
        assertThat(step.type()).isEqualTo(EtlStepType.STRIP_HTML);
    }

    @ParameterizedTest(name = "strips [{0}] → [{1}]")
    @CsvSource({
            "'<p>Hello</p>',                      'Hello'",
            "'<b>Bold</b> text',                  'Bold text'",
            "'<div class=\"x\">content</div>',    'content'",
            "'No tags here',                      'No tags here'",
            "'<br/><br/>line',                    'line'"
    })
    @DisplayName("removes HTML tags from various inputs")
    void apply_stripsHtmlTags(String input, String expected) {
        var records = List.of(Map.<String, Object>of("text", input));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("text")).isEqualTo(expected);
    }

    @Test
    @DisplayName("collapses multiple spaces left by stripped tags")
    void apply_collapsesMultipleSpaces() {
        var records = List.of(Map.<String, Object>of(
                "text", "<span>a</span>   <span>b</span>"));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("text")).isEqualTo("a b");
    }

    @Test
    @DisplayName("leaves non-string values unchanged")
    void apply_nonStringValuesUnchanged() {
        var records = List.of(Map.<String, Object>of("price", 9.99, "active", true));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst()).containsEntry("price", 9.99)
                .containsEntry("active", true);
    }

    @Test
    @DisplayName("handles empty HTML string")
    void apply_emptyString() {
        var records = List.of(Map.<String, Object>of("text", ""));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("text")).isEqualTo("");
    }

    @Test
    @DisplayName("handles nested tags")
    void apply_nestedTags() {
        var records = List.of(Map.<String, Object>of(
                "text", "<div><p><strong>Nested</strong></p></div>"));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst().get("text")).isEqualTo("Nested");
    }

    @Test
    @DisplayName("processes all string fields in a record")
    void apply_allStringFieldsStripped() {
        var records = List.of(Map.<String, Object>of(
                "title", "<h1>Title</h1>",
                "body", "<p>Body</p>"));

        var result = step.apply(records, Map.of());

        assertThat(result.getFirst())
                .containsEntry("title", "Title")
                .containsEntry("body", "Body");
    }
}
