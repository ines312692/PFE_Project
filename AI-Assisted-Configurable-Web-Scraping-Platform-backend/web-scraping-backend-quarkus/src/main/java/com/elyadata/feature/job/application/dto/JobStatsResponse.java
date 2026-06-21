package com.elyadata.feature.job.application.dto;

import lombok.Builder;

@Builder
public record JobStatsResponse(
        long total,
        long created,
        long queued,
        long running,
        long paused,
        long completed,
        long failed,
        long cancelled
) {
}