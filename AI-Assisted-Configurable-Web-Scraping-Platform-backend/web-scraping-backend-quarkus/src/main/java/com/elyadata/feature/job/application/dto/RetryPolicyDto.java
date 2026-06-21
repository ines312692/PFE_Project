package com.elyadata.feature.job.application.dto;

import lombok.Builder;

@Builder
public record RetryPolicyDto(
        Integer maxRetries,
        Integer retryInitialDelayMs,
        Double retryBackoffMultiplier,
        Integer retryMaxDelayMs,
        Boolean circuitBreakerEnabled,
        Double circuitBreakerThreshold,
        Integer circuitBreakerResetSeconds
) {
}
