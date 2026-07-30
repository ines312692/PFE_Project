package com.elyadata.feature.job.domain.model;

import lombok.Builder;

@Builder
public record CrawlerConfig(
        Integer maxPages,
        Integer maxDepth,
        String urlIncludePatterns,
        String urlExcludePatterns,
        Boolean followRedirects,
        Boolean respectRobotsTxt,
        String sitemapUrl
) {
}