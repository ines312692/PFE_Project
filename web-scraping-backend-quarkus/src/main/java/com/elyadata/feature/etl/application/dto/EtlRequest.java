package com.elyadata.feature.etl.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EtlRequest(
        @NotNull @Valid List<EtlStepConfigDto> steps,
        String label,
        Integer fromVersion
) {
    public EtlRequest(List<EtlStepConfigDto> steps) {
        this(steps, null, null);
    }
}