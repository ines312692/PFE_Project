package com.elyadata.feature.scraper.antibot.captcha.cache;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;

import java.time.Instant;
import java.util.Optional;


public interface CaptchaTokenPort {


    Optional<CachedToken> find(String domain, CaptchaType type);


    void put(String domain, CaptchaType type, String token, Instant expiresAt);


    void invalidate(String domain, CaptchaType type);

    record CachedToken(String token, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}