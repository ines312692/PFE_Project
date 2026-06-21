package com.elyadata.feature.scraper.antibot.captcha.core;

public interface CaptchaSolver {

    CaptchaProvider provider();

    boolean supports(CaptchaType type);

    boolean isEnabled();

    CaptchaSolution solve(CaptchaChallenge challenge);
}