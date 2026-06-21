package com.elyadata.common.exceptions;

import lombok.Builder;

@Builder
public record SubError(
        String field,
        String message,
        String rejectedValue,
        String errorCode
) {
}