package com.elyadata.feature.job.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SiteProbeRequest(
        @NotBlank(message = "URL must not be blank")
        String url
) {
}