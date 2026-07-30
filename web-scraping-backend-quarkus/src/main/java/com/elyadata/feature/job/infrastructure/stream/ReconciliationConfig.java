package com.elyadata.feature.job.infrastructure.stream;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;


@ConfigMapping(prefix = "scraper.reconciliation")
public interface ReconciliationConfig {

    @WithDefault("true")
    boolean enabled();

    @WithName("stale-after")
    @WithDefault("15m")
    Duration staleAfter();

    @WithName("scan-interval")
    @WithDefault("5m")
    Duration scanInterval();

    @WithName("batch-size")
    @WithDefault("100")
    int batchSize();

    @WithName("marker-ttl")
    @WithDefault("30m")
    Duration markerTtl();
}
