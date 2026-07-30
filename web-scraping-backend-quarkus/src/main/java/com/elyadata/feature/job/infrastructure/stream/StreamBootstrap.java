package com.elyadata.feature.job.infrastructure.stream;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class StreamBootstrap {

    private final StreamConfig config;
    private final StreamConsumerSupport support;

    void onStart(@Observes StartupEvent ev) {
        if (!config.enabled()) {
            log.info("[STREAM] Disabled — skip bootstrap");
            return;
        }
        support.ensureGroup(RedisStreamNames.JOB_RUN, config.jobRun().group());
        support.ensureGroup(RedisStreamNames.ETL_PROCESS, config.etl().group());
        log.info("[STREAM] Bootstrap done");
    }
}