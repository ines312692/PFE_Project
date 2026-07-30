package com.elyadata.feature.proxy.infrastructure.entity;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "proxy_antibot_status")
@IdClass(ProxyAntiBotStatusEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyAntiBotStatusEntity {

    @Id
    @Column(name = "proxy_id", nullable = false)
    private UUID proxyId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private AntiBotProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ProtectionStatus status;

    @Column(name = "detected")
    private Boolean detected;

    @Column(name = "blocked")
    private Boolean blocked;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID proxyId;
        private AntiBotProvider provider;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(proxyId, pk.proxyId) && provider == pk.provider;
        }

        @Override
        public int hashCode() {
            return Objects.hash(proxyId, provider);
        }
    }
}