package com.elyadata.common.exceptions;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Set;

@Builder
public record ErrorResponse(
        String path,
        String message,
        Set<SubError> subErrors,
        int statusCode,
        int errorCode,
        OffsetDateTime timestamp
) {
}