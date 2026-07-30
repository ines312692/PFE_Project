package com.elyadata.feature.job.infrastructure.stream;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ApplicationScoped
public class ScrapeWorkerPool {

    @Getter
    private final int capacity;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public ScrapeWorkerPool(StreamConfig config) {
        this.capacity = Math.max(1, config.jobRun().concurrency());
        this.executor = new ThreadPoolExecutor(
                capacity, capacity, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("scrape-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        log.info("[STREAM] ScrapeWorkerPool initialised — capacity={}", capacity);
    }

    public int freeSlots() {
        return Math.max(0, capacity - inFlight.get());
    }

    public int inFlight() {
        return inFlight.get();
    }

    public boolean submit(Runnable task) {
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            log.warn("[STREAM] pool saturé — tâche rejetée, sera reprise plus tard");
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        log.info("[STREAM] Shutting down ScrapeWorkerPool (inFlight={})", inFlight.get());
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}