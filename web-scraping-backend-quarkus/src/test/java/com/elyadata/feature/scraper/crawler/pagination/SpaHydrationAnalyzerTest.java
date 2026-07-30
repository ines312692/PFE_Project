package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpaHydrationAnalyzer")
class SpaHydrationAnalyzerTest {

    private SpaHydrationAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SpaHydrationAnalyzer();
    }
    @Test
    @DisplayName("returns empty for null input")
    void returnsEmptyForNull() {
        assertThat(analyzer.analyze(null)).isEmpty();
    }

    @Test
    @DisplayName("returns empty for blank HTML")
    void returnsEmptyForBlank() {
        assertThat(analyzer.analyze("")).isEmpty();
    }
    @Nested
    @DisplayName("Next.js (__NEXT_DATA__)")
    class NextData {

        @Test
        @DisplayName("detects URL_PARAM when query contains page")
        void detectsQueryPage() {
            String html = """
                    <html><head></head><body>
                    <script id="__NEXT_DATA__" type="application/json">
                    {"query":{"page":"2"},"props":{"pageProps":{}}}
                    </script>
                    </body></html>
                    """;
            Optional<SpaHydrationAnalyzer.Finding> result = analyzer.analyze(html);
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PaginationType.URL_PARAM);
            assertThat(result.get().confidence()).isGreaterThanOrEqualTo(0.90);
        }

        @Test
        @DisplayName("detects NUMBERED when pageProps contains totalPages")
        void detectsTotalPagesInPageProps() {
            String html = """
                    <html><head></head><body>
                    <script id="__NEXT_DATA__" type="application/json">
                    {"props":{"pageProps":{"totalPages":15,"currentPage":1,"items":[]}},"query":{}}
                    </script>
                    </body></html>
                    """;
            Optional<SpaHydrationAnalyzer.Finding> result = analyzer.analyze(html);
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PaginationType.NUMBERED);
        }

        @Test
        @DisplayName("detects CURSOR when pageInfo.hasNextPage is present")
        void detectsRelayPagination() {
            String html = """
                    <html><head></head><body>
                    <script id="__NEXT_DATA__" type="application/json">
                    {"props":{"pageProps":{"data":{"pageInfo":{"hasNextPage":true,"endCursor":"abc"}}}},"query":{}}
                    </script>
                    </body></html>
                    """;
            Optional<SpaHydrationAnalyzer.Finding> result = analyzer.analyze(html);
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PaginationType.CURSOR);
            assertThat(result.get().confidence()).isGreaterThanOrEqualTo(0.90);
        }

        @Test
        @DisplayName("returns empty when __NEXT_DATA__ has no pagination keys")
        void returnsEmptyWhenNoPaginationKeys() {
            String html = """
                    <html><head></head><body>
                    <script id="__NEXT_DATA__" type="application/json">
                    {"props":{"pageProps":{"title":"Home","items":[]}},"query":{}}
                    </script>
                    </body></html>
                    """;
            assertThat(analyzer.analyze(html)).isEmpty();
        }

        @Test
        @DisplayName("returns empty for invalid JSON in __NEXT_DATA__")
        void returnsEmptyForInvalidJson() {
            String html = "<script id=\"__NEXT_DATA__\" type=\"application/json\">{not valid}</script>";
            assertThat(analyzer.analyze(html)).isEmpty();
        }
    }
    @Nested
    @DisplayName("Window state patterns (Nuxt / Apollo)")
    class WindowState {

        @Test
        @DisplayName("detects NUMBERED pagination in window.__NUXT__")
        void detectsNuxtState() {
            String html = """
                    <script>
                    window.__NUXT__ = {"totalPages":8,"currentPage":2,"items":[]}
                    </script>
                    """;
            Optional<SpaHydrationAnalyzer.Finding> result = analyzer.analyze(html);
            result.ifPresent(f -> assertThat(f.type()).isEqualTo(PaginationType.NUMBERED));
        }
    }
    @Nested
    @DisplayName("Finding record")
    class FindingRecord {

        @Test
        @DisplayName("Finding carries correct fields")
        void findingFields() {
            SpaHydrationAnalyzer.Finding f = new SpaHydrationAnalyzer.Finding(
                    PaginationType.CURSOR, 0.92, "reason", null, "next.js");
            assertThat(f.type()).isEqualTo(PaginationType.CURSOR);
            assertThat(f.confidence()).isEqualTo(0.92);
            assertThat(f.reason()).isEqualTo("reason");
            assertThat(f.framework()).isEqualTo("next.js");
        }
    }
}
