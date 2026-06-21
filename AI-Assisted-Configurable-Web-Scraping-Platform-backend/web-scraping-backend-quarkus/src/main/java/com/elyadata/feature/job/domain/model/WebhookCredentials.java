package com.elyadata.feature.job.domain.model;

import lombok.Builder;


@Builder(toBuilder = true)
public record WebhookCredentials(
        String token,
        String secret,
        boolean enabled
) {
    public static WebhookCredentials disabled() {
        return WebhookCredentials.builder().enabled(false).build();
    }
}