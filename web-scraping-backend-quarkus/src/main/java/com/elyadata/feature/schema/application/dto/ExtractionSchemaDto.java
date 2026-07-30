package com.elyadata.feature.schema.application.dto;

import com.elyadata.feature.schema.domain.model.LlmModel;
import com.elyadata.feature.schema.domain.model.SchemaField;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ExtractionSchemaDto(
        UUID id,
        List<SchemaField> fields,
        String llmPrompt,
        LlmModel llmModel,
        String llmOutputFormat,
        String regexPattern,
        String regexGroup
) {
}