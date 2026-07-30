package com.elyadata.feature.scraper.site.model;

import lombok.Builder;

import java.time.Instant;


@Builder
public record OriginIpCandidate(
        String ip,
        Instant firstSeen,
        Instant lastSeen,
        String discoverySource,
        ValidationStatus validationStatus,
        Integer validationHttpStatus,
        Integer validationLatencyMs,
        double confidence
) {
    public enum ValidationStatus {
        NOT_TESTED,
        REACHABLE,
        UNREACHABLE,
        TLS_ERROR,
        TIMEOUT,
        WRONG_CONTENT
    }

    public boolean isUsable() {
        return validationStatus == ValidationStatus.REACHABLE;
    }
}