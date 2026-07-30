package com.elyadata.feature.job.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record WebhookDelivery(
        UUID id,
        UUID jobId,
        Instant receivedAt,
        WebhookDeliveryStatus status,
        int httpStatus,
        String sourceIp,
        String userAgent,
        boolean signatureValid,
        String errorMessage,
        String payload,
        Integer durationMs
) {
}