package com.elyadata.feature.project.domain.model;

import lombok.Builder;

@Builder
public record ProjectStats(
        long total,
        long active,
        long archived
) {
}