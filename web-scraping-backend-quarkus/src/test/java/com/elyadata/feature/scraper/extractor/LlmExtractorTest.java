package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmExtractor")
class LlmExtractorTest {

    private LlmExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new LlmExtractor();
    }

    private static InternalScrapeRequest anyRequest() {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .build();
    }

    @Test
    @DisplayName("mode() returns LLM")
    void mode_returnsLlm() {
        assertThat(extractor.mode()).isEqualTo(ExtractionMode.LLM);
    }

    @Test
    @DisplayName("extract() returns rawContent from text when text is available")
    void extract_usesTextWhenAvailable() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text("  Product: Widget $9.99  ")
                .html("<html><body>Product: Widget $9.99</body></html>")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isEqualTo("Product: Widget $9.99");
        assertThat(output.records()).isEmpty();
    }

    @Test
    @DisplayName("extract() falls back to html when text is null")
    void extract_fallsBackToHtmlWhenTextIsNull() {
        String html = "<html><body>content</body></html>";
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text(null)
                .html(html)
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isEqualTo(html);
    }

    @Test
    @DisplayName("extract() falls back to html when text is blank")
    void extract_fallsBackToHtmlWhenTextIsBlank() {
        String html = "<html><body>content</body></html>";
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text("   ")
                .html(html)
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isEqualTo(html);
    }

    @Test
    @DisplayName("extract() returns empty when both text and html are null")
    void extract_returnsEmptyWhenBothNull() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text(null)
                .html(null)
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isNull();
        assertThat(output.records()).isEmpty();
    }

    @Test
    @DisplayName("extract() strips leading/trailing whitespace from text content")
    void extract_stripsTextContent() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text("\n  Product page content  \n")
                .html("<html/>")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.rawContent()).isEqualTo("Product page content");
    }

    @Test
    @DisplayName("extract() never populates records list — delegates to LLM service")
    void extract_recordsAlwaysEmpty() {
        var snapshot = PageSnapshot.builder()
                .url("https://example.com")
                .text("Some product text")
                .html("<html/>")
                .build();

        var output = extractor.extract(snapshot, anyRequest());

        assertThat(output.records()).isEmpty();
    }
}
