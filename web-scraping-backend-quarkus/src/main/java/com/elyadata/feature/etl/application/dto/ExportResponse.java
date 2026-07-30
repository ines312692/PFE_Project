package com.elyadata.feature.etl.application.dto;

public record ExportResponse(
        String downloadUrl,
        int recordsExported,
        long durationMs,
        String message
) {
}


