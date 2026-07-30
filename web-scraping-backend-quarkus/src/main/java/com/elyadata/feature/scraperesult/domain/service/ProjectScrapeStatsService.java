package com.elyadata.feature.scraperesult.domain.service;

import com.elyadata.feature.scraperesult.application.dto.ProjectScrapeStatsResponse;
import com.elyadata.feature.scraperesult.domain.service.port.ProjectScrapeStatsPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class ProjectScrapeStatsService {

    private final ProjectScrapeStatsPort port;

    public ProjectScrapeStatsService(ProjectScrapeStatsPort port) {
        this.port = port;
    }

    public ProjectScrapeStatsResponse forProject(UUID projectId, Instant from, Instant to) {
        Instant safeTo   = to   != null ? to   : Instant.now();
        Instant safeFrom = from != null ? from : safeTo.minus(30, ChronoUnit.DAYS);
        return port.aggregateForProject(projectId, safeFrom, safeTo);
    }
}