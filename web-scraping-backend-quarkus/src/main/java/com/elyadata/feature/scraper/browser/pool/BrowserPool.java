package com.elyadata.feature.scraper.browser.pool;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.microsoft.playwright.Browser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class BrowserPool {

    private final BrowserPoolConfig config;
    private final BrowserPoolMetrics metrics;

    @ConfigProperty(name = "scraper.browser.headless", defaultValue = "true")
    boolean headless;

    private BlockingQueue<BrowserSlot> available;
    private List<BrowserSlot> allSlots;

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            log.warn("[POOL] désactivé — un Chrome par job (mode legacy)");
            return;
        }
        int n = Math.max(1, config.maxBrowsers());
        available = new ArrayBlockingQueue<>(n);
        allSlots = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            BrowserSlot slot = new BrowserSlot(i, headless, config.maxContextReuse());
            allSlots.add(slot);
            available.add(slot);
        }
        metrics.registerCapacity(n);
        log.info("[POOL] initialisé — maxBrowsers={} maxContextReuse={} headless={}",
                n, config.maxContextReuse(), headless);
    }

    public boolean isEnabled() {
        return config.enabled();
    }


    public PooledContext acquire(Proxy proxy,
                                 boolean sidecarMode,
                                 String sidecarUrl,
                                 Browser.NewContextOptions ctxOptions,
                                 boolean exclusive) {
        long waitStart = System.nanoTime();
        BrowserSlot slot;
        metrics.waitStarted();
        try {
            slot = takeSlot();
        } finally {
            metrics.waitEnded();
        }

        metrics.recordWait(System.nanoTime() - waitStart);
        metrics.slotAcquired();

        try {
            var ctx = slot.newContext(proxy, sidecarMode, sidecarUrl, ctxOptions);
            return new PooledContext(ctx, slot, this, exclusive);
        } catch (RuntimeException e) {
            slot.releaseContext(null, true);
            available.offer(slot);
            metrics.slotReleased();
            throw e;
        }
    }

    void release(PooledContext handle) {
        BrowserSlot slot = handle.getSlot();
        try {
            boolean recycle = handle.isExclusive() || handle.isPoisoned();
            slot.releaseContext(handle.getContext(), recycle);
            if (recycle) {
                metrics.recycled();
            }
        } catch (Exception e) {
            log.warn("[POOL][slot-{}] restitution en erreur: {}", slot.getIndex(), e.getMessage());
        } finally {
            available.offer(slot);
            metrics.slotReleased();
        }
    }

    private BrowserSlot takeSlot() {
        try {
            BrowserSlot slot = available.poll(config.acquireTimeoutMs(), TimeUnit.MILLISECONDS);
            if (slot == null) {
                metrics.acquireTimeout();
                throw new IllegalStateException(
                        "Aucun slot navigateur disponible après " + config.acquireTimeoutMs()
                                + "ms (maxBrowsers=" + config.maxBrowsers() + ")");
            }
            return slot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attente d'un slot navigateur interrompue", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (allSlots == null) {
            return;
        }
        log.info("[POOL] arrêt de {} slot(s)", allSlots.size());
        allSlots.forEach(BrowserSlot::shutdown);
    }

}