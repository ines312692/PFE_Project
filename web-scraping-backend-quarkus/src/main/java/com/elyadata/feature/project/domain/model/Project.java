package com.elyadata.feature.project.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Project(
        UUID id,
        String name,
        String description,
        String color,
        ProjectStatus status,
        UUID ownerId,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {}