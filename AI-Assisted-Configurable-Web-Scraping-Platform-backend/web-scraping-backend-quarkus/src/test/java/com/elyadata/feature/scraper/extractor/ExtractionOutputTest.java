package com.elyadata.feature.scraper.extractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExtractionOutput")
class ExtractionOutputTest {

    @Test
    @DisplayName("of() creates output with records and null rawContent")
    void of_createsWithRecordsAndNullRawContent() {
        var records = List.of(Map.<String, Object>of("name", "Alice"));

        var output = ExtractionOutput.of(records);

        assertThat(output.records()).isEqualTo(records);
        assertThat(output.rawContent()).isNull();
    }

    @Test
    @DisplayName("raw() creates output with empty records and non-null rawContent")
    void raw_createsWithEmptyRecordsAndContent() {
        var output = ExtractionOutput.raw("some html content");

        assertThat(output.records()).isEmpty();
        assertThat(output.rawContent()).isEqualTo("some html content");
    }

    @Test
    @DisplayName("empty() creates output with empty records and null rawContent")
    void empty_createsWithEmptyRecordsAndNullContent() {
        var output = ExtractionOutput.empty();

        assertThat(output.records()).isEmpty();
        assertThat(output.rawContent()).isNull();
    }

    @Test
    @DisplayName("of() preserves record content exactly")
    void of_preservesRecordContent() {
        var records = List.of(
                Map.<String, Object>of("url", "https://example.com", "price", "9.99"),
                Map.<String, Object>of("url", "https://example.com/2", "price", "19.99")
        );

        var output = ExtractionOutput.of(records);

        assertThat(output.records()).hasSize(2);
        assertThat(output.records().get(0)).containsEntry("url", "https://example.com");
        assertThat(output.records().get(1)).containsEntry("price", "19.99");
    }

    @Test
    @DisplayName("raw() preserves content string exactly including whitespace")
    void raw_preservesContentString() {
        String html = "<html>\n  <body>test</body>\n</html>";

        var output = ExtractionOutput.raw(html);

        assertThat(output.rawContent()).isEqualTo(html);
    }

    @Test
    @DisplayName("of() with empty list produces empty records")
    void of_emptyList_producesEmptyRecords() {
        var output = ExtractionOutput.of(List.of());

        assertThat(output.records()).isEmpty();
        assertThat(output.rawContent()).isNull();
    }
}
