package com.elyadata.feature.scraper.antibot.captcha.orchestration;

import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaProvider;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaSolver;
import com.elyadata.feature.scraper.antibot.captcha.core.CaptchaType;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@Slf4j
@ApplicationScoped
public class CaptchaSolverRegistry {

    private static final String LOG_REGISTERED =
            "[CAPTCHA-REGISTRY] Registered {} solver(s): {} | preferred={} fallback={}";
    private static final String LOG_DISABLED =
            "[CAPTCHA-REGISTRY] Preferred solver {} is disabled — falling back to {}";
    private static final String LOG_UNSUPPORTED =
            "[CAPTCHA-REGISTRY] Solver {} does not support type={} — falling back to {}";

    private final Instance<CaptchaSolver> solverInstances;

    @ConfigProperty(name = "captcha.preferred-provider", defaultValue = "MOCK")
    String preferredProviderName;

    @ConfigProperty(name = "captcha.fallback-provider", defaultValue = "MOCK")
    String fallbackProviderName;

    private Map<CaptchaProvider, CaptchaSolver> solvers;
    @Getter
    private CaptchaProvider preferredProvider;
    private CaptchaProvider fallbackProvider;

    public CaptchaSolverRegistry(Instance<CaptchaSolver> solverInstances) {
        this.solverInstances = solverInstances;
    }

    @PostConstruct
    void init() {
        solvers = StreamSupport.stream(solverInstances.spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(
                        CaptchaSolver::provider,
                        s -> s
                ));

        preferredProvider = parseProvider(preferredProviderName);
        fallbackProvider = parseProvider(fallbackProviderName);

        log.info(LOG_REGISTERED,
                solvers.size(), solvers.keySet(), preferredProvider, fallbackProvider);
    }


    public Optional<CaptchaSolver> resolve(CaptchaType type) {
        if (type == null) return Optional.empty();

        CaptchaSolver preferred = solvers.get(preferredProvider);
        if (isUsable(preferred, type)) {
            return Optional.of(preferred);
        }

        logWhyPreferredRejected(preferred, type);

        CaptchaSolver fallback = solvers.get(fallbackProvider);
        if (isUsable(fallback, type)) {
            return Optional.of(fallback);
        }

        return findAnyUsable(type);
    }

    private boolean isUsable(CaptchaSolver solver, CaptchaType type) {
        return solver != null && solver.isEnabled() && solver.supports(type);
    }

    private void logWhyPreferredRejected(CaptchaSolver preferred, CaptchaType type) {
        if (preferred == null) {
            log.warn("[CAPTCHA-REGISTRY] Preferred provider {} is not registered — falling back to {}",
                    preferredProvider, fallbackProvider);
        } else if (!preferred.isEnabled()) {
            log.warn(LOG_DISABLED, preferredProvider, fallbackProvider);
        } else if (!preferred.supports(type)) {
            log.warn(LOG_UNSUPPORTED, preferredProvider, type, fallbackProvider);
        }
    }

    private Optional<CaptchaSolver> findAnyUsable(CaptchaType type) {
        return solvers.values().stream()
                .filter(s -> isUsable(s, type))
                .findFirst();
    }

    private CaptchaProvider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) return CaptchaProvider.MOCK;
        try {
            return CaptchaProvider.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("[CAPTCHA-REGISTRY] Unknown provider '{}' in config — defaulting to {}",
                    raw, CaptchaProvider.MOCK);
            return CaptchaProvider.MOCK;
        }
    }
}