package com.elyadata.feature.proxy.infrastructure.entity;

import com.elyadata.feature.proxy.domain.model.*;
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
@Table(name = "proxies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProxyProtocol protocol;

    @Column
    private String username;

    @Column
    private String password;

    @Column(length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProxyAnonymity anonymity = ProxyAnonymity.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProxySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProxyStatus status = ProxyStatus.ACTIVE;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "success_rate", nullable = false)
    @Builder.Default
    private Double successRate = 0.0;

    @Column(name = "total_uses", nullable = false)
    @Builder.Default
    private Long totalUses = 0L;

    @Column(name = "successful_uses", nullable = false)
    @Builder.Default
    private Long successfulUses = 0L;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
