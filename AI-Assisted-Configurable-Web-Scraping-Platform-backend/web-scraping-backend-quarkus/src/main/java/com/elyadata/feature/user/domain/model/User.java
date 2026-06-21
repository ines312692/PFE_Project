package com.elyadata.feature.user.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record User(
        UUID id,
        String username,
        String email,
        UserRole role,
        Boolean enabled,
        String avatarUrl,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {}