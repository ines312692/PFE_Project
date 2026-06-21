package com.elyadata.feature.proxy.application.dto;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import lombok.Builder;

import java.time.Instant;

@Builder
public record AntiBotStatusDto(
        AntiBotProvider provider,
        ProtectionStatus status,
        Boolean detected,
        Boolean blocked,
        Integer lastStatusCode,
        Instant lastCheckedAt,
        String lastError
) {
}