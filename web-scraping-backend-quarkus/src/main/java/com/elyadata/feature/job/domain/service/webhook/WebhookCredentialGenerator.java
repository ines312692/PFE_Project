package com.elyadata.feature.job.domain.service.webhook;

import com.elyadata.feature.job.domain.model.WebhookCredentials;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class WebhookCredentialGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final int SECRET_BYTES = 48;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public WebhookCredentials generate() {
        return WebhookCredentials.builder()
                .token(randomString(TOKEN_BYTES))
                .secret(randomString(SECRET_BYTES))
                .enabled(true)
                .build();
    }

    public WebhookCredentials regenerateSecret(String existingToken) {
        return WebhookCredentials.builder()
                .token(existingToken)
                .secret(randomString(SECRET_BYTES))
                .enabled(true)
                .build();
    }

    private String randomString(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return encoder.encodeToString(buf);
    }
}