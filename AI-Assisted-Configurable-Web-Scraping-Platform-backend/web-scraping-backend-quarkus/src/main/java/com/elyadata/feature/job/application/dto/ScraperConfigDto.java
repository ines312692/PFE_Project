package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.PageActionType;
import com.elyadata.feature.job.domain.model.PaginationType;

import java.util.List;

public record ScraperConfigDto(
        List<String> seedUrls,
        Boolean singlePageMode,
        String waitForSelector,
        Boolean scrollToBottom,
        String clickBeforeExtract,
        String llmExtractionPrompt,
        PaginationType paginationType,
        String paginationSelector,
        Integer maxPaginationPages,
        List<PageActionDto> preExtractActions
) {
    public record PageActionDto(
            PageActionType type,
            String selector,
            String value,
            Integer timeoutMs,
            Boolean optional,
            String label
    ) {}
}