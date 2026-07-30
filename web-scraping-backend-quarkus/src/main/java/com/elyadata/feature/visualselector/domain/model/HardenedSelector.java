package com.elyadata.feature.visualselector.domain.model;

import lombok.Builder;

import java.util.List;


@Builder
public record HardenedSelector(
        String cssSelector,
        String xpath,
        int matchCount,
        boolean unique,
        List<String> previewValues,
        String suggestedName,
        String warning
) {
}