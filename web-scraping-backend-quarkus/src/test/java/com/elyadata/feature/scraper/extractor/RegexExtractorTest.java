package com.elyadata.feature.scraper.extractor;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegexExtractor")
class RegexExtractorTest {

    private RegexExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RegexExtractor();
    }

    @Test
    @DisplayName("mode() returns REGEX")
    void mode_returnsRegex() {
        assertThat(extractor.mode()).isEqualTo(ExtractionMode.REGEX);
    }

    private static PageSnapshot snapshotWithText(String text) {
        return PageSnapshot.builder()
                .url("https://example.com")
                .text(text)
                .html("<html><body>" + text + "</body></html>")
                .build();
    }

    private static PageSnapshot snapshotHtmlOnly(String html) {
        return PageSnapshot.builder()
                .url("https://example.com")
                .text(null)
                .html(html)
                .build();
    }

    private static InternalScrapeRequest requestWithPattern(String pattern) {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .regexPattern(pattern)
                .build();
    }

    private static InternalScrapeRequest requestWithPatternAndGroup(String pattern, String group) {
        return InternalScrapeRequest.builder()
                .targetUrl("https://example.com")
                .regexPattern(pattern)
                .regexGroup(group)
                .build();
    }

    // ── guard clauses ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Guard clauses")
    class GuardClauses {

        @Test
        @DisplayName("returns empty when pattern is null")
        void extract_nullPattern_returnsEmpty() {
            var snapshot = snapshotWithText("some content");
            var request = InternalScrapeRequest.builder()
                    .targetUrl("https://example.com")
                    .regexPattern(null)
                    .build();

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when pattern is blank")
        void extract_blankPattern_returnsEmpty() {
            var snapshot = snapshotWithText("some content");
            var request = requestWithPattern("   ");

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when pattern is invalid regex")
        void extract_invalidPattern_returnsEmpty() {
            var snapshot = snapshotWithText("some content");
            var request = requestWithPattern("[invalid(");

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when pattern finds no matches")
        void extract_noMatches_returnsEmpty() {
            var snapshot = snapshotWithText("no prices here");
            var request = requestWithPattern("\\$\\d+\\.\\d{2}");

            assertThat(extractor.extract(snapshot, request).records()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Full match mode")
    class FullMatchMode {

        @Test
        @DisplayName("returns one record per match with key 'match'")
        void extract_fullMatch_returnsMatchRecords() {
            var snapshot = snapshotWithText("Price: $9.99 and $14.99");
            var request = requestWithPattern("\\$\\d+\\.\\d{2}");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(2);
            assertThat(output.records().get(0)).containsEntry("match", "$9.99");
            assertThat(output.records().get(1)).containsEntry("match", "$14.99");
        }

        @Test
        @DisplayName("strips whitespace from full match value")
        void extract_stripsMatchValue() {
            var snapshot = snapshotWithText("  Alice  ");
            var request = requestWithPattern("\\s*Alice\\s*");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records().getFirst().get("match")).isEqualTo("Alice");
        }
    }

    @Nested
    @DisplayName("Named groups mode")
    class NamedGroupsMode {

        @Test
        @DisplayName("extracts named groups as separate fields")
        void extract_namedGroups_createsFieldsPerGroup() {
            var snapshot = snapshotWithText("Name: Alice Price: $9.99");
            var request = requestWithPattern(
                    "Name: (?<name>[A-Za-z]+) Price: (?<price>\\$[\\d.]+)");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst())
                    .containsEntry("name", "Alice")
                    .containsEntry("price", "$9.99");
        }

        @Test
        @DisplayName("extracts multiple records from repeated named group pattern")
        void extract_namedGroups_multipleMatches() {
            var content = "item=Widget price=9.99\nitem=Gadget price=14.99";
            var snapshot = snapshotWithText(content);
            var request = requestWithPattern(
                    "item=(?<item>[A-Za-z]+) price=(?<price>[\\d.]+)");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(2);
            assertThat(output.records().get(0))
                    .containsEntry("item", "Widget").containsEntry("price", "9.99");
            assertThat(output.records().get(1))
                    .containsEntry("item", "Gadget").containsEntry("price", "14.99");
        }
    }

    @Nested
    @DisplayName("Explicit group mode")
    class ExplicitGroupMode {

        @Test
        @DisplayName("extracts value of named group specified in regexGroup param")
        void extract_explicitGroup_returnsGroupValue() {
            var snapshot = snapshotWithText("Price: $9.99 today");
            var request = requestWithPatternAndGroup(
                    "Price: (?<price>\\$[\\d.]+)", "price");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst()).containsEntry("price", "$9.99");
        }

        @Test
        @DisplayName("falls back to full match when explicit group not in pattern")
        void extract_explicitGroupNotInPattern_fallsBackToFullMatch() {
            var snapshot = snapshotWithText("hello world");
            var request = requestWithPatternAndGroup("hello\\s+\\w+", "nonexistent");

            var output = extractor.extract(snapshot, request);
            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst()).containsKey("match");
        }
    }

    @Nested
    @DisplayName("Content source selection")
    class ContentSourceSelection {

        @Test
        @DisplayName("uses text content when available, ignores html")
        void extract_prefersText() {
            var snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .text("Alice")
                    .html("<html>Bob</html>")
                    .build();
            var request = requestWithPattern("[A-Z][a-z]+");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records().getFirst().get("match")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("falls back to html when text is null")
        void extract_fallsBackToHtml_whenTextIsNull() {
            var snapshot = snapshotHtmlOnly("<html><body>Widget $9.99</body></html>");
            var request = requestWithPattern("\\$[\\d.]+");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(1);
            assertThat(output.records().getFirst().get("match")).isEqualTo("$9.99");
        }

        @Test
        @DisplayName("falls back to html when text is blank")
        void extract_fallsBackToHtml_whenTextIsBlank() {
            var snapshot = PageSnapshot.builder()
                    .url("https://example.com")
                    .text("  ")
                    .html("<html>Price $5.00</html>")
                    .build();
            var request = requestWithPattern("\\$[\\d.]+");

            var output = extractor.extract(snapshot, request);

            assertThat(output.records()).hasSize(1);
        }
    }
}
