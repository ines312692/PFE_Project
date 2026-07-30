package com.elyadata.feature.proxy.infrastructure.entity;

import com.elyadata.feature.proxy.domain.model.ProxyRotationStrategyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proxy_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "rotation_strategy", nullable = false, length = 30)
    @Builder.Default
    private ProxyRotationStrategyType rotationStrategy = ProxyRotationStrategyType.RANDOM;

    @Column(columnDefinition = "TEXT")
    private String countries;

    @Column(columnDefinition = "TEXT")
    private String protocols;

    @Column(name = "anonymity_levels", columnDefinition = "TEXT")
    private String anonymityLevels;

    @Column(name = "max_latency_ms")
    private Integer maxLatencyMs;

    @Column(name = "min_success_rate")
    private Double minSuccessRate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "strict_geo", nullable = false)
    @Builder.Default
    private Boolean strictGeo = true;
}
