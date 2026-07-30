package com.elyadata.common.llm.observability;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;


@Slf4j
@ApplicationScoped
public class LlmCallContext {

    public record Scope(LlmFeature feature, UUID jobId, UUID projectId, UUID userId) {}

    private static final ThreadLocal<Scope> HOLDER = new ThreadLocal<>();

    public static final class Handle implements AutoCloseable {
        private final Scope previous;

        private Handle(Scope previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }


    public Handle open(LlmFeature feature, UUID jobId, UUID projectId, UUID userId) {
        Scope previous = HOLDER.get();
        HOLDER.set(new Scope(feature != null ? feature : LlmFeature.UNKNOWN, jobId, projectId, userId));
        return new Handle(previous);
    }

    public <T> T call(LlmFeature feature, UUID jobId, UUID projectId, UUID userId, Supplier<T> body) {
        try (Handle ignored = open(feature, jobId, projectId, userId)) {
            return body.get();
        }
    }

    public void run(LlmFeature feature, UUID jobId, UUID projectId, UUID userId, Runnable body) {
        try (Handle ignored = open(feature, jobId, projectId, userId)) {
            body.run();
        }
    }

    public Optional<Scope> current() {
        return Optional.ofNullable(HOLDER.get());
    }


    public void assertClean(String where) {
        Scope leaked = HOLDER.get();
        if (leaked != null) {
            log.warn("[LLM][CTX] Scope leaked at {} — feature={} job={}; forcing clear",
                    where, leaked.feature(), leaked.jobId());
            HOLDER.remove();
        }
    }
}