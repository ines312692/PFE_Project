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
public class PerimeterXSiteDetector implements SiteProtectionDetector {

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.PERIMETER_X;
    }

    @Override
    public Optional<DetectedProtection> fingerprint(int status,
                                                    Map<String, List<String>> headers,
                                                    String body) {
        String matchedHeader = findHeader(headers);
        if (matchedHeader != null) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.97,
                    "header '" + matchedHeader + "' present"));
        }

        String matchedCookie = findCookie(headers);
        return Optional.of(new DetectedProtection(
                provider(), 0.93,
                "set-cookie '" + matchedCookie + "' present"));

    }

    private String findHeader(Map<String, List<String>> headers) {
        for (String key : headers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (AntiBotMarkers.PerimeterX.HEADERS.contains(lower)) return lower;
        }
        return null;
    }

    private String findCookie(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(e -> "set-cookie".equalsIgnoreCase(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .flatMap(cookieLine -> AntiBotMarkers.PerimeterX.COOKIE_MARKERS.stream()
                        .filter(marker -> cookieLine.startsWith(marker + "=")
                                || cookieLine.contains(" " + marker + "=")))
                .findFirst()
                .orElse(null);
    }
}