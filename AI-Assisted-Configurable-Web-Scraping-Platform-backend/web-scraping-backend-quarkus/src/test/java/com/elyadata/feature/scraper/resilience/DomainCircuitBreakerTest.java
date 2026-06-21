package com.elyadata.feature.scraper.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainCircuitBreaker")
class DomainCircuitBreakerTest {

    private DomainCircuitBreaker cb;

    @BeforeEach
    void setUp() {
        cb = new DomainCircuitBreaker("example.com", 3, 1); // threshold=3, reset=1s
    }

    @Nested
    @DisplayName("Initial state (CLOSED)")
    class InitialState {

        @Test
        @DisplayName("allows requests when CLOSED")
        void allowsRequestsWhenClosed() {
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("domain is correctly stored")
        void storesDomain() {
            assertThat(cb.getDomain()).isEqualTo("example.com");
        }
    }

    @Nested
    @DisplayName("Failure recording")
    class FailureRecording {

        @Test
        @DisplayName("stays CLOSED below threshold")
        void staysClosedBelowThreshold() {
            cb.recordFailure();
            cb.recordFailure();
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("opens circuit at threshold")
        void opensAtThreshold() {
            cb.recordFailure();
            cb.recordFailure();
            cb.recordFailure(); // 3rd — should open
            assertThat(cb.allowRequest()).isFalse();
        }

        @Test
        @DisplayName("blocks requests when OPEN")
        void blocksWhenOpen() {
            tripBreaker(cb);
            assertThat(cb.allowRequest()).isFalse();
        }
    }

    @Nested
    @DisplayName("Success recording")
    class SuccessRecording {

        @Test
        @DisplayName("recordSuccess() closes the breaker after failures below threshold")
        void closesAfterSuccess() {
            cb.recordFailure();
            cb.recordSuccess();
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("resets failure count so threshold needs to be reached again")
        void resetsFailureCount() {
            cb.recordFailure();
            cb.recordFailure();
            cb.recordSuccess();
            cb.recordFailure();
            cb.recordFailure();
            assertThat(cb.allowRequest()).isTrue();
        }
    }

    @Nested
    @DisplayName("HALF_OPEN / auto-reset")
    class HalfOpen {

        @Test
        @DisplayName("transitions to HALF_OPEN after reset window elapses")
        void transitionsToHalfOpenAfterReset() throws InterruptedException {
            DomainCircuitBreaker shortCb = new DomainCircuitBreaker("test.com", 1, 0);
            shortCb.recordFailure();
            Thread.sleep(50);
            assertThat(shortCb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("failure in HALF_OPEN re-opens the breaker")
        void failureInHalfOpenReopens() throws InterruptedException {
            DomainCircuitBreaker shortCb = new DomainCircuitBreaker("test.com", 1, 1);
            shortCb.recordFailure();
            Thread.sleep(1100);
            shortCb.allowRequest();
            shortCb.recordFailure();
            assertThat(shortCb.allowRequest()).isFalse();
        }

        @Test
        @DisplayName("success in HALF_OPEN closes the breaker")
        void successInHalfOpenCloses() throws InterruptedException {
            DomainCircuitBreaker shortCb = new DomainCircuitBreaker("test.com", 1, 0);
            shortCb.recordFailure();
            Thread.sleep(50);
            shortCb.allowRequest();
            shortCb.recordSuccess();
            assertThat(shortCb.allowRequest()).isTrue();
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("reset() closes an open breaker")
        void closesOpenBreaker() {
            tripBreaker(cb);
            cb.reset();
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("reset() clears failure count")
        void clearsFailureCount() {
            cb.recordFailure();
            cb.recordFailure();
            cb.reset();
            cb.recordFailure();
            cb.recordFailure();
            assertThat(cb.allowRequest()).isTrue();
        }

        @Test
        @DisplayName("reset() on an already-closed breaker is a no-op")
        void noOpOnClosedBreaker() {
            cb.reset();
            assertThat(cb.allowRequest()).isTrue();
        }
    }

    private static void tripBreaker(DomainCircuitBreaker cb) {
        for (int i = 0; i < 3; i++) cb.recordFailure();
    }
}