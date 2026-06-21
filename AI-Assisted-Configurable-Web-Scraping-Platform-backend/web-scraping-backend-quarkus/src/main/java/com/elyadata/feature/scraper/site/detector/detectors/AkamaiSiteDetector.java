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
public class AkamaiSiteDetector implements SiteProtectionDetector {

    @Override
    public AntiBotProvider provider() {
        return AntiBotProvider.AKAMAI;
    }

    @Override
    public Optional<DetectedProtection> fingerprint(int status,
                                                    Map<String, List<String>> headers,
                                                    String body) {
        String matchedHeader = findHeader(headers);
        if (matchedHeader != null) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.95,
                    "header '" + matchedHeader + "' present"));
        }

        if (serverIsAkamai(headers)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.92, "server header indicates akamai"));
        }

        String lowerBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (AntiBotMarkers.Akamai.BODY_BLOCK_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.88, "akamai bot manager block page detected"));
        }
        if (AntiBotMarkers.Akamai.BODY_DETECT_MARKERS.stream().anyMatch(lowerBody::contains)) {
            return Optional.of(new DetectedProtection(
                    provider(), 0.50, "akamai mentioned in page body"));
        }

        return Optional.empty();
    }

    private String findHeader(Map<String, List<String>> headers) {
        for (String key : headers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (AntiBotMarkers.Akamai.HEADERS.contains(lower)) return lower;
        }
        return null;
    }

    private boolean serverIsAkamai(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(e -> "server".equalsIgnoreCase(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(v -> AntiBotMarkers.Akamai.SERVER_VALUES.stream().anyMatch(v::contains));
    }
}