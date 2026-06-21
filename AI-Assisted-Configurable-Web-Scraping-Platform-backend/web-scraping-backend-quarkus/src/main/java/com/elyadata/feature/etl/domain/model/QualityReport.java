package com.elyadata.feature.etl.domain.model;

import java.util.List;
import java.util.Map;


public record QualityReport(
        int totalRecords,
        int validRecords,
        int invalidRecords,
        double validityRate,
        Map<String, FieldQuality> byField,
        List<RuleViolation> topViolations,
        long durationMs
) {

    public static QualityReport empty() {
        return new QualityReport(0, 0, 0, 1.0, Map.of(), List.of(), 0L);
    }

    public boolean hasIssues() {
        return invalidRecords > 0;
    }
}