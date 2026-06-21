package com.elyadata.feature.scraper.resilience;

import com.elyadata.feature.job.domain.model.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CircuitBreakerRegistry")
class CircuitBreakerRegistryTest {

    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CircuitBreakerRegistry();
    }

    @Nested
    @DisplayName("getOrCreate()")
    class GetOrCreate {

        @Test
        @DisplayName("creates a breaker for a new domain")
        void createsBreakerForNewDomain() {
            DomainCircuitBreaker cb = registry.getOrCreate("https://example.com/page", null);
            assertThat(cb).isNotNull();
            assertThat(cb.getDomain()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("returns same instance for the same domain")
        void returnsSameInstanceForSameDomain() {
            DomainCircuitBreaker first  = registry.getOrCreate("https://example.com/a", null);
            DomainCircuitBreaker second = registry.getOrCreate("https://example.com/b", null);
            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("strips www. prefix when comparing domains")
        void stripsWwwPrefix() {
            DomainCircuitBreaker withWww    = registry.getOrCreate("https://www.example.com", null);
            DomainCircuitBreaker withoutWww = registry.getOrCreate("https://example.com", null);
            assertThat(withWww).isSameAs(withoutWww);
        }

        @Test
        @DisplayName("creates distinct breakers for distinct domains")
        void createsDistinctBreakers() {
            DomainCircuitBreaker alpha = registry.getOrCreate("https://alpha.com", null);
            DomainCircuitBreaker beta  = registry.getOrCreate("https://beta.com", null);
            assertThat(alpha).isNotSameAs(beta);
        }

        @Test
        @DisplayName("uses policy threshold and reset seconds when provided")
        void usesPolicySettings() {
            RetryPolicy policy = mock(RetryPolicy.class);
            when(policy.circuitBreakerThreshold()).thenReturn((double) 2L);
            when(policy.circuitBreakerResetSeconds()).thenReturn(30);

            DomainCircuitBreaker cb = registry.getOrCreate("https://mysite.com", policy);
            cb.recordFailure();
            cb.recordFailure();
            assertThat(cb.allowRequest()).isFalse();
        }

        @Test
        @DisplayName("uses default threshold when policy is null")
        void usesDefaultsWithNullPolicy() {
            DomainCircuitBreaker cb = registry.getOrCreate("https://site.com", null);
            for (int i = 0; i < 4; i++) cb.recordFailure();
            assertThat(cb.allowRequest()).isTrue();
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("resets the breaker for the given URL domain")
        void resetsBreakerForDomain() {
            DomainCircuitBreaker cb = registry.getOrCreate("https://example.com", null);
            for (int i = 0; i < 5; i++) cb.recordFailure(); // open it
            assertThat(cb.allowRequest()).isFalse();

            registry.reset("https://example.com/some-page");
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("is a no-op when domain has no registered breaker")
        void noOpForUnknownDomain() {
            registry.reset("https://unknown.com");
        }
    }

    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @DisplayName("returns empty map initially")
        void emptyMapInitially() {
            assertThat(registry.getAll()).isEmpty();
        }

        @Test
        @DisplayName("returns all registered breakers")
        void returnsAllBreakers() {
            registry.getOrCreate("https://a.com", null);
            registry.getOrCreate("https://b.com", null);
            assertThat(registry.getAll()).hasSize(2);
        }
    }
}
