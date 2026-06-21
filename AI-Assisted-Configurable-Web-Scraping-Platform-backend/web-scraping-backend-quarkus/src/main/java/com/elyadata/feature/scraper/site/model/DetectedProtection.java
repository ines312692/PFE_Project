package com.elyadata.feature.scraper.site.model;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;

public record DetectedProtection(
        AntiBotProvider provider,
        double confidence,
        String evidence
) {
    public DetectedProtection {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in [0.0, 1.0], got " + confidence);
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("evidence must not be blank");
        }
    }
}