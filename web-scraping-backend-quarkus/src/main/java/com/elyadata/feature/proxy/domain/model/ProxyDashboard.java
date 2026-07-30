package com.elyadata.feature.proxy.domain.model;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProxyDashboard(
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
        LatencyBuckets latencyBuckets,

        Map<AntiBotProvider, Map<ProtectionStatus, Long>> antiBotMatrix,
        List<RecentHealthCheck> recentHealthChecks,
        Instant lastRefreshAt
) {
    @Builder
    public record LatencyBuckets(
            long fast,
            long medium,
            long slow,
            long unknown
    ) {
    }

    @Builder
    public record RecentHealthCheck(
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