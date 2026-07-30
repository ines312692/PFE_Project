package com.elyadata.feature.scraper.resilience;

import com.elyadata.feature.job.domain.model.RetryPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@ApplicationScoped
public class CircuitBreakerRegistry {

    private static final int DEFAULT_THRESHOLD = 5;
    private static final int DEFAULT_RESET_SECONDS = 60;

    private final ConcurrentMap<String, DomainCircuitBreaker> breakers =
            new ConcurrentHashMap<>();

    public DomainCircuitBreaker getOrCreate(String url, RetryPolicy policy) {
        String domain = extractDomain(url);
        return breakers.computeIfAbsent(domain, d -> {
            int threshold = policy != null && policy.circuitBreakerThreshold() != null ? policy.circuitBreakerThreshold().intValue() : DEFAULT_THRESHOLD;
            int resetSeconds = policy != null && policy.circuitBreakerResetSeconds() != null ? policy.circuitBreakerResetSeconds() : DEFAULT_RESET_SECONDS;
            log.info("[CB-REGISTRY] Creating circuit breaker for domain={} threshold={} resetSec={}",
                    d, threshold, resetSeconds);
            return new DomainCircuitBreaker(d, threshold, resetSeconds);
        });
    }

    public ConcurrentMap<String, DomainCircuitBreaker> getAll() {
        return breakers;
    }


    private static String extractDomain(String url) {
        try {
            return URI.create(url).getHost().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    public void reset(String url) {
        String domain = extractDomain(url);
        DomainCircuitBreaker cb = breakers.get(domain);
        if (cb != null) {
            cb.reset();
            log.info("[CB-REGISTRY] Circuit breaker reset for domain={} (proxy rotation)", domain);
        }
    }
}