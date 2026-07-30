package com.elyadata.feature.monitoring.domain.service;

import com.elyadata.feature.monitoring.application.dto.SystemHealthDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@RequiredArgsConstructor
public class SystemHealthService {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";
    private static final String JVM_MEMORY_USED = "jvm.memory.used";
    private static final String JVM_MEMORY_MAX = "jvm.memory.max";
    private static final String TAG_AREA = "area";
    private static final String TAG_HEAP = "heap";

    private final MeterRegistry registry;

    public SystemHealthDto snapshot() {
        long pending = (long) gauge("scraper_stream_pending");
        long length = (long) gauge("scraper_stream_length");
        long dlq = (long) gauge("scraper_stream_dlq_length");

        long inFlight = (long) gauge("scraper_worker_inflight");
        long free = (long) gauge("scraper_worker_free_slots");
        long capacity = inFlight + free;
        double utilization = capacity == 0 ? 0 : round(inFlight * 100.0 / capacity);

        long heapUsed = (long) (gaugeSum(JVM_MEMORY_USED, TAG_AREA, TAG_HEAP) / (1024 * 1024));
        long heapMax = (long) (positiveGaugeSum(JVM_MEMORY_MAX, TAG_AREA, TAG_HEAP) / (1024 * 1024));
        double heapPct = heapMax == 0 ? 0 : round(heapUsed * 100.0 / heapMax);
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();

        long httpTotal = (long) timerCount(HTTP_SERVER_REQUESTS, null, null);
        long httpErrors = (long) timerCount(HTTP_SERVER_REQUESTS, "outcome", "SERVER_ERROR");
        double httpErrRate = httpTotal == 0 ? 0 : round(httpErrors * 100.0 / httpTotal);
        double httpAvg = httpTotal == 0 ? 0 : round(timerTotalMs(HTTP_SERVER_REQUESTS) / httpTotal);

        long llmCalls = (long) counterSum("llm.calls.total");
        long llmTokens = (long) counterSum("llm.tokens.total");

        long capAttempts = (long) counterSum("captcha.solve.attempts");
        long capSuccess = (long) counterSum("captcha.solve.success");
        double capRate = capAttempts == 0 ? 0 : round(capSuccess * 100.0 / capAttempts);

        String status = deriveStatus(dlq, heapPct, httpErrRate, utilization);

        return new SystemHealthDto(
                Instant.now(),
                status,
                new SystemHealthDto.Streams(pending, length, dlq),
                new SystemHealthDto.Workers(inFlight, capacity, free, utilization),
                new SystemHealthDto.Jvm(heapUsed, heapMax, heapPct, uptime, threads),
                new SystemHealthDto.Http(httpTotal, httpErrors, httpErrRate, httpAvg),
                new SystemHealthDto.Llm(llmCalls, llmTokens),
                new SystemHealthDto.Captcha(capAttempts, capRate));
    }

    private static String deriveStatus(long dlq, double heapPct, double httpErrRate, double utilization) {
        if (dlq > 50 || heapPct > 90 || httpErrRate > 10) return "CRITICAL";
        if (dlq > 0 || heapPct > 75 || httpErrRate > 3 || utilization > 90) return "DEGRADED";
        return "HEALTHY";
    }

    private double gauge(String name) {
        Gauge g = registry.find(name).gauge();
        return g != null && !Double.isNaN(g.value()) ? g.value() : 0.0;
    }

    @SuppressWarnings("SameParameterValue")
    private double gaugeSum(String name, String tagKey, String tagVal) {
        return registry.find(name).tag(tagKey, tagVal).gauges().stream()
                .mapToDouble(Gauge::value)
                .filter(v -> !Double.isNaN(v))
                .sum();
    }

    @SuppressWarnings("SameParameterValue")
    private double positiveGaugeSum(String name, String tagKey, String tagVal) {
        return registry.find(name).tag(tagKey, tagVal).gauges().stream()
                .mapToDouble(Gauge::value)
                .filter(v -> !Double.isNaN(v) && v > 0)
                .sum();
    }

    private double counterSum(String name) {
        return registry.find(name).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    @SuppressWarnings("SameParameterValue")
    private double timerCount(String name, String tagKey, String tagVal) {
        var search = registry.find(name);
        if (tagKey != null) search = search.tag(tagKey, tagVal);
        return search.timers().stream().mapToDouble(Timer::count).sum();
    }

    private double timerTotalMs(String name) {
        return registry.find(name).timers().stream()
                .mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS))
                .sum();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}