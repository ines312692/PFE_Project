package com.elyadata.common.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Duration;

@Singleton
public class MetricsHistogramConfig {

    private static final double MIN_NANOS = Duration.ofMillis(5).toNanos();
    private static final double MAX_NANOS = Duration.ofSeconds(120).toNanos();

    @Produces
    @Singleton
    public MeterFilter enableHistograms() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id,
                                                         DistributionStatisticConfig config) {
                if (id.getType() != Meter.Type.TIMER) {
                    return config;
                }

                String name = id.getName();
                boolean wanted = name.startsWith("http.server.requests")
                        || name.startsWith("scraper.")
                        || name.startsWith("llm.")
                        || name.startsWith("elya.");

                if (!wanted) {
                    return config;
                }

                return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .minimumExpectedValue(MIN_NANOS)
                        .maximumExpectedValue(MAX_NANOS)
                        .build()
                        .merge(config);
            }
        };
    }
}