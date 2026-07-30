package com.elyadata.feature.scraper.crawler.pagination;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class PaginationContext {

    private final int currentPage;
    private final int maxPages;
    private final String selector;
    private final long lastHtmlLength;
    private final boolean stale;

    public static PaginationContext of(String selector, int maxPages) {
        return PaginationContext.builder()
                .currentPage(1)
                .maxPages(maxPages > 0 ? maxPages : 10)
                .selector(selector)
                .lastHtmlLength(0L)
                .stale(false)
                .build();
    }

    public boolean hasReachedMaxPages() {
        return maxPages > 0 && currentPage >= maxPages;
    }

    public PaginationContext next() {
        return this.toBuilder().currentPage(currentPage + 1).build();
    }

    public PaginationContext markStale() {
        return this.toBuilder().stale(true).build();
    }

    public PaginationContext withHtmlLength(long length) {
        return this.toBuilder().lastHtmlLength(length).build();
    }
}
