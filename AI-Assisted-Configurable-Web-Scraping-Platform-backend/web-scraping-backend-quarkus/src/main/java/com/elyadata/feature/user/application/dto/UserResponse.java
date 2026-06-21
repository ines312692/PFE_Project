package com.elyadata.feature.user.application.dto;

import com.elyadata.feature.user.domain.model.UserRole;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record UserResponse(
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