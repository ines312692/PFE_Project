package com.elyadata.feature.scraper.antibot.captcha.core;

import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CaptchaChallenge")
class CaptchaChallengeTest {


    @Test
    @DisplayName("null type → IllegalArgumentException")
    void nullType_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(null)
                .pageUrl("https://example.com")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type cannot be null");
    }

    @Test
    @DisplayName("null pageUrl → IllegalArgumentException")
    void nullPageUrl_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.FUNCAPTCHA)
                .pageUrl(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageUrl required");
    }

    @Test
    @DisplayName("blank pageUrl → IllegalArgumentException")
    void blankPageUrl_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.FUNCAPTCHA)
                .pageUrl("   ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageUrl required");
    }

    @Test
    @DisplayName("RECAPTCHA_V2 without siteKey → IllegalArgumentException")
    void recaptchaV2_noSiteKey_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V2)
                .pageUrl("https://example.com")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SITE_KEY");
    }

    @Test
    @DisplayName("HCAPTCHA without siteKey → IllegalArgumentException")
    void hcaptcha_noSiteKey_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.HCAPTCHA)
                .pageUrl("https://example.com")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SITE_KEY");
    }

    @Test
    @DisplayName("TURNSTILE without siteKey → IllegalArgumentException")
    void turnstile_noSiteKey_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.TURNSTILE)
                .pageUrl("https://example.com")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SITE_KEY");
    }


    @Test
    @DisplayName("RECAPTCHA_V3 without action → IllegalArgumentException")
    void recaptchaV3_noAction_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V3)
                .pageUrl("https://example.com")
                .siteKey("6Le-xxx")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTION");
    }


    @Test
    @DisplayName("IMAGE_TEXT without imageBase64 → IllegalArgumentException")
    void imageText_noImage_throws() {
        assertThatThrownBy(() -> CaptchaChallenge.builder()
                .type(CaptchaType.IMAGE_TEXT)
                .pageUrl("https://example.com")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMAGE");
    }

    @Test
    @DisplayName("RECAPTCHA_V2 with siteKey builds successfully")
    void recaptchaV2_withSiteKey_ok() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V2)
                .pageUrl("https://example.com")
                .siteKey("6Le-xxx")
                .build();
        assertThat(c.type()).isEqualTo(CaptchaType.RECAPTCHA_V2);
        assertThat(c.siteKey()).isEqualTo("6Le-xxx");
        assertThat(c.pageUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("RECAPTCHA_V3 with siteKey + action builds successfully")
    void recaptchaV3_withSiteKeyAndAction_ok() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V3)
                .pageUrl("https://example.com")
                .siteKey("6Le-xxx")
                .action("login")
                .build();
        assertThat(c.type()).isEqualTo(CaptchaType.RECAPTCHA_V3);
        assertThat(c.action()).isEqualTo("login");
    }

    @Test
    @DisplayName("IMAGE_TEXT with imageBase64 builds successfully")
    void imageText_withImage_ok() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.IMAGE_TEXT)
                .pageUrl("https://example.com")
                .imageBase64("base64data==")
                .build();
        assertThat(c.type()).isEqualTo(CaptchaType.IMAGE_TEXT);
        assertThat(c.imageBase64()).isEqualTo("base64data==");
    }

    @Test
    @DisplayName("FUNCAPTCHA (no required fields) builds with just type+pageUrl")
    void funcaptcha_minimalBuild_ok() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.FUNCAPTCHA)
                .pageUrl("https://example.com")
                .build();
        assertThat(c.type()).isEqualTo(CaptchaType.FUNCAPTCHA);
        assertThat(c.siteKey()).isNull();
    }

    @Test
    @DisplayName("jobId is stored correctly")
    void jobId_stored() {
        UUID id = UUID.randomUUID();
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.GEETEST)
                .pageUrl("https://example.com")
                .jobId(id)
                .build();
        assertThat(c.jobId()).isEqualTo(id);
    }

    @Test
    @DisplayName("invisible flag defaults to false")
    void invisibleFlag_defaultFalse() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.HCAPTCHA)
                .pageUrl("https://example.com")
                .siteKey("key-xx")
                .build();
        assertThat(c.invisible()).isFalse();
    }

    @Test
    @DisplayName("invisible flag can be set to true")
    void invisibleFlag_canBeSet() {
        CaptchaChallenge c = CaptchaChallenge.builder()
                .type(CaptchaType.RECAPTCHA_V2)
                .pageUrl("https://example.com")
                .siteKey("key-xx")
                .invisible(true)
                .build();
        assertThat(c.invisible()).isTrue();
    }
}