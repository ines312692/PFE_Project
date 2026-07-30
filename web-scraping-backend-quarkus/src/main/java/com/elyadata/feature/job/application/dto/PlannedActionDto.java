package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.PageActionType;
import lombok.Builder;

@Builder
public record PlannedActionDto(
        PageActionType type,
        String selector,
        String value,
        Integer timeoutMs,
        Boolean optional,
        String label
) {
}