package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class RoundRobinRotationStrategy implements ProxyRotationStrategy {

    @Override
    public ProxyRotationStrategyType type() {
        return ProxyRotationStrategyType.ROUND_ROBIN;
    }

    @Override
    public String orderByClause() {
        return "last_used_at ASC NULLS FIRST";
    }
}
