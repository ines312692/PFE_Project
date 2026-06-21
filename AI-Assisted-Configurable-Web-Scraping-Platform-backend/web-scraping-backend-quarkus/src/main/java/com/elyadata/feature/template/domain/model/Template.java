package com.elyadata.feature.template.domain.model;

import com.elyadata.feature.job.domain.model.JobType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record Template(
        UUID id,
        String name,
        String description,
        TemplateCategory category,
        JobType jobType,
        List<String> tags,
        int popularity,
        Map<String, Object> config,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}