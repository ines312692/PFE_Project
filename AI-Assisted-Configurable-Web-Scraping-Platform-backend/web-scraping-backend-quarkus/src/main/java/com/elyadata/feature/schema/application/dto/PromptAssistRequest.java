package com.elyadata.feature.schema.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;


public record PromptAssistRequest(
        @NotNull PromptAssistAction action,
        @Size(max = 2_000) String description,
        @Size(max = 2_000) String currentPrompt,
        @Size(max = 1_000) String currentOutputFormat,
        @Size(max = 2_048) String targetUrl,
        UUID jobId
) {
}
