package com.elyadata.feature.audit.infrastructure.entity;

import com.elyadata.feature.audit.domain.model.AuditAction;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.model.AuditResource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_occurred_at", columnList = "occurred_at DESC"),
        @Index(name = "idx_audit_logs_user_id", columnList = "user_id, occurred_at DESC"),
        @Index(name = "idx_audit_logs_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_audit_logs_outcome", columnList = "outcome, occurred_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "user_role", length = 30)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 50, nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 50, nullable = false)
    private AuditResource resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "endpoint", length = 500)
    private String endpoint;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20, nullable = false)
    private AuditOutcome outcome;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}