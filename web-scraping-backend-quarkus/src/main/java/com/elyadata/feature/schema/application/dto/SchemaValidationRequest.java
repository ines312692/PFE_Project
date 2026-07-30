package com.elyadata.feature.schema.application.dto;

import com.elyadata.feature.schema.domain.model.SchemaFieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SchemaValidationRequest(
        @NotBlank @Size(max = 2_048) String targetUrl,
        @NotEmpty @Valid List<FieldToValidate> fields
) {
    public record FieldToValidate(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1_000) String selector,
            @NotNull SchemaFieldType type,
            @Size(max = 100) String attribute
    ) {
    }
}