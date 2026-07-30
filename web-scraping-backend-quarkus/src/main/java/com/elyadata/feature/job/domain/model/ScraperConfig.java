package com.elyadata.feature.job.domain.model;

import lombok.Builder;

import java.util.List;

@Builder
public record ScraperConfig(
        List<String> seedUrls,
        Boolean singlePageMode,
        String waitForSelector,
        Boolean scrollToBottom,
        String clickBeforeExtract,
        String llmExtractionPrompt,
        PaginationType paginationType,
        String paginationSelector,
        Integer maxPaginationPages,
        List<PageAction> preExtractActions
) {
}