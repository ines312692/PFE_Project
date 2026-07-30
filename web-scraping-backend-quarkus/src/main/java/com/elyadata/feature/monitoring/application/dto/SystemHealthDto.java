package com.elyadata.feature.monitoring.application.dto;

import java.time.Instant;


public record SystemHealthDto(
        Instant timestamp,
        String status,
        Streams streams,
        Workers workers,
        Jvm jvm,
        Http http,
        Llm llm,
        Captcha captcha) {

    public record Streams(long pending, long length, long dlq) {}

    public record Workers(long inFlight, long capacity, long freeSlots, double utilizationPct) {}

    public record Jvm(long heapUsedMb, long heapMaxMb, double heapPct,
                      long uptimeMs, int threads) {}

    public record Http(long totalRequests, long errorRequests,
                       double errorRatePct, double avgLatencyMs) {}

    public record Llm(long totalCalls, long totalTokens) {}

    public record Captcha(long attempts, double successRatePct) {}
}
