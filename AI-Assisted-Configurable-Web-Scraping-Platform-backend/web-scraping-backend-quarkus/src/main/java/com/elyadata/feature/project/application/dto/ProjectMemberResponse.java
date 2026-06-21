package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.ProjectRole;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        String username,
        String email,
        ProjectRole role,
        Instant joinedAt
) {}