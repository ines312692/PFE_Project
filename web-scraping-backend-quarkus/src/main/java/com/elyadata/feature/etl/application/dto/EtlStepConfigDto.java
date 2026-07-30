package com.elyadata.feature.etl.application.dto;

import com.elyadata.feature.etl.domain.model.EtlStepType;

import java.util.Map;

public record EtlStepConfigDto(EtlStepType type, Map<String, Object> params) {
}