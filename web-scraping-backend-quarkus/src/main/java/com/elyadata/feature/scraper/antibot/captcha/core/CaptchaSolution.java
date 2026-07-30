package com.elyadata.feature.scraper.antibot.captcha.core;

import lombok.Builder;

import java.time.Instant;


@Builder
public record CaptchaSolution(
        String token,
        CaptchaProvider provider,
        boolean success,
        long solvedInMs,
        Double costUsd,
        String errorMessage,
        Instant solvedAt
) {
    public static CaptchaSolution success(String token,
                                          CaptchaProvider provider,
                                          long solvedInMs,
                                          Double costUsd) {
        return CaptchaSolution.builder()
                .token(token)
                .provider(provider)
                .success(true)
                .solvedInMs(solvedInMs)
                .costUsd(costUsd)
                .solvedAt(Instant.now())
                .build();
    }

    public static CaptchaSolution failure(CaptchaProvider provider,
                                          long solvedInMs,
                                          String errorMessage) {
        return CaptchaSolution.builder()
                .provider(provider)
                .success(false)
                .solvedInMs(solvedInMs)
                .errorMessage(errorMessage)
                .solvedAt(Instant.now())
                .build();
    }
}