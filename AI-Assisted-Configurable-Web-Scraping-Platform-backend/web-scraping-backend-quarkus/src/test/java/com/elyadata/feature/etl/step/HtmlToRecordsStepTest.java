package com.elyadata.feature.etl.step;

import com.elyadata.feature.etl.domain.model.EtlStepType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HtmlToRecordsStep")
class HtmlToRecordsStepTest {

    private HtmlToRecordsStep step;

    @BeforeEach
    void setUp() {
        step = new HtmlToRecordsStep();
    }

    // ─── type() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("type() returns PARSE_HTML")
    void type_returnsParseHtml() {
        assertThat(step.type()).isEqualTo(EtlStepType.PARSE_HTML);
    }

    // ─── empty / no-html input ────────────────────────────────────────────────

    @Nested
    @DisplayName("records without HTML")
    class NoHtmlRecords {

        @Test
        @DisplayName("empty records list returns empty list")
        void emptyList_returnsEmpty() {
            assertThat(step.apply(List.of(), Map.of())).isEmpty();
        }

        @Test
        @DisplayName("record without html field is kept as-is")
        void recordWithoutHtmlField_keptAsIs() {
            var records = List.of(Map.<String, Object>of("url", "https://example.com", "title", "T"));
            var result = step.apply(records, Map.of());
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("url", "https://example.com");
        }

        @Test
        @DisplayName("record with blank html field is kept as-is")
        void recordWithBlankHtml_keptAsIs() {
            var records = List.of(Map.<String, Object>of("html", "   "));
            var result = step.apply(records, Map.of());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("non-String html value is kept as-is")
        void nonStringHtml_keptAsIs() {
            var records = List.of(Map.<String, Object>of("html", 12345));
            var result = step.apply(records, Map.of());
            assertThat(result).hasSize(1);
        }
    }

    // ─── table extraction ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("table extraction")
    class TableExtraction {

        @Test
        @DisplayName("table with thead headers — rows use header names as keys")
        void tableWithHeaders_usesHeaderNamesAsKeys() {
            String html = """
                    <table>
                      <thead><tr><th>Name</th><th>Price</th></tr></thead>
                      <tbody>
                        <tr><td>Apple</td><td>1.20</td></tr>
                        <tr><td>Banana</td><td>0.80</td></tr>
                      </tbody>
                    </table>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("Name", "Apple").containsEntry("Price", "1.20");
            assertThat(result.get(1)).containsEntry("Name", "Banana").containsEntry("Price", "0.80");
        }

        @Test
        @DisplayName("table without headers — uses col_N keys")
        void tableWithoutHeaders_usesColKeys() {
            String html = """
                    <table>
                      <tbody>
                        <tr><td>Row1Col1</td><td>Row1Col2</td></tr>
                      </tbody>
                    </table>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsKey("col_1");
            assertThat(result.getFirst()).containsKey("col_2");
        }

        @Test
        @DisplayName("table with duplicate headers — appended _N to keep uniqueness")
        void tableWithDuplicateHeaders_appendsIndex() {
            String html = """
                    <table>
                      <thead><tr><th>Price</th><th>Price</th></tr></thead>
                      <tbody>
                        <tr><td>10</td><td>20</td></tr>
                      </tbody>
                    </table>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsKey("Price");
            assertThat(result.getFirst()).containsKey("Price_2");
        }

        @Test
        @DisplayName("multiple tables — rows from all tables are merged")
        void multipleTables_allRowsMerged() {
            String html = """
                    <table>
                      <tbody><tr><td>A</td></tr></tbody>
                    </table>
                    <table>
                      <tbody><tr><td>B</td></tr></tbody>
                    </table>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("script and style tags are stripped before parsing")
        void scriptAndStyleRemoved() {
            String html = "<script>alert('x')</script><table><tbody><tr><td>Clean</td></tr></tbody></table>";
            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst().values()).noneMatch(v -> v.toString().contains("alert"));
        }
    }

    // ─── repeated block extraction ────────────────────────────────────────────

    @Nested
    @DisplayName("repeated block extraction (no table)")
    class RepeatedBlocks {

        @Test
        @DisplayName("3+ identical child tags — extracted as repeated blocks")
        void repeatedBlocks_extracted() {
            String html = """
                    <ul>
                      <li class="item">Item One</li>
                      <li class="item">Item Two</li>
                      <li class="item">Item Three</li>
                    </ul>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(r -> r.containsKey("text"));
        }

        @Test
        @DisplayName("links inside blocks are captured in 'link' field")
        void linksInsideBlocksCaptured() {
            String html = """
                    <ul>
                      <li class="prod"><a href="http://a.com">A</a></li>
                      <li class="prod"><a href="http://b.com">B</a></li>
                      <li class="prod"><a href="http://c.com">C</a></li>
                    </ul>""";

            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).allMatch(r -> r.containsKey("link"));
        }
    }

    // ─── fallback text extraction ─────────────────────────────────────────────

    @Nested
    @DisplayName("fallback text extraction")
    class FallbackText {

        @Test
        @DisplayName("simple paragraph page — returns title and full text")
        void simplePage_returnsTitleAndText() {
            String html = "<html><head><title>My Page</title></head><body><p>Hello world</p></body></html>";
            var records = List.of(Map.<String, Object>of("html", html));
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("title", "My Page");
            assertThat(result.getFirst().get("text").toString()).contains("Hello world");
        }
    }

    // ─── custom htmlField param ───────────────────────────────────────────────

    @Nested
    @DisplayName("custom htmlField parameter")
    class CustomHtmlField {

        @Test
        @DisplayName("uses custom field name when 'htmlField' param is set")
        void customField_usedForHtmlExtraction() {
            String html = "<table><tbody><tr><td>Data</td></tr></tbody></table>";
            var records = List.of(Map.<String, Object>of("content", html));
            var result = step.apply(records, Map.of("htmlField", "content"));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsKey("col_1");
        }

        @Test
        @DisplayName("'html' and 'html_length' fields are dropped from inherited fields")
        void htmlAndHtmlLengthDropped() {
            String html = "<table><tbody><tr><td>X</td></tr></tbody></table>";
            var records = List.of(Map.<String, Object>of(
                    "html", html,
                    "html_length", 100,
                    "source", "web"
            ));
            var result = step.apply(records, Map.of());

            assertThat(result.getFirst()).doesNotContainKey("html");
            assertThat(result.getFirst()).doesNotContainKey("html_length");
            assertThat(result.getFirst()).containsKey("source");
        }
    }

    // ─── multiple input records ───────────────────────────────────────────────

    @Nested
    @DisplayName("multiple input records")
    class MultipleRecords {

        @Test
        @DisplayName("each record is processed independently")
        void eachRecordProcessedIndependently() {
            String html1 = "<table><tbody><tr><td>R1</td></tr></tbody></table>";
            String html2 = "<table><tbody><tr><td>R2</td></tr></tbody></table>";
            var records = List.of(
                    Map.<String, Object>of("html", html1),
                    Map.<String, Object>of("html", html2)
            );
            var result = step.apply(records, Map.of());

            assertThat(result).hasSize(2);
        }
    }
}