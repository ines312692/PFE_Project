package com.elyadata.feature.scraper.antibot.captcha.cache;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Slf4j
@ApplicationScoped
public class CaptchaTokenCache implements CaptchaTokenPort {

    private final ConcurrentMap<String, CachedToken> store = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedToken> find(String domain, CaptchaType type) {
        String key = key(domain, type);
        CachedToken cached = store.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.isExpired()) {
            store.remove(key);
            log.debug("[CAPTCHA-CACHE] Evicted expired token for {} (key={})", domain, key);
            return Optional.empty();
        }
        log.info("[CAPTCHA-CACHE] HIT for domain={} type={}", domain, type);
        return Optional.of(cached);
    }

    @Override
    public void put(String domain, CaptchaType type, String token, Instant expiresAt) {
        store.put(key(domain, type), new CachedToken(token, expiresAt));
        log.info("[CAPTCHA-CACHE] Stored token for domain={} type={} expiresAt={}",
                domain, type, expiresAt);
    }

    @Override
    public void invalidate(String domain, CaptchaType type) {
        if (store.remove(key(domain, type)) != null) {
            log.info("[CAPTCHA-CACHE] Invalidated token for domain={} type={}", domain, type);
        }
    }

    private static String key(String domain, CaptchaType type) {
        return domain.toLowerCase() + "|" + type.name();
    }
}