package com.elyadata.feature.proxy.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Proxy(
        UUID id,
        String host,
        Integer port,
        ProxyProtocol protocol,
        String username,
        String password,
        String country,
        ProxyAnonymity anonymity,
        ProxySource source,
        ProxyStatus status,
        Integer latencyMs,
        Double successRate,
        Long totalUses,
        Long successfulUses,
        Instant lastCheckedAt,
        Instant lastUsedAt,
        Instant leasedUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public String endpoint() {
        return host + ":" + port;
    }

    public String url() {
        return protocol.scheme() + "://" + endpoint();
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }
}