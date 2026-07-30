package com.elyadata.feature.project.application.dto;

import com.elyadata.feature.project.domain.model.ProjectStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String color,
        ProjectStatus status,
        UUID ownerId,
        String ownerUsername,
        int memberCount,
        int jobCount,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {}