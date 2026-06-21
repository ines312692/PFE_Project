package com.elyadata.feature.proxy.application.dto;

import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxySource;
import com.elyadata.feature.proxy.domain.model.ProxyStatus;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProxyResponse(
        UUID id,
        String host,
        Integer port,
        ProxyProtocol protocol,
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
        Instant createdAt,
        Instant updatedAt,
        Map<AntiBotProvider, AntiBotStatusDto> antiBotStatuses
) {
}