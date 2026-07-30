package com.elyadata.feature.scraper.browser.session;

import lombok.Builder;

import java.time.Instant;


@Builder(toBuilder = true)
public record SessionState(
        String domain,
        String storageState,
        Instant capturedAt,
        Instant expiresAt
) {
    public boolean isEmpty() {
        return storageState == null || storageState.isBlank();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !isEmpty() && !isExpired();
    }
}