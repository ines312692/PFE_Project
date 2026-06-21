package com.elyadata.feature.project.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RespondInvitationRequest(
        @NotBlank String action
) {}