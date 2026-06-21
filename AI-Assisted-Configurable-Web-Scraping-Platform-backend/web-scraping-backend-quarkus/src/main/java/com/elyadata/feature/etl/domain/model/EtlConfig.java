package com.elyadata.feature.etl.domain.model;

import java.util.List;

public record EtlConfig(List<EtlStepConfig> steps) {

    public static EtlConfig empty() {
        return new EtlConfig(List.of());
    }

    public boolean hasSteps() {
        return steps != null && !steps.isEmpty();
    }
}
