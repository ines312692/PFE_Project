package com.elyadata.feature.proxy.domain.model.antibot;

import java.time.Instant;

public record DetectionResult(
        AntiBotProvider provider,
        ProtectionStatus status,
        Integer httpStatusCode,
        boolean detected,
        boolean blocked,
        Integer latencyMs,
        Instant checkedAt,
        String errorMessage
) {
    public static DetectionResult untested(AntiBotProvider provider, String reason) {
        return new DetectionResult(provider, ProtectionStatus.UNTESTED,
                null, false, false, null, Instant.now(), reason);
    }

    public static DetectionResult unreachable(AntiBotProvider provider, String reason) {
        return new DetectionResult(provider, ProtectionStatus.UNREACHABLE,
                null, false, false, null, Instant.now(), reason);
    }

    public boolean isUsable() {
        return status == ProtectionStatus.CLEAN || status == ProtectionStatus.CHALLENGED;
    }
}