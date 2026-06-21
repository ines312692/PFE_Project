package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class ProxyRotationStrategyRegistry {

    @Inject
    Instance<ProxyRotationStrategy> strategies;

    private Map<ProxyRotationStrategyType, ProxyRotationStrategy> byType;

    @PostConstruct
    void init() {
        byType = new EnumMap<>(ProxyRotationStrategyType.class);
        for (ProxyRotationStrategy s : strategies) {
            byType.put(s.type(), s);
        }
        log.info("[PROXY-ROTATION] Registered {} strategies: {}", byType.size(), byType.keySet());
    }

    public ProxyRotationStrategy get(ProxyRotationStrategyType type) {
        ProxyRotationStrategyType resolved = type != null ? type : ProxyRotationStrategyType.RANDOM;
        ProxyRotationStrategy strategy = byType.get(resolved);
        if (strategy == null) {
            log.warn("[PROXY-ROTATION] Unknown strategy {}, falling back to RANDOM", resolved);
            return byType.get(ProxyRotationStrategyType.RANDOM);
        }
        return strategy;
    }
}
