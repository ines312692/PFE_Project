package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaginationStrategyRegistry")
class PaginationStrategyRegistryTest {

    @Mock
    private Instance<PaginationStrategy> strategyInstances;
    @Mock
    private InfiniteScrollPagination scrollStrategy;
    @Mock
    private Instance<PaginationDetector> detectorInstance;
    @Mock
    private PaginationDetector detector;
    @Mock
    private ABrowser browser;
    @Mock
    private InternalScrapeRequest request;

    private UrlParamPagination urlParamStrategy;
    private NumberedPagination numberedStrategy;

    private PaginationStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        urlParamStrategy = new UrlParamPagination();
        numberedStrategy = new NumberedPagination();
        List<PaginationStrategy> list = List.of(urlParamStrategy, numberedStrategy, scrollStrategy);
        when(strategyInstances.spliterator()).thenReturn(list.spliterator());
        when(scrollStrategy.type()).thenReturn(PaginationType.INFINITE_SCROLL);

        when(detectorInstance.get()).thenReturn(detector);

        registry = new PaginationStrategyRegistry(
                strategyInstances, scrollStrategy, detectorInstance);
    }
    @Nested
    @DisplayName("getStrategy()")
    class GetStrategy {

        @Test
        @DisplayName("returns the correct strategy for a registered type")
        void returnsRegisteredStrategy() {
            assertThat(registry.getStrategy(PaginationType.URL_PARAM))
                    .isSameAs(urlParamStrategy);
        }

        @Test
        @DisplayName("returns null for an unregistered type")
        void returnsNullForUnknown() {
            assertThat(registry.getStrategy(PaginationType.CURSOR)).isNull();
        }
    }

    @Nested
    @DisplayName("buildContext()")
    class BuildContext {

        @Test
        @DisplayName("uses request selector when present")
        void usesRequestSelector() {
            when(request.getPaginationSelector()).thenReturn(".my-selector");
            when(request.getMaxPaginationPages()).thenReturn(5);

            PaginationContext ctx = registry.buildContext(request);

            assertThat(ctx.getSelector()).isEqualTo(".my-selector");
            assertThat(ctx.getMaxPages()).isEqualTo(5);
            assertThat(ctx.getCurrentPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("falls back to provided fallbackSelector when request selector is blank")
        void usesFallbackSelector() {
            when(request.getPaginationSelector()).thenReturn("  ");
            when(request.getMaxPaginationPages()).thenReturn(3);

            PaginationContext ctx = registry.buildContext(request, ".fallback");

            assertThat(ctx.getSelector()).isEqualTo(".fallback");
        }

        @Test
        @DisplayName("falls back to null when both selectors are blank")
        void nullSelectorWhenBothBlank() {
            when(request.getPaginationSelector()).thenReturn(null);
            when(request.getMaxPaginationPages()).thenReturn(3);

            PaginationContext ctx = registry.buildContext(request, null);

            assertThat(ctx.getSelector()).isNull();
        }

        @Test
        @DisplayName("defaults maxPages to 10 when request returns 0")
        void defaultsMaxPagesTo10() {
            when(request.getPaginationSelector()).thenReturn(null);
            when(request.getMaxPaginationPages()).thenReturn(0);

            PaginationContext ctx = registry.buildContext(request);

            assertThat(ctx.getMaxPages()).isEqualTo(10);
        }
    }
    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("returns declared type directly when not AUTO")
        void returnsDeclaredType() {
            when(request.getPaginationType()).thenReturn("NUMBERED");

            PaginationStrategyRegistry.Resolution r = registry.resolve(request);

            assertThat(r.type()).isEqualTo(PaginationType.NUMBERED);
            assertThat(r.suggestedSelector()).isNull();
            verifyNoInteractions(detectorInstance);
        }

        @Test
        @DisplayName("returns NONE when paginationType is null")
        void returnsNoneForNullType() {
            when(request.getPaginationType()).thenReturn(null);

            PaginationStrategyRegistry.Resolution r = registry.resolve(request);

            assertThat(r.type()).isEqualTo(PaginationType.NONE);
        }

        @Test
        @DisplayName("returns NONE when paginationType is unknown string")
        void returnsNoneForUnknownType() {
            when(request.getPaginationType()).thenReturn("BANANA");

            PaginationStrategyRegistry.Resolution r = registry.resolve(request);

            assertThat(r.type()).isEqualTo(PaginationType.NONE);
        }

        @Test
        @DisplayName("AUTO with null snapshot returns NONE without calling detector")
        void autoWithNullSnapshotReturnsNone() {
            when(request.getPaginationType()).thenReturn("AUTO");

            PaginationStrategyRegistry.Resolution r = registry.resolve(request, null);

            assertThat(r.type()).isEqualTo(PaginationType.NONE);
            verify(detectorInstance, never()).get();
        }

        @Test
        @DisplayName("AUTO with snapshot delegates to detector and returns its result")
        void autoWithSnapshotDelegatesToDetector() {
            when(request.getPaginationType()).thenReturn("AUTO");

            var report = new PaginationDetector.DetectionReport(
                    PaginationType.URL_PARAM, 0.95, List.of(), "page");
            when(detector.detect(any())).thenReturn(report);

            PageSnapshot snap = PageSnapshot.builder()
                    .url("https://example.com")
                    .html("<a href='?page=2'>2</a>")
                    .build();

            PaginationStrategyRegistry.Resolution r = registry.resolve(request, snap);

            assertThat(r.type()).isEqualTo(PaginationType.URL_PARAM);
            assertThat(r.suggestedSelector()).isEqualTo("page");
        }
    }

    @Nested
    @DisplayName("isPaginationEnabled()")
    class IsPaginationEnabled {

        @Test
        @DisplayName("returns true when type is not NONE and maxPages > 1")
        void trueWhenEnabledAndMultiPage() {
            when(request.getPaginationType()).thenReturn("NUMBERED");
            when(request.getMaxPaginationPages()).thenReturn(5);

            assertThat(registry.isPaginationEnabled(request)).isTrue();
        }

        @Test
        @DisplayName("returns false when type is NONE")
        void falseWhenNone() {
            when(request.getPaginationType()).thenReturn("NONE");
            when(request.getMaxPaginationPages()).thenReturn(5);

            assertThat(registry.isPaginationEnabled(request)).isFalse();
        }

        @Test
        @DisplayName("returns false when maxPages is 1")
        void falseWhenSinglePage() {
            when(request.getPaginationType()).thenReturn("NUMBERED");
            when(request.getMaxPaginationPages()).thenReturn(1);

            assertThat(registry.isPaginationEnabled(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("advance()")
    class Advance {

        @Test
        @DisplayName("returns null when type is NONE")
        void returnsNullForNone() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            PageSnapshot snap = snap("<div/>");

            assertThat(registry.advance(browser, snap, ctx, PaginationType.NONE)).isNull();
        }

        @Test
        @DisplayName("returns null when type is AUTO")
        void returnsNullForAuto() {
            PaginationContext ctx = PaginationContext.of(null, 5);

            assertThat(registry.advance(browser, snap("<div/>"), ctx, PaginationType.AUTO)).isNull();
        }

        @Test
        @DisplayName("returns null when type is null")
        void returnsNullForNullType() {
            PaginationContext ctx = PaginationContext.of(null, 5);

            assertThat(registry.advance(browser, snap("<div/>"), ctx, null)).isNull();
        }

        @Test
        @DisplayName("returns null when no strategy registered for type")
        void returnsNullForUnregisteredType() {
            PaginationContext ctx = PaginationContext.of(null, 5);

            assertThat(registry.advance(browser, snap("<div/>"), ctx, PaginationType.CURSOR)).isNull();
        }

        @Test
        @DisplayName("returns null when strategy reports no next page")
        void returnsNullWhenExhausted() {
            PaginationContext ctx = PaginationContext.of(null, 1);
            String html = "<a href='?page=2'>2</a>";

            assertThat(registry.advance(browser, snap(html), ctx, PaginationType.URL_PARAM)).isNull();
        }

        @Test
        @DisplayName("returns updated context (page+1) when strategy advances")
        void returnsUpdatedContext() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            String html = "<a href='?page=2'>2</a>";
            when(browser.snapshot()).thenReturn(snap("https://example.com?page=1"));

            PaginationContext updated = registry.advance(
                    browser, snap(html), ctx, PaginationType.URL_PARAM);

            assertThat(updated).isNotNull();
            assertThat(updated.getCurrentPage()).isEqualTo(2);
        }

        @Test
        @DisplayName("calls refreshContext() after INFINITE_SCROLL advance")
        void refreshesContextAfterScroll() {
            PaginationContext ctx = PaginationContext.of(null, 5);
            String bigHtml = "x".repeat(2_000);

            when(scrollStrategy.hasNextPage(any(), any())).thenReturn(true);
            doNothing().when(scrollStrategy).goToNextPage(any(), any());
            when(browser.snapshot()).thenReturn(snap(bigHtml));
            when(scrollStrategy.refreshContext(any(), any()))
                    .thenAnswer(inv -> ((PaginationContext) inv.getArgument(1)).next());

            PaginationContext updated = registry.advance(
                    browser, snap(bigHtml), ctx, PaginationType.INFINITE_SCROLL);

            assertThat(updated).isNotNull();
            verify(scrollStrategy).refreshContext(any(), any());
        }
    }

    @Nested
    @DisplayName("resolveType() — deprecated")
    class ResolveTypeDeprecated {

        @Test
        @DisplayName("delegates to resolve() and returns type")
        void delegatesForNonAuto() {
            when(request.getPaginationType()).thenReturn("LOAD_MORE");

            @SuppressWarnings("deprecation")
            PaginationType type = registry.resolveType(request);

            assertThat(type).isEqualTo(PaginationType.LOAD_MORE);
        }

        @Test
        @DisplayName("delegates to resolve(request, snapshot) and returns type")
        void delegatesWithSnapshot() {
            when(request.getPaginationType()).thenReturn("NEXT_BUTTON");

            @SuppressWarnings("deprecation")
            PaginationType type = registry.resolveType(request, snap("<a rel='next' href='/p2'>Next</a>"));

            assertThat(type).isEqualTo(PaginationType.NEXT_BUTTON);
        }
    }

    private static PageSnapshot snap(String html) {
        return PageSnapshot.builder().url("https://example.com").html(html).build();
    }
}
