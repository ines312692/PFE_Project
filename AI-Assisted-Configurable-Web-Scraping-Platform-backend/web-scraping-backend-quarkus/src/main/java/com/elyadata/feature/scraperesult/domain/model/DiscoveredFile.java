package com.elyadata.feature.scraperesult.domain.model;

public record DiscoveredFile(
        String url,
        String fileType,
        int statusCode,
        String content
) {
}