package com.elyadata.feature.scraper.browser.pool;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class BrowserPoolMetrics {

    private final MeterRegistry registry;

    private final AtomicInteger inUse = new AtomicInteger();
    private final AtomicInteger waiting = new AtomicInteger();
    private final AtomicInteger capacity = new AtomicInteger();

    private Timer waitTimer;
    private Counter recycles;
    private Counter timeouts;

    @PostConstruct
    void init() {
        Gauge.builder("scraper.browser.pool.in_use", inUse, AtomicInteger::get)
                .description("Navigateurs actuellement empruntes")
                .strongReference(true)
                .register(registry);

        Gauge.builder("scraper.browser.pool.waiting", waiting, AtomicInteger::get)
                .description("Workers bloques en attente d'un slot navigateur")
                .strongReference(true)
                .register(registry);

        Gauge.builder("scraper.browser.pool.capacity", capacity, AtomicInteger::get)
                .description("Nombre maximal de navigateurs simultanes (max-browsers)")
                .strongReference(true)
                .register(registry);

        waitTimer = Timer.builder("scraper.browser.pool.wait")
                .description("Temps d'attente d'un slot navigateur")
                .publishPercentileHistogram()
                .register(registry);

        recycles = Counter.builder("scraper.browser.pool.recycles")
                .description("Chrome recycles (mode exclusif ou contexte empoisonne)")
                .register(registry);

        timeouts = Counter.builder("scraper.browser.pool.acquire_timeouts")
                .description("Echecs d'acquisition apres acquire-timeout-ms")
                .register(registry);
    }

    void registerCapacity(int n) {
        capacity.set(n);
    }

    void waitStarted() {
        waiting.incrementAndGet();
    }

    void waitEnded() {
        waiting.decrementAndGet();
    }

    void slotAcquired() {
        inUse.incrementAndGet();
    }

    void slotReleased() {
        inUse.decrementAndGet();
    }

    void recordWait(long nanos) {
        waitTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    void recycled() {
        recycles.increment();
    }

    void acquireTimeout() {
        timeouts.increment();
    }
}