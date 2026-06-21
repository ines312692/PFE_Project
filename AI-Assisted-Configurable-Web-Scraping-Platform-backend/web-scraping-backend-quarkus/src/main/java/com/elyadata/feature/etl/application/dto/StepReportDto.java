package com.elyadata.feature.etl.application.dto;

public record StepReportDto(
        String step,
        int inputCount,
        int outputCount,
        boolean success,
        String error
) {
}