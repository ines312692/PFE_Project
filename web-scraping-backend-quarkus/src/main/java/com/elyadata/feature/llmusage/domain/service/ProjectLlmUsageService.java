package com.elyadata.feature.llmusage.domain.service;

import com.elyadata.feature.llmusage.application.dto.ProjectLlmUsageResponse;
import com.elyadata.feature.llmusage.domain.service.port.ProjectLlmUsagePort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class ProjectLlmUsageService {

    private final ProjectLlmUsagePort port;

    public ProjectLlmUsageService(ProjectLlmUsagePort port) {
        this.port = port;
    }

    public ProjectLlmUsageResponse forProject(UUID projectId, Instant from, Instant to) {
        Instant safeTo   = to   != null ? to   : Instant.now();
        Instant safeFrom = from != null ? from : safeTo.minus(30, ChronoUnit.DAYS);
        return port.aggregateForProject(projectId, safeFrom, safeTo);
    }
}