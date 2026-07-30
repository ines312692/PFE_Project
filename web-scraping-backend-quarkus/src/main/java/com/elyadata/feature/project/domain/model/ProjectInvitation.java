package com.elyadata.feature.project.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProjectInvitation(
        UUID id,
        UUID projectId,
        String projectName,
        UUID invitedBy,
        String invitedByUsername,
        UUID invitedUser,
        ProjectRole role,
        InvitationStatus status,
        Instant createdAt,
        Instant respondedAt,
        Instant expiresAt
) {
}