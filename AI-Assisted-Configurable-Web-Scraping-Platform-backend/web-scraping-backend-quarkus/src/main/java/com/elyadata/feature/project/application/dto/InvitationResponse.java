package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.InvitationStatus;
import com.elyadata.feature.project.domain.model.ProjectRole;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InvitationResponse(
        UUID id,
        UUID projectId,
        String projectName,
        UUID invitedBy,
        String invitedByUsername,
        ProjectRole role,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt
) {
}