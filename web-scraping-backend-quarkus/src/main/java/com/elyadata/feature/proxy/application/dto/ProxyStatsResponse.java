package com.elyadata.feature.proxy.application.dto;

public record ProxyStatsResponse(
        Long total,
        Long active,
        Long inactive,
        Long blacklisted,
        Long cloudflareClean,
        Long manual
) {
}