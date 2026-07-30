package com.elyadata.feature.schema.application.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SchemaValidationResponse(
        boolean success,
        String errorMessage,
        int totalFields,
        int validFields,
        int brokenFields,
        List<FieldValidationResult> results
) {

    public enum FieldStatus {OK, BROKEN, SUGGESTED}

    @Builder
    public record FieldValidationResult(
            String fieldName,
            String currentSelector,
            FieldStatus status,
            int matchCount,
            List<String> previewValues,
            String suggestedSelector,
            Double suggestionConfidence,
            String suggestionRationale,
            String errorReason
    ) {
    }

    public static SchemaValidationResponse error(String message) {
        return SchemaValidationResponse.builder()
                .success(false)
                .errorMessage(message)
                .results(List.of())
                .build();
    }
}