package com.elyadata.common.llm.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_calls")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallEntity {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false, length = 60)
    private String feature;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "prompt_chars", nullable = false)
    private int promptChars;

    @Column(name = "response_chars", nullable = false)
    private int responseChars;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "estimated_cost", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
