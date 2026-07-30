package com.elyadata.feature.scraper.resilience;

public enum DomainCircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}