package com.elyadata.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageMeta(
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext,
        boolean hasPrevious
) {
}