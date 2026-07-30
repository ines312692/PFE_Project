package com.elyadata.feature.job.infrastructure.entity;

import com.elyadata.feature.job.domain.model.WebhookDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WebhookDeliveryStatus status;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "duration_ms")
    private Integer durationMs;
}