package com.elyadata.feature.scraper.antibot.captcha.core;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;

import java.util.Set;


public class CaptchaSolverException extends Infrastructure5xxException {

    private CaptchaSolverException(String message, String uriPath) {
        super(message, uriPath, Set.of());
    }

    public static CaptchaSolverException noSolverFor(CaptchaProvider provider, CaptchaType type) {
        return new CaptchaSolverException(
                "No active solver for provider=" + provider + " type=" + type,
                "captcha/solve");
    }

    public static CaptchaSolverException providerDown(CaptchaProvider provider, String reason) {
        return new CaptchaSolverException(
                "CAPTCHA provider " + provider + " unavailable: " + reason,
                "captcha/provider/" + provider);
    }

    public static CaptchaSolverException timeout(CaptchaProvider provider, long elapsedMs) {
        return new CaptchaSolverException(
                "CAPTCHA solving timeout on " + provider + " after " + elapsedMs + "ms",
                "captcha/timeout");
    }

    public static CaptchaSolverException invalidConfig(String reason) {
        return new CaptchaSolverException(
                "CAPTCHA configuration invalid: " + reason,
                "captcha/config");
    }
}