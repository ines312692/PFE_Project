package com.elyadata.feature.schema.domain.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
public record SchemaField(
        String name,
        String selector,
        SchemaFieldType type,
        String attribute,
        Boolean required
) {
}