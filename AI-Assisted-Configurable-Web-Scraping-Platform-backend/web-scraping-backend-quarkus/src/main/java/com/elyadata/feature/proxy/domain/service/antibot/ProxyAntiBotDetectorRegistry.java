package com.elyadata.feature.proxy.domain.service.antibot;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class ProxyAntiBotDetectorRegistry {

    private static final String LOG_DUPLICATE = "[ANTIBOT-REGISTRY] Duplicate detector for {} — keeping {}";
    private static final String LOG_REGISTERED = "[ANTIBOT-REGISTRY] Registered {} detectors: {}";
    private static final String LOG_CRASHED = "[ANTIBOT-REGISTRY] {} crashed for {}: {}";
    private static final String LOG_NO_DETECTOR = "No detector registered for {}";

    @Inject
    Instance<ProxyAntiBotDetector> detectorInstances;

    private Map<AntiBotProvider, ProxyAntiBotDetector> detectors;

    @PostConstruct
    void init() {
        detectors = StreamSupport.stream(detectorInstances.spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(
                        ProxyAntiBotDetector::provider,
                        d -> d,
                        (a, b) -> {
                            log.warn(LOG_DUPLICATE, a.provider(), a.getClass().getSimpleName());
                            return a;
                        }));
        log.info(LOG_REGISTERED, detectors.size(), detectors.keySet());
    }

    public Set<AntiBotProvider> registeredProviders() {
        return detectors.keySet();
    }

    public List<DetectionResult> checkAll(Proxy proxy) {
        List<DetectionResult> results = new ArrayList<>(detectors.size());
        for (ProxyAntiBotDetector d : detectors.values()) {
            results.add(checkOne(proxy, d.provider()));
        }
        return results;
    }

    public DetectionResult checkOne(Proxy proxy, AntiBotProvider provider) {
        ProxyAntiBotDetector d = detectors.get(provider);
        if (d == null) {
            log.warn(LOG_NO_DETECTOR, provider);
            return DetectionResult.untested(provider, "No detector registered for " + provider);
        }
        try {
            return d.check(proxy);
        } catch (Exception e) {
            log.warn(LOG_CRASHED, provider, proxy.endpoint(), e.getMessage());
            return DetectionResult.unreachable(provider,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public DetectionResult check(Proxy proxy, AntiBotProvider provider) {
        return checkOne(proxy, provider);
    }
}