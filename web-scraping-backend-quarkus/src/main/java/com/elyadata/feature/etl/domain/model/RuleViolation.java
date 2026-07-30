package com.elyadata.feature.etl.domain.model;


public record RuleViolation(
        int recordIndex,
        String field,
        String rule,
        Object actualValue,
        String message
) {
}