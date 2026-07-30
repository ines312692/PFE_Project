package com.elyadata.feature.scraper.browser.login;

import com.elyadata.feature.job.domain.model.SessionConfig.LoginMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class LoginStrategyRegistry {

    private final Map<LoginMode, LoginStrategy> byMode = new EnumMap<>(LoginMode.class);

    @Inject
    public LoginStrategyRegistry(Instance<LoginStrategy> strategies) {
        for (LoginStrategy s : strategies) {
            byMode.put(s.mode(), s);
        }
        log.info("[LOGIN] Registered strategies: {}", byMode.keySet());
    }

    public LoginStrategy get(LoginMode mode) {
        LoginStrategy s = byMode.get(mode);
        if (s == null) {
            throw new IllegalStateException("No LoginStrategy registered for mode " + mode);
        }
        return s;
    }
}