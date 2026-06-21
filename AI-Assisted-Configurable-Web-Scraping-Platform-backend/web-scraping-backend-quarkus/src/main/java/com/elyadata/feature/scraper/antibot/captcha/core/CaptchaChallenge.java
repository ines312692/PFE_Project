package com.elyadata.feature.scraper.antibot.captcha.core;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CaptchaChallenge(
        CaptchaType type,
        String siteKey,
        String pageUrl,
        UUID jobId,
        boolean invisible,
        String imageBase64,
        String inputSelector,
        String action,
        Double minScore
) {
    public CaptchaChallenge {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (pageUrl == null || pageUrl.isBlank()) {
            throw new IllegalArgumentException("pageUrl required");
        }
        requireIfNeeded(type, CaptchaType.Field.SITE_KEY, siteKey);
        requireIfNeeded(type, CaptchaType.Field.ACTION, action);
        requireIfNeeded(type, CaptchaType.Field.IMAGE, imageBase64);
    }

    private static void requireIfNeeded(CaptchaType type,
                                        CaptchaType.Field field,
                                        String value) {
        if (type.getRequiredFields().contains(field)
                && (value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " required for " + type);
        }
    }
}