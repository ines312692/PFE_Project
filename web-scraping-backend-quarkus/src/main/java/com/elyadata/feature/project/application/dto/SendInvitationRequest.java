package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record SendInvitationRequest(

        @NotBlank
        @Email
        String email,

        @NotNull
        ProjectRole role
) {
}