package com.elyadata.feature.job.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "retry_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "retry_initial_delay_ms")
    private Long retryInitialDelayMs;

    @Column(name = "retry_backoff_multiplier")
    private Double retryBackoffMultiplier;

    @Column(name = "retry_max_delay_ms")
    private Long retryMaxDelayMs;

    @Column(name = "circuit_breaker_enabled")
    private Boolean circuitBreakerEnabled;

    @Column(name = "circuit_breaker_threshold")
    private Double circuitBreakerThreshold;

    @Column(name = "circuit_breaker_reset_seconds")
    private Integer circuitBreakerResetSeconds;
}