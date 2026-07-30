package com.elyadata.feature.proxy.application.dto;

public record ProxyRefreshResponse(
        int newProxies,
        long timestampMs
) {
}
