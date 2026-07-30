package com.elyadata.feature.proxy.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ProxyHealthCheck(
        UUID id,
        UUID proxyId,
        boolean success,
        Integer latencyMs,
        Integer httpStatusCode,
        String errorMessage,
        Instant checkedAt
) {
}