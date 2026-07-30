package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LeastUsedRotationStrategy implements ProxyRotationStrategy {

    @Override
    public ProxyRotationStrategyType type() {
        return ProxyRotationStrategyType.LEAST_USED;
    }

    @Override
    public String orderByClause() {
        return "total_uses ASC, RANDOM()";
    }
}
