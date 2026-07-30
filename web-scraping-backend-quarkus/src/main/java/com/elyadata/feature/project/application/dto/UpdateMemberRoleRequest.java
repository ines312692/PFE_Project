package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(

        @NotNull
        ProjectRole role
) {}