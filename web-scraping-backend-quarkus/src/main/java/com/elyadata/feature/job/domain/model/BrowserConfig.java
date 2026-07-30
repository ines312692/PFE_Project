package com.elyadata.feature.job.domain.model;

import lombok.Builder;

@Builder
public record BrowserConfig(
        Boolean javascriptEnabled,
        Boolean stealthMode,
        Integer requestDelayMs,
        Integer timeoutMs,
        String userAgent,
        SessionConfig sessionConfig
) {
}