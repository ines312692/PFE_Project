package com.elyadata.feature.job.infrastructure.stream;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

@ConfigMapping(prefix = "scraper.stream")
public interface StreamConfig {

    @WithDefault("true")
    boolean enabled();

    JobRun jobRun();
    Recovery recovery();
    Etl etl();
    CrawlJoin crawlJoin();

    interface JobRun {
        @WithDefault("job-runners")
        String group();

        @WithName("concurrency")
        @WithDefault("${SCRAPER_MAX_ASYNC:2}")
        int concurrency();

        @WithName("poll-interval")
        @WithDefault("1s")
        Duration pollInterval();

        @WithName("max-deliveries")
        @WithDefault("3")
        int maxDeliveries();

        @WithDefault("100000")
        long maxlen();
    }

    interface Recovery {
        @WithName("scan-interval")
        @WithDefault("30s")
        Duration scanInterval();

        @WithName("claim-min-idle")
        @WithDefault("2m")
        Duration claimMinIdle();

        @WithDefault("50")
        int count();
    }

    interface Etl {
        @WithDefault("etl-runners")
        String group();

        @WithName("concurrency")
        @WithDefault("${SCRAPER_MAX_ASYNC:2}")
        int concurrency();

        @WithName("poll-interval")
        @WithDefault("1s")
        Duration pollInterval();

        @WithName("max-deliveries")
        @WithDefault("3")
        int maxDeliveries();

        @WithDefault("100000")
        long maxlen();
    }

    interface CrawlJoin {
        @WithDefault("crawl-joiners")
        String group();

        @WithDefault("2")
        int concurrency();

        @WithDefault("2")
        int parallelism();

        @WithName("poll-interval")
        @WithDefault("1s")
        Duration pollInterval();

        @WithName("max-deliveries")
        @WithDefault("2")
        int maxDeliveries();

        @WithDefault("100000")
        long maxlen();
    }
}