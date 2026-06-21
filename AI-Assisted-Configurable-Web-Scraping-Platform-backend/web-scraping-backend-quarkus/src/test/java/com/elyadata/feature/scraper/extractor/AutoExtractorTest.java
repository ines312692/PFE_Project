package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutoExtractor")
class AutoExtractorTest {

    private AutoExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AutoExtractor();
    }

    private static InternalScrapeRequest anyRequest() {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .build();
    }

    @Test
    @DisplayName("mode() returns AUTO")
    void mode_returnsAuto() {
        assertThat(extractor.mode()).isEqualTo(ExtractionMode.AUTO);
    }

    @Test
    @DisplayName("extract() returns one record with url and html_length")
    void extract_returnsMetaRecord() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .html("<html><body>Hello</body></html>")
                .text("Hello")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.records()).hasSize(1);
        var record = output.records().getFirst();
        assertThat(record).containsEntry("url", "https://example.com");
        assertThat(record.get("html_length")).isEqualTo(31);
    }

    @Test
    @DisplayName("extract() returns empty output when html is null")
    void extract_nullHtml_returnsEmpty() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .html(null)
                .text("some text")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.records()).isEmpty();
    }

    @Test
    @DisplayName("extract() produces record with html_length when text is null")
    void extract_nullText_producesRecord() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .html("<html/>")
                .text(null)
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.records().getFirst().get("html_length")).isEqualTo(7);
    }

    @Test
    @DisplayName("extract() preserves url from snapshot")
    void extract_preservesUrl() {
        var snapshot = PageSnapshot.builder()
                .url("https://shop.example.com/products?page=2")
                .html("x")
                .text("x")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.records().getFirst().get("url"))
                .isEqualTo("https://shop.example.com/products?page=2");
    }

    @Test
    @DisplayName("extract() rawContent is null — not an LLM extractor")
    void extract_rawContentIsNull() {
        var snapshot = PageSnapshot.builder().url("u").html("h").text("t").build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isNull();
    }
}