package com.elyadata.feature.etl.domain.model;

import java.util.Map;


public record FieldQuality(
        String field,
        int totalChecks,
        int passed,
        int failed,
        int missing,
        double completenessRate,
        double validityRate,
        Map<String, Integer> failuresByRule
) {

    public static FieldQuality forEmpty(String field) {
        return new FieldQuality(field, 0, 0, 0, 0, 1.0, 1.0, Map.of());
    }
}