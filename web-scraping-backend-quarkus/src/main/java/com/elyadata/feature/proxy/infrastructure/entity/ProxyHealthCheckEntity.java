package com.elyadata.feature.proxy.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proxy_health_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyHealthCheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proxy_id", nullable = false)
    private UUID proxyId;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "checked_at", nullable = false)
    @Builder.Default
    private Instant checkedAt = Instant.now();
}