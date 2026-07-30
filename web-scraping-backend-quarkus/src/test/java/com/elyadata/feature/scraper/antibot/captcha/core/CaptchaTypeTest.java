package com.elyadata.feature.scraper.antibot.captcha.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CaptchaType")
class CaptchaTypeTest {


    @Test
    @DisplayName("RECAPTCHA_V2 is token-based")
    void recaptchaV2_isTokenBased() {
        assertThat(CaptchaType.RECAPTCHA_V2.isTokenBased()).isTrue();
    }

    @Test
    @DisplayName("RECAPTCHA_V3 is token-based")
    void recaptchaV3_isTokenBased() {
        assertThat(CaptchaType.RECAPTCHA_V3.isTokenBased()).isTrue();
    }

    @Test
    @DisplayName("HCAPTCHA is token-based")
    void hcaptcha_isTokenBased() {
        assertThat(CaptchaType.HCAPTCHA.isTokenBased()).isTrue();
    }

    @Test
    @DisplayName("TURNSTILE is token-based")
    void turnstile_isTokenBased() {
        assertThat(CaptchaType.TURNSTILE.isTokenBased()).isTrue();
    }

    @Test
    @DisplayName("IMAGE_TEXT is NOT token-based")
    void imageText_notTokenBased() {
        assertThat(CaptchaType.IMAGE_TEXT.isTokenBased()).isFalse();
    }

    @Test
    @DisplayName("FUNCAPTCHA is NOT token-based")
    void funcaptcha_notTokenBased() {
        assertThat(CaptchaType.FUNCAPTCHA.isTokenBased()).isFalse();
    }

    @Test
    @DisplayName("ROTATE is NOT token-based")
    void rotate_notTokenBased() {
        assertThat(CaptchaType.ROTATE.isTokenBased()).isFalse();
    }

    @Test
    @DisplayName("GEETEST is NOT token-based")
    void geetest_notTokenBased() {
        assertThat(CaptchaType.GEETEST.isTokenBased()).isFalse();
    }

    @Test
    @DisplayName("UNKNOWN is NOT token-based")
    void unknown_notTokenBased() {
        assertThat(CaptchaType.UNKNOWN.isTokenBased()).isFalse();
    }


    @Test
    @DisplayName("RECAPTCHA_V2 requires SITE_KEY only")
    void recaptchaV2_requiresSiteKey() {
        assertThat(CaptchaType.RECAPTCHA_V2.getRequiredFields())
                .containsExactlyInAnyOrder(CaptchaType.Field.SITE_KEY);
    }

    @Test
    @DisplayName("RECAPTCHA_V3 requires SITE_KEY and ACTION")
    void recaptchaV3_requiresSiteKeyAndAction() {
        assertThat(CaptchaType.RECAPTCHA_V3.getRequiredFields())
                .containsExactlyInAnyOrder(CaptchaType.Field.SITE_KEY, CaptchaType.Field.ACTION);
    }

    @Test
    @DisplayName("HCAPTCHA requires SITE_KEY only")
    void hcaptcha_requiresSiteKey() {
        assertThat(CaptchaType.HCAPTCHA.getRequiredFields())
                .containsExactlyInAnyOrder(CaptchaType.Field.SITE_KEY);
    }

    @Test
    @DisplayName("TURNSTILE requires SITE_KEY only")
    void turnstile_requiresSiteKey() {
        assertThat(CaptchaType.TURNSTILE.getRequiredFields())
                .containsExactlyInAnyOrder(CaptchaType.Field.SITE_KEY);
    }

    @Test
    @DisplayName("IMAGE_TEXT requires IMAGE")
    void imageText_requiresImage() {
        assertThat(CaptchaType.IMAGE_TEXT.getRequiredFields())
                .containsExactlyInAnyOrder(CaptchaType.Field.IMAGE);
    }

    @ParameterizedTest
    @DisplayName("these types have no required fields")
    @EnumSource(value = CaptchaType.class,
            names = {"FUNCAPTCHA", "ROTATE", "GEETEST", "CLOUDFLARE_INTERSTITIAL", "CUSTOM", "UNKNOWN"})
    void noRequiredFields(CaptchaType type) {
        assertThat(type.getRequiredFields()).isEmpty();
    }


    @Test
    @DisplayName("CaptchaType has exactly 11 values")
    void enumSize() {
        assertThat(CaptchaType.values()).hasSize(11);
    }

    @ParameterizedTest
    @DisplayName("getRequiredFields() never returns null")
    @EnumSource(CaptchaType.class)
    void requiredFields_neverNull(CaptchaType type) {
        assertThat(type.getRequiredFields()).isNotNull();
    }
}