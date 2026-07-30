package com.elyadata.common.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app.security")
public interface SecurityConfigSettings {

    Optional<RateLimitConfig> rateLimit();

    interface RateLimitConfig {
        @WithDefault("100")
        int maxRequestsPerWindow();

        @WithDefault("60")
        long windowSeconds();
    }
}