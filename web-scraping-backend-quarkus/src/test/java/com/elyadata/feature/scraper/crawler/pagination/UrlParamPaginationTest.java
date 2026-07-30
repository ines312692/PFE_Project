package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("UrlParamPagination")
class UrlParamPaginationTest {

    private UrlParamPagination strategy;
    private ABrowser browser;

    @BeforeEach
    void setUp() {
        strategy = new UrlParamPagination();
        browser = mock(ABrowser.class);
    }

    @Test
    @DisplayName("type() returns URL_PARAM")
    void type() {
        assertThat(strategy.type()).isEqualTo(PaginationType.URL_PARAM);
    }
    @Nested
    @DisplayName("buildUrlWithParam()")
    class BuildUrlWithParam {

        @Test
        @DisplayName("appends param when no query string exists")
        void appendsToCleanUrl() {
            String result = UrlParamPagination.buildUrlWithParam("https://example.com/list", "page", 2);
            assertThat(result).isEqualTo("https://example.com/list?page=2");
        }

        @Test
        @DisplayName("appends param when other params are already present")
        void appendsToExistingParams() {
            String result = UrlParamPagination.buildUrlWithParam(
                    "https://example.com/list?q=shoes", "page", 3);
            assertThat(result).isEqualTo("https://example.com/list?q=shoes&page=3");
        }

        @Test
        @DisplayName("replaces existing param value")
        void replacesExistingParam() {
            String result = UrlParamPagination.buildUrlWithParam(
                    "https://example.com/list?page=1&sort=asc", "page", 5);
            assertThat(result).contains("page=5").doesNotContain("page=1");
        }

        @Test
        @DisplayName("handles param at start of query string")
        void handlesParamAtStart() {
            String result = UrlParamPagination.buildUrlWithParam(
                    "https://example.com/list?page=2&q=test", "page", 3);
            assertThat(result).contains("page=3").doesNotContain("page=2");
        }

        @ParameterizedTest(name = "param={0} value={1} expected contains={2}")
        @CsvSource({
                "p,    2, p=2",
                "pg,   3, pg=3",
                "offset, 10, offset=10"
        })
        @DisplayName("works with various param names")
        void worksWithVariousParamNames(String param, int value, String expected) {
            String result = UrlParamPagination.buildUrlWithParam(
                    "https://example.com/items", param, value);
            assertThat(result).contains(expected);
        }
    }

    @Nested
    @DisplayName("hasNextPage()")
    class HasNextPage {

        @Test
        @DisplayName("returns true when within page limit and not stale")
        void returnsTrueWithinLimit() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            PageSnapshot snap = PageSnapshot.builder().url("https://ex.com?page=1").html("").build();
            assertThat(strategy.hasNextPage(snap, ctx)).isTrue();
        }

        @Test
        @DisplayName("returns false when maxPages reached")
        void returnsFalseAtMaxPages() {
            PaginationContext ctx = PaginationContext.of(null, 1); // maxPages=1, currentPage=1
            PageSnapshot snap = PageSnapshot.builder().url("https://ex.com?page=1").html("").build();
            assertThat(strategy.hasNextPage(snap, ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when context is stale")
        void returnsFalseWhenStale() {
            PaginationContext ctx = PaginationContext.of(null, 10).markStale();
            PageSnapshot snap = PageSnapshot.builder().url("https://ex.com?page=1").html("").build();
            assertThat(strategy.hasNextPage(snap, ctx)).isFalse();
        }
    }

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("detects page param in URL with confidence 0.95")
        void detectsPageInUrl() {
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://shop.com/products?page=2")
                    .html("")
                    .build();
            PaginationStrategy.DetectionResult result = strategy.detect(snap);
            assertThat(result.isFound()).isTrue();
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.90);
            assertThat(result.type()).isEqualTo(PaginationType.URL_PARAM);
        }

        @ParameterizedTest(name = "param={0}")
        @CsvSource({"page", "p", "pg", "offset", "start", "from"})
        @DisplayName("detects all common param names in URL")
        void detectsAllCommonParams(String param) {
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com/list?" + param + "=1")
                    .html("")
                    .build();
            assertThat(strategy.detect(snap).isFound()).isTrue();
        }

        @Test
        @DisplayName("detects param in outgoing href with confidence 0.80")
        void detectsParamInHtml() {
            String html = "<a href='/products?page=2'>Next</a>";
            PageSnapshot snap = PageSnapshot.builder().url("https://example.com/").html(html).build();
            PaginationStrategy.DetectionResult result = strategy.detect(snap);
            assertThat(result.isFound()).isTrue();
            assertThat(result.confidence()).isEqualTo(0.80);
        }

        @Test
        @DisplayName("returns notFound when no param in URL or html")
        void returnsNotFound() {
            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com/products")
                    .html("<div>No pagination</div>")
                    .build();
            assertThat(strategy.detect(snap).isFound()).isFalse();
        }

        @Test
        @DisplayName("returns notFound when URL is null")
        void returnsNotFoundForNullUrl() {
            PageSnapshot snap = PageSnapshot.builder().url(null).html("").build();
            assertThat(strategy.detect(snap).isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("goToNextPage()")
    class GoToNextPage {

        @Test
        @DisplayName("navigates using the param name from context selector")
        void navigatesToNextPage() {
            PageSnapshot currentSnap = PageSnapshot.builder()
                    .url("https://example.com/items?page=1")
                    .html("")
                    .build();
            when(browser.snapshot()).thenReturn(currentSnap);

            PaginationContext ctx = PaginationContext.of("page", 10);
            strategy.goToNextPage(browser, ctx);
            verify(browser).navigate("https://example.com/items?page=2");
        }

        @Test
        @DisplayName("uses custom param name from context selector; nextPage = currentPage + 1")
        void usesCustomParam() {
            PageSnapshot currentSnap = PageSnapshot.builder()
                    .url("https://example.com/items?pg=1")
                    .html("")
                    .build();
            when(browser.snapshot()).thenReturn(currentSnap);

            PaginationContext ctx = PaginationContext.of("pg", 10);
            strategy.goToNextPage(browser, ctx);
            verify(browser).navigate("https://example.com/items?pg=2");
        }
    }
}