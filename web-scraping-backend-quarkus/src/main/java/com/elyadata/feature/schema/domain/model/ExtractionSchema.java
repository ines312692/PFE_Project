package com.elyadata.feature.schema.domain.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.UUID;

@Builder
@Jacksonized
public record ExtractionSchema(
        UUID id,
        UUID jobId,
        List<SchemaField> fields,
        String llmPrompt,
        LlmModel llmModel,
        String llmOutputFormat,
        String regexPattern,
        String regexGroup
) {
}