package com.elyadata.feature.notification.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Notification(
        UUID id,
        UUID userId,
        UUID jobId,
        NotificationType type,
        String title,
        String message,
        boolean read,
        Instant createdAt,
        String referenceId
) {
}