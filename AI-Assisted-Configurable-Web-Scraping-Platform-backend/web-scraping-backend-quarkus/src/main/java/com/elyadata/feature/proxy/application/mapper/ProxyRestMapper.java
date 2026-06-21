package com.elyadata.feature.proxy.application.mapper;

import com.elyadata.feature.proxy.application.dto.*;
import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyDashboard;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.domain.service.ProxyService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "jakarta-cdi")
public interface ProxyRestMapper {

    @Mapping(target = "antiBotStatuses", expression = "java(java.util.Map.of())")
    ProxyResponse toRest(Proxy proxy);

    List<ProxyResponse> toRest(List<Proxy> proxies);

    ProxyConfig toDomain(ProxyConfigDto dto);

    ProxyConfigDto toDto(ProxyConfig domain);

    default ProxyResponse toRest(Proxy proxy, List<ProxyAntiBotStatus> statuses) {
        if (proxy == null) return null;
        Map<AntiBotProvider, AntiBotStatusDto> map = (statuses == null || statuses.isEmpty())
                ? Map.of()
                : statuses.stream().collect(Collectors.toMap(
                ProxyAntiBotStatus::provider,
                this::toAntiBotDto,
                (a, b) -> a));
        return new ProxyResponse(
                proxy.id(), proxy.host(), proxy.port(), proxy.protocol(),
                proxy.country(), proxy.anonymity(), proxy.source(), proxy.status(),
                proxy.latencyMs(), proxy.successRate(),
                proxy.totalUses(), proxy.successfulUses(),
                proxy.lastCheckedAt(), proxy.lastUsedAt(),
                proxy.createdAt(), proxy.updatedAt(),
                map
        );
    }

    default List<ProxyResponse> toRest(List<Proxy> proxies,
                                       Map<UUID, List<ProxyAntiBotStatus>> grouped) {
        if (proxies == null) return List.of();
        return proxies.stream()
                .map(p -> toRest(p, grouped.getOrDefault(p.id(), List.of())))
                .toList();
    }

    default AntiBotStatusDto toAntiBotDto(ProxyAntiBotStatus s) {
        if (s == null) return null;
        return AntiBotStatusDto.builder()
                .provider(s.provider())
                .status(s.status())
                .detected(s.detected())
                .blocked(s.blocked())
                .lastStatusCode(s.lastStatusCode())
                .lastCheckedAt(s.lastCheckedAt())
                .lastError(s.lastError())
                .build();
    }

    default ProxyStatsResponse toStats(ProxyService.PoolStats stats) {
        if (stats == null) {
            return new ProxyStatsResponse(0L, 0L, 0L, 0L, 0L, 0L);
        }
        return new ProxyStatsResponse(
                stats.total(), stats.active(), stats.inactive(),
                stats.blacklisted(), stats.cloudflareClean(), stats.manual()
        );
    }

    default ProxyDashboardResponse toDashboard(ProxyDashboard d) {
        return new ProxyDashboardResponse(
                d.total(), d.active(), d.inactive(), d.blacklisted(),
                d.avgLatencyMs(), d.avgSuccessRate(), d.antiBotTestedCount(),
                d.countBySource(), d.countByProtocol(), d.countByAnonymity(),
                d.topCountries(),
                new ProxyDashboardResponse.LatencyBucketsDto(
                        d.latencyBuckets().fast(),
                        d.latencyBuckets().medium(),
                        d.latencyBuckets().slow(),
                        d.latencyBuckets().unknown()
                ),
                d.antiBotMatrix(),
                d.recentHealthChecks().stream().map(rh ->
                        new ProxyDashboardResponse.RecentHealthCheckDto(
                                rh.proxyId(), rh.host(), rh.port(), rh.success(),
                                rh.latencyMs(), rh.httpStatusCode(),
                                rh.errorMessage(), rh.checkedAt()
                        )).toList(),
                d.lastRefreshAt()
        );
    }
}