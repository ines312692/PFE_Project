package com.elyadata.feature.proxy.domain.model;

import lombok.Builder;

@Builder
public record ProxyOutcome(
        boolean success,
        Integer latencyMs,
        String errorMessage
) {

    public static ProxyOutcome success(int latencyMs) {
        return ProxyOutcome.builder().success(true).latencyMs(latencyMs).build();
    }

    public static ProxyOutcome failure(String error) {
        return ProxyOutcome.builder().success(false).errorMessage(error).build();
    }
}