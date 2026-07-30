package com.elyadata.feature.scraper.resilience;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.domain.model.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("RetryExecutor")
class RetryExecutorTest {

    private static final Logger LOG = mock(Logger.class);

    private RetryPolicy policyOf(int maxRetries) {
        RetryPolicy p = mock(RetryPolicy.class);
        org.mockito.Mockito.when(p.maxRetries()).thenReturn(maxRetries);
        org.mockito.Mockito.when(p.retryInitialDelayMs()).thenReturn(Math.toIntExact(10L));
        org.mockito.Mockito.when(p.retryBackoffMultiplier()).thenReturn(1.0);
        org.mockito.Mockito.when(p.retryMaxDelayMs()).thenReturn(Math.toIntExact(50L));
        return p;
    }

    private String incrementAndThrowAlways(AtomicInteger calls) {
        calls.incrementAndGet();
        throw new RuntimeException("always fails");
    }

    private String incrementAndRethrow(AtomicInteger calls, Infrastructure5xxException ex) {
        calls.incrementAndGet();
        throw ex;
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("returns value on first successful attempt")
        void returnsOnFirstAttempt() {
            String result = RetryExecutor.execute(
                    () -> "hello",
                    policyOf(3),
                    "ctx",
                    LOG);
            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("retries and succeeds after a transient failure")
        void retriesOnTransientFailure() {
            AtomicInteger calls = new AtomicInteger(0);

            String result = RetryExecutor.execute(
                    () -> {
                        if (calls.incrementAndGet() < 3) throw new RuntimeException("transient");
                        return "ok";
                    },
                    policyOf(3),
                    "ctx",
                    LOG);

            assertThat(result).isEqualTo("ok");
            assertThat(calls.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("throws Infrastructure5xxException after exhausting all retries")
        void throwsAfterExhaustingRetries() {
            AtomicInteger calls = new AtomicInteger(0);

            assertThatThrownBy(() -> RetryExecutor.execute(
                    () -> incrementAndThrowAlways(calls),
                    policyOf(2),
                    "ctx",
                    LOG))
                    .isInstanceOf(Infrastructure5xxException.class)
                    .hasMessageContaining("exhausted");

            assertThat(calls.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("works with null policy (0 retries, no wait)")
        void worksWithNullPolicy() {
            String result = RetryExecutor.execute(() -> "done", null, "ctx", LOG);
            assertThat(result).isEqualTo("done");
        }

        @Test
        @DisplayName("throws immediately with null policy on failure")
        void throwsImmediatelyWithNullPolicy() {
            assertThatThrownBy(() -> RetryExecutor.execute(
                    () -> { throw new RuntimeException("boom"); },
                    null, "ctx", LOG))
                    .isInstanceOf(Infrastructure5xxException.class);
        }

        @Test
        @DisplayName("Infrastructure5xxException is not swallowed — re-thrown directly")
        void rethrowsInfraException() {
            Infrastructure5xxException infra = Infrastructure5xxException
                    .infrastructure5xxExceptionBuilder()
                    .message("infra error")
                    .uriPath("/test")
                    .subErrors(java.util.Set.of())
                    .build();

            AtomicInteger calls = new AtomicInteger(0);

            assertThatThrownBy(() -> RetryExecutor.execute(
                    () -> incrementAndRethrow(calls, infra),
                    policyOf(5),
                    "ctx",
                    LOG))
                    .isInstanceOf(Infrastructure5xxException.class);
            assertThat(calls.get()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("run()")
    class Run {

        @Test
        @DisplayName("executes runnable without returning value")
        void executesRunnable() {
            AtomicInteger counter = new AtomicInteger(0);
            RetryExecutor.run(counter::incrementAndGet, policyOf(0), "ctx", LOG);
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("retries runnable on failure")
        void retriesRunnable() {
            AtomicInteger calls = new AtomicInteger(0);

            RetryExecutor.run(
                    () -> {
                        if (calls.incrementAndGet() < 2) throw new RuntimeException("fail");
                    },
                    policyOf(2),
                    "ctx",
                    LOG);

            assertThat(calls.get()).isEqualTo(2);
        }
    }
}