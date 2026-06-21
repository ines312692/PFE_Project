package com.elyadata.feature.scraper.resilience;

import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import com.elyadata.feature.job.domain.model.RetryPolicy;
import org.slf4j.Logger;

import java.util.Set;

public final class RetryExecutor {

    private RetryExecutor() {
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    public static <T> T execute(
            CheckedSupplier<T> action,
            RetryPolicy policy,
            String context,
            Logger log) {

        int maxRetries = policy != null ? policy.maxRetries() : 0;
        long initialDelay = policy != null ? policy.retryInitialDelayMs() : 1_000L;
        double multiplier = policy != null ? policy.retryBackoffMultiplier() : 2.0;
        long maxDelay = policy != null ? policy.retryMaxDelayMs() : 30_000L;

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.get();

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                        .message("Interrupted during retry execution for: " + context)
                        .uriPath(context)
                        .subErrors(Set.of())
                        .build();

            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    long delay = computeDelay(initialDelay, multiplier, maxDelay, attempt);
                    log.warn("[RETRY] {} — attempt {}/{} failed, retrying in {}ms: {}",
                            context, attempt + 1, maxRetries, delay, e.getMessage());
                    sleep(delay);
                } else {
                    log.error("[RETRY] {} — all {} attempt(s) exhausted: {}",
                            context, maxRetries + 1, e.getMessage());
                }
            }
        }

        throw Infrastructure5xxException.infrastructure5xxExceptionBuilder()
                .message("All " + (maxRetries + 1) + " attempt(s) exhausted for: "
                        + context + " — " + (lastException != null ? lastException.getMessage() : "unknown"))
                .uriPath(context)
                .subErrors(Set.of())
                .build();
    }

    public static void run(
            CheckedRunnable action,
            RetryPolicy policy,
            String context,
            Logger log) {
        execute(() -> {
            action.run();
            return null;
        }, policy, context, log);
    }

    private static long computeDelay(long initial, double mult, long max, int attempt) {
        double raw = initial * Math.pow(mult, attempt);
        return Math.min((long) raw, max);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}