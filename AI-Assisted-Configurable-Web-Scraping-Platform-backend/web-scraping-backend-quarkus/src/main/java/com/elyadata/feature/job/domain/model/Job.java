package com.elyadata.feature.job.domain.model;

import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Job(
        UUID id,
        String name,
        String targetUrl,
        JobType jobType,
        JobStatus status,
        JobPriority priority,
        JobTriggerType triggerType,
        ExtractionMode extractionMode,
        BrowserConfig browserConfig,
        RetryPolicy retryPolicy,
        ExtractionSchema extractionSchema,
        ScraperConfig scraperConfig,
        CrawlerConfig crawlerConfig,
        ProxyConfig proxyConfig,
        String cronExpression,
        String webhookToken,
        String webhookSecret,
        Boolean webhookEnabled,
        UUID projectId,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}