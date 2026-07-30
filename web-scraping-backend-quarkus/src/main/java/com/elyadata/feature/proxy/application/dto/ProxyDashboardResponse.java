package com.elyadata.feature.proxy.application.dto;

import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProxyDashboardResponse(
        long total,
        long active,
        long inactive,
        long blacklisted,
        Double avgLatencyMs,
        Double avgSuccessRate,
        long antiBotTestedCount,
        Map<ProxySource, Long> countBySource,
        Map<ProxyProtocol, Long> countByProtocol,
        Map<ProxyAnonymity, Long> countByAnonymity,
        Map<String, Long> topCountries,
        LatencyBucketsDto latencyBuckets,
        Map<AntiBotProvider, Map<ProtectionStatus, Long>> antiBotMatrix,
        List<RecentHealthCheckDto> recentHealthChecks,
        Instant lastRefreshAt
) {
    public record LatencyBucketsDto(long fast, long medium, long slow, long unknown) {
    }

    public record RecentHealthCheckDto(
            UUID proxyId,
            String host,
            Integer port,
            boolean success,
            Integer latencyMs,
            Integer httpStatusCode,
            String errorMessage,
            Instant checkedAt
    ) {
    }
}