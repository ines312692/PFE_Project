package com.elyadata.feature.proxy.domain.model;

import lombok.Builder;
import java.util.List;

@Builder(toBuilder = true)
public record ProxyConfig(
        Boolean enabled,
        ProxyRotationStrategyType rotationStrategy,
        List<String> countries,
        Boolean strictGeo,
        List<ProxyProtocol> protocols,
        List<ProxyAnonymity> anonymityLevels,
        Integer maxLatencyMs,
        Double minSuccessRate
) {
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isStrictGeo() {
        return !Boolean.FALSE.equals(strictGeo);
    }

    public boolean hasCountryFilter() {
        return countries != null && !countries.isEmpty();
    }

    public static ProxyConfig disabled() {
        return ProxyConfig.builder()
                .enabled(false)
                .rotationStrategy(ProxyRotationStrategyType.RANDOM)
                .strictGeo(true)
                .build();
    }
}