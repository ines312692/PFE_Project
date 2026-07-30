package com.elyadata.feature.scraper.crawler.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaginationContext")
class PaginationContextTest {

    @Nested
    @DisplayName("of() factory")
    class Of {

        @Test
        @DisplayName("creates context starting at page 1")
        void startsAtPageOne() {
            PaginationContext ctx = PaginationContext.of("selector", 10);
            assertThat(ctx.getCurrentPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("stores provided selector")
        void storesSelector() {
            PaginationContext ctx = PaginationContext.of(".next-btn", 5);
            assertThat(ctx.getSelector()).isEqualTo(".next-btn");
        }

        @Test
        @DisplayName("uses maxPages=10 when zero or negative is given")
        void defaultsMaxPages() {
            PaginationContext ctx = PaginationContext.of(null, 0);
            assertThat(ctx.getMaxPages()).isEqualTo(10);
        }

        @Test
        @DisplayName("stores provided maxPages when positive")
        void storesMaxPages() {
            PaginationContext ctx = PaginationContext.of(null, 20);
            assertThat(ctx.getMaxPages()).isEqualTo(20);
        }

        @Test
        @DisplayName("starts not stale with htmlLength=0")
        void startsNotStale() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            assertThat(ctx.isStale()).isFalse();
            assertThat(ctx.getLastHtmlLength()).isZero();
        }
    }

    @Nested
    @DisplayName("hasReachedMaxPages()")
    class HasReachedMaxPages {

        @Test
        @DisplayName("returns false when currentPage < maxPages")
        void notReachedWhenBelow() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            assertThat(ctx.hasReachedMaxPages()).isFalse();
        }

        @Test
        @DisplayName("returns true when currentPage == maxPages")
        void reachedWhenEqual() {
            // Advance to maxPages
            PaginationContext ctx = PaginationContext.of(null, 1);
            assertThat(ctx.hasReachedMaxPages()).isTrue();  // starts at 1, max=1
        }

        @Test
        @DisplayName("returns true when currentPage > maxPages after several advances")
        void reachedAfterAdvances() {
            PaginationContext ctx = PaginationContext.of(null, 3);
            ctx = ctx.next().next(); // page 3
            assertThat(ctx.hasReachedMaxPages()).isTrue();
        }
    }

    @Nested
    @DisplayName("next()")
    class Next {

        @Test
        @DisplayName("increments currentPage by 1")
        void incrementsPage() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            PaginationContext next = ctx.next();
            assertThat(next.getCurrentPage()).isEqualTo(2);
        }

        @Test
        @DisplayName("is immutable — original context unchanged")
        void originalUnchanged() {
            PaginationContext ctx = PaginationContext.of(null, 10);
            ctx.next();
            assertThat(ctx.getCurrentPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("preserves selector and maxPages")
        void preservesFields() {
            PaginationContext ctx = PaginationContext.of(".btn", 7);
            PaginationContext next = ctx.next();
            assertThat(next.getSelector()).isEqualTo(".btn");
            assertThat(next.getMaxPages()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("markStale()")
    class MarkStale {

        @Test
        @DisplayName("sets stale=true")
        void setsStale() {
            PaginationContext ctx = PaginationContext.of(null, 5).markStale();
            assertThat(ctx.isStale()).isTrue();
        }

        @Test
        @DisplayName("original is still not stale")
        void originalUntouched() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            ctx.markStale();
            assertThat(ctx.isStale()).isFalse();
        }
    }

    @Nested
    @DisplayName("withHtmlLength()")
    class WithHtmlLength {

        @Test
        @DisplayName("stores the new html length")
        void storesLength() {
            PaginationContext ctx = PaginationContext.of(null, 5).withHtmlLength(42_000L);
            assertThat(ctx.getLastHtmlLength()).isEqualTo(42_000L);
        }

        @Test
        @DisplayName("is immutable — original length unchanged")
        void originalUnchanged() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            ctx.withHtmlLength(9_999L);
            assertThat(ctx.getLastHtmlLength()).isZero();
        }
    }
}
