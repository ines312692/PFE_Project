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
public class DataDomeSiteDetector implements SiteProtectionDetector {

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.DATADOME;
    }

    @Override
    public Optional<DetectedProtection> fingerprint(int status,
                                                    Map<String, List<String>> headers,
                                                    String body) {
        String matchedHeader = findHeader(headers);
        if (matchedHeader != null) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.98,
                    "header '" + matchedHeader + "' present"));
        }

        String lowerBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (AntiBotMarkers.DataDome.BODY_BLOCK_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.92, "datadome challenge/captcha page detected"));
        }
        if (AntiBotMarkers.DataDome.BODY_DETECT_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.75, "datadome marker found in page body"));
        }

        return Optional.empty();
    }

    private String findHeader(Map<String, List<String>> headers) {
        for (String key : headers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (AntiBotMarkers.DataDome.HEADERS.contains(lower)) return lower;
        }
        return null;
    }
}