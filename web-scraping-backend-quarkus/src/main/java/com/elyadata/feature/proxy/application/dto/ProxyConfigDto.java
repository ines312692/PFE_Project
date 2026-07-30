package com.elyadata.feature.proxy.application.dto;

import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;

import java.util.List;

public record ProxyConfigDto(
        Boolean enabled,
        ProxyRotationStrategyType rotationStrategy,
        List<String> countries,
        List<ProxyProtocol> protocols,
        List<ProxyAnonymity> anonymityLevels,
        Integer maxLatencyMs,
        Double minSuccessRate
) {
}
