package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class PaginationStrategyRegistry {

    private final Map<PaginationType, PaginationStrategy> strategies;
    private final InfiniteScrollPagination scrollStrategy;
    private final Instance<PaginationDetector> detectorInstance;

    @Inject
    public PaginationStrategyRegistry(Instance<PaginationStrategy> instances,
                                      InfiniteScrollPagination scrollStrategy,
                                      Instance<PaginationDetector> detectorInstance) {
        this.scrollStrategy = scrollStrategy;
        this.detectorInstance = detectorInstance;
        this.strategies = StreamSupport
                .stream(instances.spliterator(), false)
                .collect(Collectors.toMap(PaginationStrategy::type, Function.identity()));

        log.info("[PaginationRegistry] registered {} strategies: {}",
                strategies.size(), strategies.keySet());
    }

    public PaginationContext buildContext(InternalScrapeRequest request, String fallbackSelector) {
        String selector = hasText(request.getPaginationSelector())
                ? request.getPaginationSelector()
                : fallbackSelector;
        return PaginationContext.of(selector, request.getMaxPaginationPages());
    }

    public PaginationContext buildContext(InternalScrapeRequest request) {
        return buildContext(request, null);
    }

    public PaginationStrategy getStrategy(PaginationType type) {
        return strategies.get(type);
    }

    public Resolution resolve(InternalScrapeRequest request) {
        return resolve(request, null);
    }

    public Resolution resolve(InternalScrapeRequest request, PageSnapshot snapshot) {
        PaginationType declared = parseType(request.getPaginationType());

        if (declared != PaginationType.AUTO) {
            return new Resolution(declared, null);
        }

        if (snapshot == null) {
            log.warn("[PaginationRegistry] AUTO mode but no snapshot available — returning NONE");
            return new Resolution(PaginationType.NONE, null);
        }

        var report = detectorInstance.get().detect(snapshot);
        log.info("[PaginationRegistry] AUTO → detected={} confidence={} suggestedSelector={}",
                report.detectedType(), report.confidence(), report.suggestedSelector());

        return new Resolution(report.detectedType(), report.suggestedSelector());
    }

    @Deprecated(since = "1.0", forRemoval = true)
    public PaginationType resolveType(InternalScrapeRequest request) {
        return resolve(request).type();
    }

    @Deprecated(since = "1.0", forRemoval = true)
    public PaginationType resolveType(InternalScrapeRequest request, PageSnapshot snapshot) {
        return resolve(request, snapshot).type();
    }

    private PaginationType parseType(String raw) {
        if (raw == null || raw.isBlank()) return PaginationType.NONE;
        try {
            return PaginationType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[PaginationRegistry] unknown paginationType='{}' — defaulting to NONE", raw);
            return PaginationType.NONE;
        }
    }

    public PaginationContext advance(ABrowser browser,
                                     PageSnapshot snapshot,
                                     PaginationContext context,
                                     PaginationType type) {

        if (type == null || type == PaginationType.NONE || type == PaginationType.AUTO) {
            return null;
        }

        PaginationStrategy strategy = strategies.get(type);
        if (strategy == null) {
            log.warn("[PaginationRegistry] no strategy for type={}", type);
            return null;
        }

        if (!strategy.hasNextPage(snapshot, context)) {
            log.info("[PaginationRegistry] pagination exhausted at page {}", context.getCurrentPage());
            return null;
        }

        strategy.goToNextPage(browser, context);

        PaginationContext updated = context.next();
        if (type == PaginationType.INFINITE_SCROLL) {
            PageSnapshot afterScroll = browser.snapshot();
            updated = scrollStrategy.refreshContext(afterScroll, updated);
        }

        return updated;
    }

    public boolean isPaginationEnabled(InternalScrapeRequest request) {
        PaginationType t = parseType(request.getPaginationType());
        return t != PaginationType.NONE && request.getMaxPaginationPages() > 1;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public record Resolution(PaginationType type, String suggestedSelector) {
    }
}