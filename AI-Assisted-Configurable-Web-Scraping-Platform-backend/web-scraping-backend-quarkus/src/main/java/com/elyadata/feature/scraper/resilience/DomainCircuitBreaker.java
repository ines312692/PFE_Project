package com.elyadata.feature.scraper.resilience;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class DomainCircuitBreaker {

    @Getter
    private final String domain;
    private final int threshold;
    private final long resetMs;

    private final AtomicReference<DomainCircuitBreakerState> state =
            new AtomicReference<>(DomainCircuitBreakerState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);

    public DomainCircuitBreaker(String domain, int threshold, int resetSeconds) {
        this.domain = domain;
        this.threshold = Math.max(1, threshold);
        this.resetMs = (long) resetSeconds * 1_000;
    }

    public boolean allowRequest() {
        DomainCircuitBreakerState current = state.get();
        return switch (current) {
            case CLOSED, HALF_OPEN -> true;
            case OPEN -> {
                if (System.currentTimeMillis() - openedAt.get() >= resetMs) {
                    if (state.compareAndSet(DomainCircuitBreakerState.OPEN, DomainCircuitBreakerState.HALF_OPEN)) {
                        log.info("[CB] {} → HALF_OPEN (probe request allowed)", domain);
                    }
                    yield true;
                }
                yield false;
            }
        };
    }

    public void recordSuccess() {
        DomainCircuitBreakerState prev = state.getAndSet(DomainCircuitBreakerState.CLOSED);
        failureCount.set(0);
        if (prev != DomainCircuitBreakerState.CLOSED) {
            log.info("[CB] {} → CLOSED (recovered)", domain);
        }
    }

    public void recordFailure() {
        DomainCircuitBreakerState current = state.get();

        if (current == DomainCircuitBreakerState.HALF_OPEN) {
            open();
            return;
        }

        int count = failureCount.incrementAndGet();
        log.debug("[CB] {} failure count={}/{}", domain, count, threshold);

        if (count >= threshold) {
            open();
        }
    }

    private void open() {
        state.set(DomainCircuitBreakerState.OPEN);
        openedAt.set(System.currentTimeMillis());
        failureCount.set(0);
        log.warn("[CB] {} → OPEN (circuit breaker tripped, reset in {}s)", domain, resetMs / 1_000);
    }

    public void reset() {
        DomainCircuitBreakerState prev = state.getAndSet(DomainCircuitBreakerState.CLOSED);
        failureCount.set(0);
        openedAt.set(0);
        if (prev != DomainCircuitBreakerState.CLOSED) {
            log.info("[CB] {} → CLOSED (reset for proxy rotation)", domain);
        }
    }
}