package com.elyadata.feature.scraper.site.detector;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.SiteDnsProbe;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SiteProtectionDetector {

    AntiBotProvider provider();

    Optional<DetectedProtection> fingerprint(int status,
                                             Map<String, List<String>> headers,
                                             String body);


    default Optional<DetectedProtection> dnsFingerprint(SiteDnsProbe dns) {
        return Optional.empty();
    }
}