package com.elyadata.feature.scraper.crawler.pagination;

import com.elyadata.feature.job.domain.model.PaginationType;
import com.elyadata.feature.scraper.browser.ABrowser;
import com.elyadata.feature.scraper.browser.PageSnapshot;

public interface PaginationStrategy {

    PaginationType type();

    boolean hasNextPage(PageSnapshot snapshot, PaginationContext context);

    void goToNextPage(ABrowser browser, PaginationContext context);

    default DetectionResult detect(PageSnapshot snapshot) {
        return DetectionResult.notFound(type());
    }

    record DetectionResult(
            PaginationType type,
            double confidence,
            String reason,
            String suggestedSelector
    ) {
        public static DetectionResult notFound(PaginationType t) {
            return new DetectionResult(t, 0.0, "not detected", null);
        }

        public boolean isFound() {
            return confidence > 0.0;
        }
    }
}