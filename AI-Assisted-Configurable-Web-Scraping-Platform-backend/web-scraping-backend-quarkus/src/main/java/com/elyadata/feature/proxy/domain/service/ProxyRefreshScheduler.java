package com.elyadata.feature.proxy.domain.service;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetectorRegistry;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ProxyRefreshScheduler {

    private final ProxyService proxyService;

    private final ProxyHealthChecker healthChecker;

    private final ProxyPort proxyPort;

    @ConfigProperty(name = "proxy.scheduler.health-batch-size", defaultValue = "100")
    int healthBatchSize;

    @ConfigProperty(name = "proxy.scheduler.health-check-history-days", defaultValue = "7")
    int historyDays;


    @ConfigProperty(name = "proxy.scheduler.cleanup-inactive-days", defaultValue = "3")
    int cleanupInactiveDays;

    private final ProxyAntiBotDetectorRegistry detectorRegistry;

    @ConfigProperty(name = "proxy.scheduler.antibot-batch-size", defaultValue = "20")
    int antiBotBatchSize;

    @ConfigProperty(name = "proxy.scheduler.antibot-recheck-hours", defaultValue = "12")
    int antiBotRecheckHours;

    @Scheduled(every = "30m", delayed = "2m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void antiBotCheckBatch() {
        try {
            for (AntiBotProvider provider : detectorRegistry.registeredProviders()) {
                Instant olderThan = Instant.now().minus(Duration.ofHours(antiBotRecheckHours));
                List<Proxy> candidates = proxyPort.findActiveNeedingAntiBotCheck(
                        provider, olderThan, antiBotBatchSize);
                if (candidates.isEmpty()) continue;

                long startMs = System.currentTimeMillis();
                ProxyHealthChecker.BatchResult r = healthChecker.checkAntiBotBatch(provider, candidates);
                log.info("[PROXY-SCHED] antiBotCheckBatch[{}] — checked={} usable={} unusable={} duration={}ms",
                        provider, r.total(), r.usable(), r.unusable(),
                        System.currentTimeMillis() - startMs);
            }
        } catch (Exception e) {
            log.warn("[PROXY-SCHED] antiBotCheckBatch crashed: {}", e.getMessage());
        }
    }
    public ProxyRefreshScheduler(ProxyService proxyService, ProxyHealthChecker healthChecker, ProxyPort proxyPort, ProxyAntiBotDetectorRegistry detectorRegistry) {
        this.proxyService = proxyService;
        this.healthChecker = healthChecker;
        this.proxyPort = proxyPort;
        this.detectorRegistry = detectorRegistry;
    }

    //@Scheduled(every = "30m", delayed = "1m",
            //concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void refreshPool() {
        try {
            int added = proxyService.refreshPool();
            log.info("[PROXY-SCHED] refreshPool — {} new proxies", added);
        } catch (Exception e) {
            log.warn("[PROXY-SCHED] refreshPool crashed: {}", e.getMessage());
        }
    }

    @Scheduled(every = "5m", delayed = "30s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void healthCheckBatch() {
        try {
            List<Proxy> active = proxyPort.findActive();
            int limit = Math.min(healthBatchSize, active.size());
            if (limit == 0) {
                log.info("[PROXY-SCHED] healthCheckBatch — no active proxies to check");
                return;
            }
            List<Proxy> batch = active.subList(0, limit);

            long startMs = System.currentTimeMillis();
            int ok = healthChecker.checkBatch(batch);
            long durationMs = System.currentTimeMillis() - startMs;

            log.info("[PROXY-SCHED] healthCheckBatch — checked={} ok={} duration={}ms",
                    limit, ok, durationMs);
        } catch (Exception e) {
            log.warn("[PROXY-SCHED] healthCheckBatch crashed: {}", e.getMessage());
        }
    }

    @Scheduled(every = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void clearExpiredLeases() {
        try {
            proxyPort.clearExpiredLeases(Instant.now());
        } catch (Exception e) {
            log.error("[PROXY-SCHED] clearExpiredLeases failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    void purgeHealthHistory() {
        try {
            int days = Math.max(1, historyDays);
            int purged = proxyPort.purgeHealthChecksOlderThan(
                    Instant.now().minus(Duration.ofDays(days)));
            log.info("[PROXY-SCHED] purgeHealthHistory — removed {} rows older than {}d",
                    purged, days);
        } catch (Exception e) {
            log.warn("[PROXY-SCHED] purgeHealthHistory crashed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 30 4 * * ?")
    void cleanupDeadProxies() {
        try {
            Instant threshold = Instant.now().minus(Duration.ofDays(cleanupInactiveDays));
            int deleted = proxyPort.deleteInactiveOlderThan(threshold);
            log.info("[PROXY-SCHED] cleanupDeadProxies — deleted {} INACTIVE proxies older than {}d",
                    deleted, cleanupInactiveDays);
        } catch (Exception e) {
            log.warn("[PROXY-SCHED] cleanupDeadProxies crashed: {}", e.getMessage());
        }
    }
}