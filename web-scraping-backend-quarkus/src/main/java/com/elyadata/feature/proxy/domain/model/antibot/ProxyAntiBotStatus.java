package com.elyadata.feature.proxy.domain.model.antibot;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ProxyAntiBotStatus(
        UUID proxyId,
        AntiBotProvider provider,
        ProtectionStatus status,
        Boolean detected,
        Boolean blocked,
        Integer lastStatusCode,
        Instant lastCheckedAt,
        String lastError
) {
    public static ProxyAntiBotStatus fromDetection(UUID proxyId, DetectionResult r) {
        return ProxyAntiBotStatus.builder()
                .proxyId(proxyId)
                .provider(r.provider())
                .status(r.status())
                .detected(r.detected())
                .blocked(r.blocked())
                .lastStatusCode(r.httpStatusCode())
                .lastCheckedAt(r.checkedAt())
                .lastError(r.errorMessage())
                .build();
    }
}