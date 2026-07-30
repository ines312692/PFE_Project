package com.elyadata.feature.llmusage.domain.service.port;

import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse;

import java.time.Instant;
import java.util.UUID;

public interface ProjectLlmUsagePort {
    ProjectLlmUsageResponse aggregateForProject(UUID projectId, Instant from, Instant to);
}