package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaginationHtmlSampler")
class PaginationHtmlSamplerTest {

    private static final int MAX_SAMPLE_CHARS = 32_000;

    private PaginationHtmlSampler sampler;

    @BeforeEach
    void setUp() {
        sampler = new PaginationHtmlSampler();
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }

    @Nested
    @DisplayName("empty / null input")
    class EmptyInput {

        @Test
        @DisplayName("returns empty string for null snapshot")
        void nullSnapshot() {
            assertThat(sampler.sample(null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when html is null")
        void nullHtml() {
            assertThat(sampler.sample(snap(null))).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when html is blank")
        void blankHtml() {
            assertThat(sampler.sample(snap(""))).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when no pagination-relevant region exists")
        void noRelevantRegion() {
            String html = "<html><body><article>just prose, no nav, no pagination</article></body></html>";
            assertThat(sampler.sample(snap(html))).isEmpty();
        }
    }

    @Nested
    @DisplayName("signal retention")
    class SignalRetention {

        @Test
        @DisplayName("keeps a .pagination container")
        void keepsPaginationContainer() {
            String html = """
                    <html><body>
                      <div class="pagination">
                        <a href="?page=1">1</a><a href="?page=2">2</a>
                      </div>
                    </body></html>
                    """;
            String out = sampler.sample(snap(html));
            assertThat(out).contains("pagination");
            assertThat(out).contains("page=2");
        }

        @Test
        @DisplayName("keeps a rel=next link from the head")
        void keepsRelNextFromHead() {
            String html = """
                    <html><head>
                      <link rel="next" href="https://example.com?page=2"/>
                    </head><body><p>content</p></body></html>
                    """;
            String out = sampler.sample(snap(html));
            assertThat(out).contains("rel=\"next\"").contains("page=2");
        }

        @Test
        @DisplayName("keeps load-more / infinite-scroll controls")
        void keepsLoadMoreAndInfinite() {
            String html = """
                    <html><body>
                      <button class="load-more-btn">Load more</button>
                      <div class="infinite-scroll-trigger"></div>
                    </body></html>
                    """;
            String out = sampler.sample(snap(html));
            assertThat(out).contains("load-more-btn");
            assertThat(out).contains("infinite-scroll-trigger");
        }

        @Test
        @DisplayName("keeps anchors that carry pagination query params anywhere in the page")
        void keepsParamAnchors() {
            String html = "<html><body><main>"
                    + "<a href='/results?offset=20'>next slice</a>"
                    + "</main></body></html>";
            String out = sampler.sample(snap(html));
            assertThat(out).contains("offset=20");
        }
    }

    @Nested
    @DisplayName("content exclusion")
    class ContentExclusion {

        @Test
        @DisplayName("drops main product/article content while keeping the pagination chrome")
        void dropsMainContent() {
            String marker = "UNIQUE_PRODUCT_BODY_MARKER_" + "z".repeat(2_000);
            String html = """
                    <html><body>
                      <main><div class="product-grid">%s</div></main>
                      <nav class="pagination"><a href="?page=2">2</a></nav>
                    </body></html>
                    """.formatted(marker);

            String out = sampler.sample(snap(html));
            assertThat(out).contains("page=2");
            assertThat(out).doesNotContain(marker);
        }
    }

    @Nested
    @DisplayName("embedded state scripts")
    class StateScripts {

        @Test
        @DisplayName("extracts __NEXT_DATA__ state script")
        void extractsNextData() {
            String html = """
                    <html><body>
                      <script id="__NEXT_DATA__" type="application/json">
                        {"props":{"pageProps":{"totalPages":12}}}
                      </script>
                    </body></html>
                    """;
            String out = sampler.sample(snap(html));
            assertThat(out).contains("totalPages");
        }

        @Test
        @DisplayName("truncates an oversized state script")
        void truncatesOversizedScript() {
            String huge = "\"x\":\"" + "y".repeat(10_000) + "\"";
            String html = "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">{"
                    + huge + "}</script></body></html>";

            String out = sampler.sample(snap(html));
            assertThat(out).contains("truncated");
        }
    }

    @Nested
    @DisplayName("hard caps")
    class HardCaps {

        @Test
        @DisplayName("never exceeds the global char cap")
        void respectsGlobalCap() {
            StringBuilder sb = new StringBuilder("<html><body>");
            for (int i = 0; i < 5_000; i++) {
                sb.append("<a href='?page=").append(i).append("'>").append(i).append("</a>");
            }
            sb.append("</body></html>");

            String out = sampler.sample(snap(sb.toString()));
            assertThat(out.length()).isLessThanOrEqualTo(MAX_SAMPLE_CHARS + 1);
        }

        @Test
        @DisplayName("reduces a large page well below its original size")
        void reducesLargePage() {
            String bulk = "<main>" + "padding ".repeat(20_000) + "</main>";
            String html = "<html><body>" + bulk
                    + "<nav class='pagination'><a href='?page=2'>2</a></nav></body></html>";

            String out = sampler.sample(snap(html));
            assertThat(out.length()).isLessThan(html.length());
            assertThat(out).contains("page=2");
        }
    }
}