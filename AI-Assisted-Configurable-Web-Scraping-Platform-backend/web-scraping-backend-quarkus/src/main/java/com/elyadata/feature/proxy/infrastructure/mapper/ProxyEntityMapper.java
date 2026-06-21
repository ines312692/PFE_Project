package com.elyadata.feature.proxy.infrastructure.mapper;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyHealthCheck;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyConfigEntity;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyEntity;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyHealthCheckEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProxyEntityMapper {

    public Proxy toDomain(ProxyEntity e) {
        if (e == null) return null;
        return Proxy.builder()
                .id(e.getId())
                .host(e.getHost())
                .port(e.getPort())
                .protocol(e.getProtocol())
                .username(e.getUsername())
                .password(e.getPassword())
                .country(e.getCountry())
                .anonymity(e.getAnonymity())
                .source(e.getSource())
                .status(e.getStatus())
                .latencyMs(e.getLatencyMs())
                .successRate(e.getSuccessRate())
                .totalUses(e.getTotalUses())
                .successfulUses(e.getSuccessfulUses())
                .lastCheckedAt(e.getLastCheckedAt())
                .lastUsedAt(e.getLastUsedAt())
                .leasedUntil(e.getLeasedUntil())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public ProxyEntity toEntity(Proxy p) {
        if (p == null) return null;
        return ProxyEntity.builder()
                .id(p.id())
                .host(p.host())
                .port(p.port())
                .protocol(p.protocol())
                .username(p.username())
                .password(p.password())
                .country(p.country())
                .anonymity(p.anonymity() != null ? p.anonymity() : ProxyAnonymity.UNKNOWN)
                .source(p.source())
                .status(p.status())
                .latencyMs(p.latencyMs())
                .successRate(p.successRate() != null ? p.successRate() : 0.0)
                .totalUses(p.totalUses() != null ? p.totalUses() : 0L)
                .successfulUses(p.successfulUses() != null ? p.successfulUses() : 0L)
                .lastCheckedAt(p.lastCheckedAt())
                .lastUsedAt(p.lastUsedAt())
                .leasedUntil(p.leasedUntil())
                .build();
    }

    public ProxyHealthCheck toDomain(ProxyHealthCheckEntity e) {
        if (e == null) return null;
        return ProxyHealthCheck.builder()
                .id(e.getId())
                .proxyId(e.getProxyId())
                .success(Boolean.TRUE.equals(e.getSuccess()))
                .latencyMs(e.getLatencyMs())
                .httpStatusCode(e.getHttpStatusCode())
                .errorMessage(e.getErrorMessage())
                .checkedAt(e.getCheckedAt())
                .build();
    }

    public ProxyHealthCheckEntity toEntity(ProxyHealthCheck c) {
        if (c == null) return null;
        return ProxyHealthCheckEntity.builder()
                .id(c.id())
                .proxyId(c.proxyId())
                .success(c.success())
                .latencyMs(c.latencyMs())
                .httpStatusCode(c.httpStatusCode())
                .errorMessage(c.errorMessage())
                .checkedAt(c.checkedAt())
                .build();
    }

    public ProxyConfig toDomain(ProxyConfigEntity e) {
        if (e == null) return ProxyConfig.disabled();
        return ProxyConfig.builder()
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .rotationStrategy(e.getRotationStrategy())
                .countries(splitCsv(e.getCountries()))
                .strictGeo(e.getStrictGeo() == null || e.getStrictGeo())
                .protocols(parseProtocols(e.getProtocols()))
                .anonymityLevels(parseAnonymity(e.getAnonymityLevels()))
                .maxLatencyMs(e.getMaxLatencyMs())
                .minSuccessRate(e.getMinSuccessRate())
                .build();
    }

    public ProxyConfigEntity toEntity(ProxyConfig c, UUID jobId) {
        ProxyConfig safe = c != null ? c : ProxyConfig.disabled();
        return ProxyConfigEntity.builder()
                .jobId(jobId)
                .enabled(Boolean.TRUE.equals(safe.enabled()))
                .rotationStrategy(safe.rotationStrategy() != null
                        ? safe.rotationStrategy()
                        : com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType.RANDOM)
                .countries(joinCsv(safe.countries()))
                .strictGeo(safe.isStrictGeo())
                .protocols(joinEnums(safe.protocols()))
                .anonymityLevels(joinEnums(safe.anonymityLevels()))
                .maxLatencyMs(safe.maxLatencyMs())
                .minSuccessRate(safe.minSuccessRate())
                .build();
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> String joinEnums(List<E> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static List<ProxyProtocol> parseProtocols(String csv) {
        List<String> parts = splitCsv(csv);
        if (parts == null) return Collections.emptyList();
        return parts.stream().map(s -> {
            try {
                return ProxyProtocol.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static List<ProxyAnonymity> parseAnonymity(String csv) {
        List<String> parts = splitCsv(csv);
        if (parts == null) return Collections.emptyList();
        return parts.stream().map(s -> {
            try {
                return ProxyAnonymity.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
    }
}
