package com.elyadata.feature.job.application.dto;

public record BrowserConfigDto(
        Boolean javascriptEnabled,
        Boolean stealthMode,
        Integer requestDelayMs,
        Integer timeoutMs,
        String userAgent,
        SessionConfigDto session
) {}