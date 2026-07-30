package com.elyadata.feature.scraperesult.domain.service.port;

import com.elyadata.feature.scraperesult.application.dto.ProjectScrapeStatsResponse;

import java.time.Instant;
import java.util.UUID;

public interface ProjectScrapeStatsPort {
    ProjectScrapeStatsResponse aggregateForProject(UUID projectId, Instant from, Instant to);
}