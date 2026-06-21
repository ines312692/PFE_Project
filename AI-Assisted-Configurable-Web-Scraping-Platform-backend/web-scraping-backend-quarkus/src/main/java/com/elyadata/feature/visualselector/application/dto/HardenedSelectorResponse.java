package com.elyadata.feature.visualselector.application.dto;

import lombok.Builder;

import java.util.List;


@Builder
public record HardenedSelectorResponse(
        String cssSelector,
        String xpath,
        int matchCount,
        boolean unique,
        List<String> previewValues,
        String suggestedName,
        String warning
) {
}