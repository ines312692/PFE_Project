package com.elyadata.feature.project.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProjectMember(
        UUID id,
        UUID projectId,
        UUID userId,
        String username,
        String email,
        ProjectRole role,
        Instant joinedAt
) {}