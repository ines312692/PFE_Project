package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobPriority;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.proxy.application.dto.ProxyConfigDto;
import com.elyadata.feature.schema.application.dto.ExtractionSchemaDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateJobRequest(

        @NotBlank
        String name,

        @NotBlank
        String targetUrl,

        @NotNull
        JobType jobType,

        @NotNull
        JobPriority priority,

        @NotNull
        JobTriggerType triggerType,

        @NotNull
        ExtractionMode extractionMode,

        String cronExpression,
        BrowserConfigDto browserConfig,
        RetryPolicyDto retryPolicy,
        ProxyConfigDto proxyConfig,
        ExtractionSchemaDto extractionSchema,
        ScraperConfigDto scraperConfig,
        CrawlerConfigDto crawlerConfig,

        UUID projectId
) {
}