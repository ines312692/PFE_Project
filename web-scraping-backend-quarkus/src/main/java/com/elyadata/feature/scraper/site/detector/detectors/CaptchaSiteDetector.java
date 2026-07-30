package com.elyadata.feature.scraper.site.detector.detectors;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotMarkers;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.detector.SiteProtectionDetector;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CaptchaSiteDetector implements SiteProtectionDetector {

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.HCAPTCHA;
    }

    @Override
    public Optional<DetectedProtection> fingerprint(int status,
                                                    Map<String, List<String>> headers,
                                                    String body) {
        if (body == null || body.isEmpty()) return Optional.empty();
        String lowerBody = body.toLowerCase(Locale.ROOT);

        if (AntiBotMarkers.Captcha.HCAPTCHA_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    AntiBotProvider.HCAPTCHA, 0.95, "hCaptcha widget detected in page"));
        }
        if (AntiBotMarkers.Captcha.TURNSTILE_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    AntiBotProvider.HCAPTCHA, 0.93, "Cloudflare Turnstile widget detected in page"));
        }
        if (AntiBotMarkers.Captcha.RECAPTCHA_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    AntiBotProvider.RECAPTCHA, 0.95, "reCAPTCHA widget detected in page"));
        }

        return Optional.empty();
    }
}