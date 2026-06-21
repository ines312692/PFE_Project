package com.elyadata.feature.job.domain.model;

import lombok.Builder;

@Builder
public record JobStats(
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