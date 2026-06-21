package com.elyadata.feature.etl.domain.model;

import java.util.List;


public record ValidationRule(
        String field,
        Boolean required,
        String type,
        String regex,
        Double min,
        Double max,
        List<String> allowedValues,
        String crossField,
        String crossOp
) {

    public boolean hasTypeCheck() {
        return type != null && !type.isBlank();
    }

    public boolean hasRegexCheck() {
        return regex != null && !regex.isBlank();
    }

    public boolean hasRangeCheck() {
        return min != null || max != null;
    }

    public boolean hasEnumCheck() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    public boolean hasCrossCheck() {
        return crossField != null && crossOp != null;
    }

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }
}