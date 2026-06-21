package com.elyadata.feature.etl.domain.model;

import java.util.Map;

public record EtlStepConfig(
        EtlStepType type,
        Map<String, Object> params
) {
}
