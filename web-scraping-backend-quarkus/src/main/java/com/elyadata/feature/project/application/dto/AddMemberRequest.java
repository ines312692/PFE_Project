package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(

        @NotNull
        UUID userId,

        @NotNull
        ProjectRole role
) {}