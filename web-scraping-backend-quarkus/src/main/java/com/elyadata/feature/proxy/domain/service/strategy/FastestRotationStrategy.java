package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FastestRotationStrategy implements ProxyRotationStrategy {

    @Override
    public ProxyRotationStrategyType type() {
        return ProxyRotationStrategyType.FASTEST;
    }

    @Override
    public String orderByClause() {
        return "latency_ms ASC NULLS LAST, success_rate DESC";
    }
}
