package com.elyadata.feature.job.e2e;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;


public class DistributedStreamTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "scraper.stream.enabled", "true",
                "scraper.stream.job-run.poll-interval", "200ms",
                "scraper.stream.crawl-join.poll-interval", "200ms",
                "scraper.stream.etl.poll-interval", "200ms"
        );
    }
}
