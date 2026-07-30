package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.scraper.model.FieldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExtractionUtils")
class ExtractionUtilsTest {

    private static FieldConfig field(String name) {
        return FieldConfig.builder().name(name).selector("." + name).build();
    }

    @Test
    @DisplayName("buildRecords creates one record per row index up to maxCount")
    void buildRecords_basicCase() {
        var fields = List.of(field("name"), field("price"));
        var fieldValues = Map.of(
                "name", List.of("Alice", "Bob"),
                "price", List.of("10.00", "20.00")
        );

        var records = ExtractionUtils.buildRecords(fields, fieldValues, 2);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsEntry("name", "Alice").containsEntry("price", "10.00");
        assertThat(records.get(1)).containsEntry("name", "Bob").containsEntry("price", "20.00");
    }

    @Test
    @DisplayName("buildRecords fills null when a field has fewer values than maxCount")
    void buildRecords_missingValuesFilledWithNull() {
        var fields = List.of(field("name"), field("desc"));
        var fieldValues = Map.of(
                "name", List.of("Alice", "Bob"),
                "desc", List.of("Senior dev")   // only 1 value
        );

        var records = ExtractionUtils.buildRecords(fields, fieldValues, 2);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsEntry("desc", "Senior dev");
        assertThat(records.get(1)).containsEntry("desc", null);
    }

    @Test
    @DisplayName("buildRecords skips rows where all values are null or blank")
    void buildRecords_skipsAllBlankRows() {
        var fields = List.of(field("name"), field("price"));
        var fieldValues = Map.<String, List<String>>of(
                "name", Arrays.asList("Alice", "  ", ""),
                "price", Arrays.asList("10.00", "", null)
        );
        var records = ExtractionUtils.buildRecords(fields, fieldValues, 3);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst()).containsEntry("name", "Alice");
    }


    @Test
    @DisplayName("buildRecords returns empty list when maxCount is 0")
    void buildRecords_maxCountZero_returnsEmpty() {
        var fields = List.of(field("name"));
        var fieldValues = Map.<String, List<String>>of("name", List.of());

        assertThat(ExtractionUtils.buildRecords(fields, fieldValues, 0)).isEmpty();
    }

    @Test
    @DisplayName("buildRecords handles field not present in fieldValues map")
    void buildRecords_missingFieldKey_fillsNull() {
        var fields = List.of(field("name"), field("missing_field"));
        var fieldValues = Map.of("name", List.of("Alice"));

        var records = ExtractionUtils.buildRecords(fields, fieldValues, 1);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst()).containsEntry("name", "Alice");
        assertThat(records.getFirst()).containsEntry("missing_field", null);
    }

    @Test
    @DisplayName("buildRecords preserves field insertion order (LinkedHashMap)")
    void buildRecords_preservesFieldOrder() {
        var fields = List.of(field("z_field"), field("a_field"), field("m_field"));
        var fieldValues = Map.of(
                "z_field", List.of("z"),
                "a_field", List.of("a"),
                "m_field", List.of("m")
        );

        var records = ExtractionUtils.buildRecords(fields, fieldValues, 1);

        assertThat(records.getFirst().keySet())
                .containsExactly("z_field", "a_field", "m_field");
    }

    @Test
    @DisplayName("buildRecords with single field and multiple values")
    void buildRecords_singleField_multipleValues() {
        var fields = List.of(field("price"));
        var fieldValues = Map.of("price", List.of("10.00", "20.00", "30.00"));

        var records = ExtractionUtils.buildRecords(fields, fieldValues, 3);

        assertThat(records).hasSize(3);
        assertThat(records).extracting(r -> r.get("price"))
                .containsExactly("10.00", "20.00", "30.00");
    }
}