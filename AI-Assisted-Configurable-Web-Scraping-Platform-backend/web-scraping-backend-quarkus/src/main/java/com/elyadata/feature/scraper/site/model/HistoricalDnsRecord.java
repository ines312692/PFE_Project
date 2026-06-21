package com.elyadata.feature.scraper.site.model;

import java.time.Instant;


public record HistoricalDnsRecord(
        String ip,
        Instant firstSeen,
        Instant lastSeen,
        String source
) {
    public HistoricalDnsRecord {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}