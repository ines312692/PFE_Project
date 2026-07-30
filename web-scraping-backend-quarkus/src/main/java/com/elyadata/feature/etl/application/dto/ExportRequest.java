package com.elyadata.feature.etl.application.dto;

import java.util.Map;

public record ExportRequest(
        ExportFormat format,
        ExportDestination destination,
        Map<String, Object> destinationConfig,
        Integer fromVersion,
        Object o) {
}