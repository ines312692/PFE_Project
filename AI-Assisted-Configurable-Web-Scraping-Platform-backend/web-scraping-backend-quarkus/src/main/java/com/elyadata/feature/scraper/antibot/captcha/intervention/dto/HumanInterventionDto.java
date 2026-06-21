package com.elyadata.feature.scraper.antibot.captcha.intervention.dto;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record HumanInterventionDto(
        UUID jobId,
        String pageUrl,
        CaptchaType captchaType,
        String siteKey,
        String screenshotBase64,
        Instant requestedAt,
        Instant expiresAt
) {
    public static HumanInterventionDto from(HumanInterventionRequest req) {
        return HumanInterventionDto.builder()
                .jobId(req.getJobId())
                .pageUrl(req.getPageUrl())
                .captchaType(req.getCaptchaType())
                .siteKey(req.getSiteKey())
                .screenshotBase64(req.getScreenshotBase64())
                .requestedAt(req.getRequestedAt())
                .expiresAt(req.getExpiresAt())
                .build();
    }
}