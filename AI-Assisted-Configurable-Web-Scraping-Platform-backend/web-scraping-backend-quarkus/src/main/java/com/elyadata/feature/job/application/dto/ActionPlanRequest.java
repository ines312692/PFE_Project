package com.elyadata.feature.job.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ActionPlanRequest(
        @NotBlank @Size(max = 2_048) String targetUrl,
        @NotBlank @Size(max = 2_000) String instruction,
        UUID jobId
) {
}