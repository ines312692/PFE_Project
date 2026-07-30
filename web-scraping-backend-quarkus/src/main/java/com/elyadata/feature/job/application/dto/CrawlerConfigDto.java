package com.elyadata.feature.job.application.dto;


public record CrawlerConfigDto(
        Integer maxPages,
        Integer maxDepth,
        String urlIncludePatterns,
        String urlExcludePatterns,
        Boolean followRedirects,
        Boolean respectRobotsTxt,
        String sitemapUrl
) {
}