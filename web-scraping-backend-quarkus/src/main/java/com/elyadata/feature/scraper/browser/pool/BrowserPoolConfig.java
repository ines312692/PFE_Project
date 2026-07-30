package com.elyadata.feature.scraper.browser.pool;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "scraper.browser.pool")
public interface BrowserPoolConfig {

    @WithDefault("3")
    int maxBrowsers();

    @WithDefault("20")
    int maxContextReuse();

    @WithDefault("120000")
    long acquireTimeoutMs();

    @WithDefault("true")
    boolean enabled();
}