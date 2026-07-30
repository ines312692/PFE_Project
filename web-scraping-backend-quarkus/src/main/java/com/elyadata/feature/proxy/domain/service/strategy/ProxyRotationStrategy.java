package com.elyadata.feature.proxy.domain.service.strategy;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;

public interface ProxyRotationStrategy {

    ProxyRotationStrategyType type();

    String orderByClause();
}