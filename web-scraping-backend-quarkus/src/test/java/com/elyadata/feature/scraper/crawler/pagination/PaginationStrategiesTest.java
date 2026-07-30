package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("InfiniteScrollPagination")
class InfiniteScrollPaginationTest {

    private InfiniteScrollPagination strategy;
    private ABrowser browser;

    @BeforeEach
    void setUp() {
        strategy = new InfiniteScrollPagination();
        browser  = mock(ABrowser.class);
    }

    @Test
    @DisplayName("type() returns INFINITE_SCROLL")
    void type() {
        assertThat(strategy.type()).isEqualTo(PaginationType.INFINITE_SCROLL);
    }

    @Nested
    @DisplayName("hasNextPage()")
    class HasNextPage {

        @Test
        @DisplayName("returns true when within limit and not stale")
        void returnsTrueWithinLimit() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap(""), ctx)).isTrue();
        }

        @Test
        @DisplayName("returns false when max pages reached")
        void returnsFalseAtMaxPages() {
            PaginationContext ctx = PaginationContext.of(null, 1);
            assertThat(strategy.hasNextPage(snap(""), ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when context is stale")
        void returnsFalseWhenStale() {
            PaginationContext ctx = PaginationContext.of(null, 10).markStale();
            assertThat(strategy.hasNextPage(snap(""), ctx)).isFalse();
        }
    }

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("detects infinite-scroll library reference")
        void detectsLibraryRef() {
            String html = "<script src='/assets/ngx-infinite-scroll.js'></script>" +
                    "<div class='content'>items...</div>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("detects data-infinite-scroll DOM markers")
        void detectsDomMarker() {
            String html = "<div data-infinite-scroll='true'>items</div>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("detects IntersectionObserver combined with scroll sentinel")
        void detectsIntersectionObserver() {
            String html = "<div class='infinite-scroll-sentinel'></div>" +
                    "<script>const obs = new IntersectionObserver(entries => loadMore());</script>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("detects embedded hasNextPage=true state")
        void detectsHasNextPage() {
            String html = "<script>var state={\"hasNextPage\":true,\"items\":[]}</script>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("returns notFound for plain HTML page")
        void returnsNotFound() {
            String html = "<div class='pagination'><a href='?page=2'>Next</a></div>";
            assertThat(strategy.detect(snap(html)).isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("refreshContext()")
    class RefreshContext {

        @Test
        @DisplayName("marks context stale when content did not grow enough")
        void marksStaleWhenNoGrowth() {
            PaginationContext ctx = PaginationContext.of(null, 10).withHtmlLength(1_000L);
            PageSnapshot newSnap = snap("x".repeat(1_050)); // delta=50 < MIN_CONTENT_DELTA
            PaginationContext updated = strategy.refreshContext(newSnap, ctx);
            assertThat(updated.isStale()).isTrue();
        }

        @Test
        @DisplayName("does NOT mark stale when content grew significantly")
        void notStaleWhenGrew() {
            PaginationContext ctx = PaginationContext.of(null, 10).withHtmlLength(1_000L);
            PageSnapshot newSnap = snap("x".repeat(1_500)); // delta=500 > MIN_CONTENT_DELTA
            PaginationContext updated = strategy.refreshContext(newSnap, ctx);
            assertThat(updated.isStale()).isFalse();
        }
    }

    @Test
    @DisplayName("goToNextPage() scrolls browser to bottom")
    void goToNextPageScrolls() {
        strategy.goToNextPage(browser, PaginationContext.of(null, 10));
        verify(browser).scroll(true);
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }
}

@DisplayName("LoadMorePagination")
class LoadMorePaginationTest {

    private LoadMorePagination strategy;
    private ABrowser browser;

    @BeforeEach
    void setUp() {
        strategy = new LoadMorePagination();
        browser  = mock(ABrowser.class);
    }

    @Test
    @DisplayName("type() returns LOAD_MORE")
    void type() {
        assertThat(strategy.type()).isEqualTo(PaginationType.LOAD_MORE);
    }

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("detects standard load-more selector with high confidence")
        void detectsStandardSelector() {
            String html = "<button data-action='load-more'>Load more</button>";
            PaginationStrategy.DetectionResult result = strategy.detect(snap(html));
            assertThat(result.isFound()).isTrue();
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.80);
        }

        @Test
        @DisplayName("detects 'Load more' text on a button")
        void detectsLoadMoreText() {
            String html = "<button class='my-btn'>Load more</button>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("detects 'Voir plus' text (French) on a button")
        void detectsFrenchText() {
            String html = "<button>Voir plus</button>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("returns notFound when no load-more button present")
        void returnsNotFound() {
            String html = "<a href='?page=2'>Next</a>";
            assertThat(strategy.detect(snap(html)).isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasNextPage()")
    class HasNextPage {

        @Test
        @DisplayName("returns true when standard button is present and enabled")
        void returnsTrueWhenButtonPresent() {
            String html = "<button data-action='load-more'>Load more</button>";
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isTrue();
        }

        @Test
        @DisplayName("returns false when button is disabled")
        void returnsFalseWhenDisabled() {
            String html = "<button data-action='load-more' disabled>Load more</button>";
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when button is absent")
        void returnsFalseWhenAbsent() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap("<div>no button</div>"), ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when max pages reached")
        void returnsFalseAtMaxPages() {
            String html = "<button data-action='load-more'>More</button>";
            PaginationContext ctx = PaginationContext.of(null, 1);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isFalse();
        }
    }

    @Test
    @DisplayName("goToNextPage() clicks the load-more button")
    void goToNextPageClicks() {
        strategy.goToNextPage(browser, PaginationContext.of(null, 10));
        verify(browser).click(anyString());
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }
}


@DisplayName("NextButtonPagination")
class NextButtonPaginationTest {

    private NextButtonPagination strategy;
    private ABrowser browser;

    @BeforeEach
    void setUp() {
        strategy = new NextButtonPagination();
        browser  = mock(ABrowser.class);
    }

    @Test
    @DisplayName("type() returns NEXT_BUTTON")
    void type() {
        assertThat(strategy.type()).isEqualTo(PaginationType.NEXT_BUTTON);
    }

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("detects rel='next' anchor")
        void detectsRelNext() {
            String html = "<a rel='next' href='/page/2'>Next</a>";
            PaginationStrategy.DetectionResult result = strategy.detect(snap(html));
            assertThat(result.isFound()).isTrue();
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.80);
        }

        @Test
        @DisplayName("detects aria-label='Next' button")
        void detectsAriaLabel() {
            String html = "<button aria-label='Next'>›</button>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("does not detect disabled rel='next' element")
        void ignoresDisabled() {
            String html = "<button aria-label='Next' aria-disabled='true'>›</button>";
            assertThat(strategy.detect(snap(html)).isFound()).isFalse();
        }

        @Test
        @DisplayName("returns notFound on plain page with no next button")
        void returnsNotFound() {
            assertThat(strategy.detect(snap("<div>content</div>")).isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasNextPage()")
    class HasNextPage {

        @Test
        @DisplayName("returns true when next button selector matches")
        void returnsTrueWhenFound() {
            String html = "<a rel='next' href='/page/2'>Next</a>";
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isTrue();
        }

        @Test
        @DisplayName("returns false when no selector matches")
        void returnsFalseWhenAbsent() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap("<div>end</div>"), ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when context is stale")
        void returnsFalseWhenStale() {
            String html = "<a rel='next' href='/page/2'>Next</a>";
            PaginationContext ctx = PaginationContext.of(null, 10).markStale();
            assertThat(strategy.hasNextPage(snap(html), ctx)).isFalse();
        }
    }

    @Test
    @DisplayName("goToNextPage() clicks the resolved selector")
    void goToNextPageClicks() {
        strategy.goToNextPage(browser, PaginationContext.of(null, 10));
        verify(browser).click(anyString());
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }
}

@DisplayName("NumberedPagination")
class NumberedPaginationTest {

    private NumberedPagination strategy;

    @BeforeEach
    void setUp() {
        strategy = new NumberedPagination();
    }

    @Test
    @DisplayName("type() returns NUMBERED")
    void type() {
        assertThat(strategy.type()).isEqualTo(PaginationType.NUMBERED);
    }

    @Nested
    @DisplayName("detect()")
    class Detect {

        @Test
        @DisplayName("detects numbered pagination links with high confidence")
        void detectsNumberedLinks() {
            String html = "<nav class='pagination'>" +
                    "<a href='?page=1'>1</a>" +
                    "<a href='?page=2'>2</a>" +
                    "<a href='?page=3'>3</a>" +
                    "</nav>";
            PaginationStrategy.DetectionResult result = strategy.detect(snap(html));
            assertThat(result.isFound()).isTrue();
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
        }

        @Test
        @DisplayName("detects pagination container as fallback")
        void detectsPaginationContainer() {
            String html = "<nav aria-label='pagination'><a>Prev</a><a>Next</a></nav>";
            assertThat(strategy.detect(snap(html)).isFound()).isTrue();
        }

        @Test
        @DisplayName("returns notFound for plain page")
        void returnsNotFound() {
            assertThat(strategy.detect(snap("<div>text</div>")).isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("hasNextPage()")
    class HasNextPage {

        @Test
        @DisplayName("returns true when link for next page exists in href")
        void returnsTrueByHref() {
            String html = "<a href='?page=2'>2</a>";
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isTrue();
        }

        @Test
        @DisplayName("returns false when no next-page link found")
        void returnsFalseWhenAbsent() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            assertThat(strategy.hasNextPage(snap("<div>last page</div>"), ctx)).isFalse();
        }

        @Test
        @DisplayName("returns false when maxPages reached")
        void returnsFalseAtMaxPages() {
            String html = "<a href='?page=2'>2</a>";
            PaginationContext ctx = PaginationContext.of(null, 1);
            assertThat(strategy.hasNextPage(snap(html), ctx)).isFalse();
        }
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }
}