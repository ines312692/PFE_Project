package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobPriority;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.proxy.application.dto.ProxyConfigDto;
import com.elyadata.feature.schema.application.dto.ExtractionSchemaDto;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;


@Builder(toBuilder = true)
public record JobResponse(
        UUID id,
        String name,
        String targetUrl,
        JobType jobType,
        JobStatus status,
        JobPriority priority,
        JobTriggerType triggerType,
        ExtractionMode extractionMode,
        BrowserConfigDto browserConfig,
        RetryPolicyDto retryPolicy,
        ExtractionSchemaDto extractionSchema,
        ScraperConfigDto scraperConfig,
        CrawlerConfigDto crawlerConfig,
        String cronExpression,
        String webhookToken,
        String webhookSecret,
        Boolean webhookEnabled,
        UUID createdBy,
        String createdByUsername,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        ProxyConfigDto proxyConfig
) {
}