package com.elyadata.feature.notification.application.dto;

import com.elyadata.feature.notification.domain.model.NotificationType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record NotificationResponse(
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