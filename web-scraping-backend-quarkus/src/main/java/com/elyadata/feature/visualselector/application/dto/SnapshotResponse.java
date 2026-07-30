package com.elyadata.feature.visualselector.application.dto;

import lombok.Builder;


@Builder
public record SnapshotResponse(
        String finalUrl,
        String html,
        String title,
        int htmlLength
) {
}