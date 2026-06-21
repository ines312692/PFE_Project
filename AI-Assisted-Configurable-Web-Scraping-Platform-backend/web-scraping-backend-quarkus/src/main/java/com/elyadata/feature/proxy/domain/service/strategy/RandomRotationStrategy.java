package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RandomRotationStrategy implements ProxyRotationStrategy {

    @Override
    public ProxyRotationStrategyType type() {
        return ProxyRotationStrategyType.RANDOM;
    }

    @Override
    public String orderByClause() {
        return "RANDOM()";
    }
}
