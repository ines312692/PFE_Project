package com.elyadata.feature.visualselector.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


public record SnapshotRequest(
        @NotBlank(message = "url is required")
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,

        boolean stealthMode,

        String waitForSelector
) {
}