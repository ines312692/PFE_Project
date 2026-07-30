package com.elyadata.feature.scraper.site.detector;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.scraper.site.model.DetectedProtection;
import com.elyadata.feature.scraper.site.model.SiteDnsProbe;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class SiteProtectionDetectorRegistry {

    private static final String LOG_DUPLICATE =
            "[SITE-PROTECTION-REGISTRY] Duplicate detector for {} — keeping {}";
    private static final String LOG_REGISTERED =
            "[SITE-PROTECTION-REGISTRY] Registered {} detectors: {}";
    private static final String LOG_CRASHED =
            "[SITE-PROTECTION-REGISTRY] {} crashed during {}: {}";

    private final Instance<SiteProtectionDetector> detectorInstances;

    private Map<AntiBotProvider, SiteProtectionDetector> detectors;

    public SiteProtectionDetectorRegistry(Instance<SiteProtectionDetector> detectorInstances) {
        this.detectorInstances = detectorInstances;
    }

    @PostConstruct
    void init() {
        detectors = StreamSupport.stream(detectorInstances.spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(
                        SiteProtectionDetector::provider,
                        d -> d,
                        (a, b) -> {
                            log.warn(LOG_DUPLICATE, a.provider(), a.getClass().getSimpleName());
                            return a;
                        }));
        log.info(LOG_REGISTERED, detectors.size(), detectors.keySet());
    }

    public List<DetectedProtection> detectAll(int status,
                                              Map<String, List<String>> headers,
                                              String body,
                                              SiteDnsProbe dns) {
        if (detectors.isEmpty()) {
            return List.of();
        }

        Map<AntiBotProvider, List<DetectedProtection>> byProvider = new LinkedHashMap<>();

        for (SiteProtectionDetector detector : detectors.values()) {
            detectSafely(detector, "fingerprint", () -> detector.fingerprint(status, headers, body))
                    .ifPresent(protection -> addDetection(byProvider, protection));

            if (dns != null && !dns.isEmpty()) {
                detectSafely(detector, "dnsFingerprint", () -> detector.dnsFingerprint(dns))
                        .ifPresent(protection -> addDetection(byProvider, protection));
            }
        }

        return byProvider.values().stream()
                .map(this::mergePerProvider)
                .toList();
    }

    private void addDetection(Map<AntiBotProvider, List<DetectedProtection>> byProvider,
                              DetectedProtection protection) {
        byProvider.computeIfAbsent(protection.provider(), ignored -> new ArrayList<>()).add(protection);
    }

    private Optional<DetectedProtection> detectSafely(SiteProtectionDetector detector,
                                                      String operation,
                                                      Supplier<Optional<DetectedProtection>> detection) {
        try {
            return detection.get();
        } catch (Exception e) {
            log.warn(LOG_CRASHED, detector.provider(), operation, e.getMessage());
            return Optional.empty();
        }
    }

    private DetectedProtection mergePerProvider(List<DetectedProtection> detections) {
        if (detections.size() == 1) {
            return detections.getFirst();
        }

        AntiBotProvider provider = detections.getFirst().provider();
        double maxConfidence = detections.stream()
                .mapToDouble(DetectedProtection::confidence)
                .max()
                .orElse(0.0);
        String combinedEvidence = detections.stream()
                .map(DetectedProtection::evidence)
                .collect(Collectors.joining(" + "));

        return new DetectedProtection(provider, maxConfidence, combinedEvidence);
    }
}