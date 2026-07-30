package com.elyadata.feature.job.application.dto;

import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.JobPriority;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.proxy.application.dto.ProxyConfigDto;
import com.elyadata.feature.schema.application.dto.ExtractionSchemaDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateJobRequest(

        @NotBlank
        String name,
        @NotBlank
        String targetUrl,
        @NotNull
        JobType jobType,
        @NotNull
        JobStatus status,
        @NotNull
        JobPriority priority,
        @NotNull
        JobTriggerType triggerType,
        @NotNull
        ExtractionMode extractionMode,
        String cronExpression,
        BrowserConfigDto browserConfig,
        RetryPolicyDto retryPolicy,
        ExtractionSchemaDto extractionSchema,
        ScraperConfigDto scraperConfig,
        CrawlerConfigDto crawlerConfig,
        ProxyConfigDto proxyConfig
) {
}