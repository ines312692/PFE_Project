package com.elyadata.feature.scraper.antibot.captcha.cache;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CaptchaTokenCache")
class CaptchaTokenCacheTest {

    private CaptchaTokenCache cache;

    @BeforeEach
    void setUp() {
        cache = new CaptchaTokenCache();
    }


    @Test
    @DisplayName("find() on empty cache returns empty")
    void find_emptyCache_returnsEmpty() {
        assertThat(cache.find("example.com", CaptchaType.RECAPTCHA_V2)).isEmpty();
    }


    @Test
    @DisplayName("find() after put() returns the stored token")
    void putThenFind_returnsToken() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("example.com", CaptchaType.HCAPTCHA, "token-abc", future);

        Optional<CaptchaTokenPort.CachedToken> result =
                cache.find("example.com", CaptchaType.HCAPTCHA);

        assertThat(result).isPresent();
        assertThat(result.get().token()).isEqualTo("token-abc");
    }

    @Test
    @DisplayName("find() is type-specific (different type returns empty)")
    void find_differentType_returnsEmpty() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("example.com", CaptchaType.RECAPTCHA_V2, "token-v2", future);

        assertThat(cache.find("example.com", CaptchaType.HCAPTCHA)).isEmpty();
    }

    @Test
    @DisplayName("find() is domain-specific (different domain returns empty)")
    void find_differentDomain_returnsEmpty() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("site-a.com", CaptchaType.TURNSTILE, "token-a", future);

        assertThat(cache.find("site-b.com", CaptchaType.TURNSTILE)).isEmpty();
    }


    @Test
    @DisplayName("find() returns empty for expired token")
    void find_expiredToken_returnsEmpty() {
        Instant past = Instant.now().minusSeconds(1);
        cache.put("example.com", CaptchaType.RECAPTCHA_V3, "old-token", past);

        assertThat(cache.find("example.com", CaptchaType.RECAPTCHA_V3)).isEmpty();
    }

    @Test
    @DisplayName("expired token is evicted on find() (subsequent find still empty)")
    void expiredToken_evictedAfterFind() {
        Instant past = Instant.now().minusSeconds(1);
        cache.put("domain.com", CaptchaType.TURNSTILE, "tok", past);

        cache.find("domain.com", CaptchaType.TURNSTILE); // triggers eviction
        assertThat(cache.find("domain.com", CaptchaType.TURNSTILE)).isEmpty();
    }


    @Test
    @DisplayName("invalidate() removes a stored token")
    void invalidate_removesToken() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("site.com", CaptchaType.HCAPTCHA, "tok", future);
        cache.invalidate("site.com", CaptchaType.HCAPTCHA);

        assertThat(cache.find("site.com", CaptchaType.HCAPTCHA)).isEmpty();
    }

    @Test
    @DisplayName("invalidate() on absent key does not throw")
    void invalidate_absentKey_noException() {
        assertThatCode(() -> cache.invalidate("never-stored.com", CaptchaType.GEETEST))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invalidate() is type-specific (other type still present)")
    void invalidate_typeSpecific() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("site.com", CaptchaType.HCAPTCHA, "tok-h", future);
        cache.put("site.com", CaptchaType.RECAPTCHA_V2, "tok-r", future);

        cache.invalidate("site.com", CaptchaType.HCAPTCHA);

        assertThat(cache.find("site.com", CaptchaType.HCAPTCHA)).isEmpty();
        assertThat(cache.find("site.com", CaptchaType.RECAPTCHA_V2)).isPresent();
    }


    @Test
    @DisplayName("put() overwrites an existing token for the same domain+type")
    void put_overwritesExistingToken() {
        Instant future = Instant.now().plusSeconds(300);
        cache.put("site.com", CaptchaType.TURNSTILE, "first", future);
        cache.put("site.com", CaptchaType.TURNSTILE, "second", future);

        Optional<CaptchaTokenPort.CachedToken> result =
                cache.find("site.com", CaptchaType.TURNSTILE);
        assertThat(result.get().token()).isEqualTo("second");
    }


    @Nested
    @DisplayName("CachedToken.isExpired()")
    class CachedTokenExpiry {

        @Test
        @DisplayName("future expiresAt → not expired")
        void future_notExpired() {
            var token = new CaptchaTokenPort.CachedToken("t", Instant.now().plusSeconds(60));
            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("past expiresAt → expired")
        void past_expired() {
            var token = new CaptchaTokenPort.CachedToken("t", Instant.now().minusSeconds(1));
            assertThat(token.isExpired()).isTrue();
        }
    }
}