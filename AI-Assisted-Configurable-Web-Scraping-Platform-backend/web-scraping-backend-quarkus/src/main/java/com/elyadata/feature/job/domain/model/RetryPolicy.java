package com.elyadata.feature.job.domain.model;

import lombok.Builder;

@Builder
public record RetryPolicy(
        Integer maxRetries,
        Integer retryInitialDelayMs,
        Double retryBackoffMultiplier,
        Integer retryMaxDelayMs,
        Boolean circuitBreakerEnabled,
        Double circuitBreakerThreshold,
        Integer circuitBreakerResetSeconds
) {
}
