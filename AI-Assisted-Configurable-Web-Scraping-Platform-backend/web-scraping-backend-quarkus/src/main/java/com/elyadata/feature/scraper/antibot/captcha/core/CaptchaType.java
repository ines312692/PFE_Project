package com.elyadata.feature.scraper.antibot.captcha.core;

import lombok.Getter;

import java.util.Set;

@Getter
public enum CaptchaType {
    IMAGE_TEXT(false, Field.IMAGE),
    RECAPTCHA_V2(true, Field.SITE_KEY),
    RECAPTCHA_V3(true, Field.SITE_KEY, Field.ACTION),
    HCAPTCHA(true, Field.SITE_KEY),
    FUNCAPTCHA(false),
    ROTATE(false),
    TURNSTILE(true, Field.SITE_KEY),
    GEETEST(false),
    CLOUDFLARE_INTERSTITIAL(false),
    CUSTOM(false),
    UNKNOWN(false);

    private final boolean tokenBased;
    private final Set<Field> requiredFields;

    CaptchaType(boolean tokenBased, Field... required) {
        this.tokenBased = tokenBased;
        this.requiredFields = Set.of(required);
    }


    public enum Field {SITE_KEY, ACTION, IMAGE}
}