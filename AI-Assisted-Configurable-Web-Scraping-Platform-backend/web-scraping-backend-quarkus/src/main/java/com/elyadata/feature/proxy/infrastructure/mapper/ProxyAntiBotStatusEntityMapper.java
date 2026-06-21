package com.elyadata.feature.proxy.infrastructure.mapper;

import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyAntiBotStatusEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProxyAntiBotStatusEntityMapper {

    public ProxyAntiBotStatus toDomain(ProxyAntiBotStatusEntity e) {
        return ProxyAntiBotStatus.builder()
                .proxyId(e.getProxyId())
                .provider(e.getProvider())
                .status(e.getStatus())
                .detected(e.getDetected())
                .blocked(e.getBlocked())
                .lastStatusCode(e.getLastStatusCode())
                .lastCheckedAt(e.getLastCheckedAt())
                .lastError(e.getLastError())
                .build();
    }

    public ProxyAntiBotStatusEntity toEntity(ProxyAntiBotStatus d) {
        return ProxyAntiBotStatusEntity.builder()
                .proxyId(d.proxyId())
                .provider(d.provider())
                .status(d.status())
                .detected(d.detected())
                .blocked(d.blocked())
                .lastStatusCode(d.lastStatusCode())
                .lastCheckedAt(d.lastCheckedAt())
                .lastError(d.lastError())
                .build();
    }
}