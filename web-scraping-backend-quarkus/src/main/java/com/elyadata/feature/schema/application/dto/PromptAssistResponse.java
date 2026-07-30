package com.elyadata.feature.schema.application.dto;

import lombok.Builder;


@Builder
public record PromptAssistResponse(
        String suggestedPrompt,
        String suggestedOutputFormat,
        String reasoning,
        String modelUsed,
        boolean success,
        String errorMessage
) {
    public static PromptAssistResponse ok(String prompt,
                                          String outputFormat,
                                          String reasoning,
                                          String modelUsed) {
        return PromptAssistResponse.builder()
                .suggestedPrompt(prompt)
                .suggestedOutputFormat(outputFormat)
                .reasoning(reasoning)
                .modelUsed(modelUsed)
                .success(true)
                .build();
    }

    public static PromptAssistResponse error(String errorMessage) {
        return PromptAssistResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
