package com.elyadata.feature.job.application.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ActionPlanResponse(
        List<PlannedActionDto> actions,
        String reasoning,
        String modelUsed,
        boolean groundedOnHtml,
        boolean success,
        String errorMessage
) {
    public static ActionPlanResponse ok(List<PlannedActionDto> actions,
                                        String reasoning,
                                        String modelUsed,
                                        boolean groundedOnHtml) {
        return ActionPlanResponse.builder()
                .actions(actions)
                .reasoning(reasoning)
                .modelUsed(modelUsed)
                .groundedOnHtml(groundedOnHtml)
                .success(true)
                .build();
    }

    public static ActionPlanResponse error(String errorMessage) {
        return ActionPlanResponse.builder()
                .actions(List.of())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}