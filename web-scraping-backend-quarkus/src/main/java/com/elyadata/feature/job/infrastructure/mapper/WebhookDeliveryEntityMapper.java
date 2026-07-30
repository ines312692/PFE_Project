package com.elyadata.feature.job.infrastructure.mapper;

import com.elyadata.feature.job.domain.model.WebhookDelivery;
import com.elyadata.feature.job.infrastructure.entity.WebhookDeliveryEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebhookDeliveryEntityMapper {

    public WebhookDeliveryEntity toEntity(WebhookDelivery d) {
        return WebhookDeliveryEntity.builder()
                .id(d.id())
                .jobId(d.jobId())
                .receivedAt(d.receivedAt())
                .status(d.status())
                .httpStatus(d.httpStatus())
                .sourceIp(d.sourceIp())
                .userAgent(d.userAgent())
                .signatureValid(d.signatureValid())
                .errorMessage(d.errorMessage())
                .payload(d.payload())
                .durationMs(d.durationMs())
                .build();
    }

    public WebhookDelivery toDomain(WebhookDeliveryEntity e) {
        return WebhookDelivery.builder()
                .id(e.getId())
                .jobId(e.getJobId())
                .receivedAt(e.getReceivedAt())
                .status(e.getStatus())
                .httpStatus(e.getHttpStatus())
                .sourceIp(e.getSourceIp())
                .userAgent(e.getUserAgent())
                .signatureValid(e.isSignatureValid())
                .errorMessage(e.getErrorMessage())
                .payload(e.getPayload())
                .durationMs(e.getDurationMs())
                .build();
    }
}