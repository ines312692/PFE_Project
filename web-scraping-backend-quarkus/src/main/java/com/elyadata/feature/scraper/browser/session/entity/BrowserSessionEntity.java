package com.elyadata.feature.scraper.browser.session.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "browser_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "domain", nullable = false, unique = true, length = 255)
    private String domain;

    @Column(name = "storage_state_enc", nullable = false, columnDefinition = "TEXT")
    private String storageStateEnc;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}