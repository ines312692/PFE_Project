package com.elyadata.feature.proxy.domain.service;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyOutcome;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import com.elyadata.feature.proxy.domain.service.strategy.ProxyRotationStrategy;
import com.elyadata.feature.proxy.domain.service.strategy.ProxyRotationStrategyRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class ProxyPoolServiceImpl implements ProxyPoolService {

    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(60);


    private final ProxyPort proxyPort;


    private final ProxyRotationStrategyRegistry strategyRegistry;

    @ConfigProperty(name = "proxy.lease.minutes", defaultValue = "60")
    int leaseMinutes;

    public ProxyPoolServiceImpl(ProxyPort proxyPort, ProxyRotationStrategyRegistry strategyRegistry) {
        this.proxyPort = proxyPort;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public Optional<Proxy> acquire(ProxyConfig config, UUID jobId, List<UUID> excludedIds) {
        if (config == null || !config.isEnabled()) {
            log.debug("[PROXY-POOL] acquire skipped — config disabled (job={})", jobId);
            return Optional.empty();
        }

        ProxyRotationStrategy strategy = strategyRegistry.get(config.rotationStrategy());
        Instant leasedUntil = Instant.now().plus(leaseDuration());

        Optional<Proxy> picked = tryAcquireWith(config, config.countries(),
                excludedIds, strategy, leasedUntil);

        if (picked.isEmpty() && config.hasCountryFilter() && !config.isStrictGeo()) {
            log.warn("[PROXY-POOL] geo fallback for job={} — no proxy matched countries={}, retrying any-country",
                    jobId, config.countries());
            picked = tryAcquireWith(config, null, excludedIds, strategy, leasedUntil);
        }

        if (picked.isEmpty()) {
            log.warn("[PROXY-POOL] acquire FAILED for job={} (strategy={}, countries={}, strictGeo={}, excluded={})",
                    jobId, strategy.type(), config.countries(), config.isStrictGeo(),
                    excludedIds != null ? excludedIds.size() : 0);
            return Optional.empty();
        }

        Proxy p = picked.get();
        log.info("[PROXY-POOL] acquired {} for job={} (strategy={}, country={}, requested={}, latency={}ms, sr={})",
                p.endpoint(), jobId, strategy.type(), p.country(), config.countries(),
                p.latencyMs(), p.successRate());
        return picked;
    }

    private Optional<Proxy> tryAcquireWith(ProxyConfig config,
                                           List<String> countries,
                                           List<UUID> excludedIds,
                                           ProxyRotationStrategy strategy,
                                           Instant leasedUntil) {
        return proxyPort.tryAcquire(
                countries,
                config.protocols(),
                config.anonymityLevels(),
                config.maxLatencyMs(),
                config.minSuccessRate(),
                excludedIds,
                strategy.orderByClause(),
                leasedUntil);
    }

    @Override
    public void release(UUID proxyId, UUID jobId, ProxyOutcome outcome) {
        if (proxyId == null) return;

        try {
            proxyPort.release(proxyId, outcome.success(), outcome.latencyMs());
            proxyPort.recordUsage(proxyId, jobId, outcome.success(),
                    outcome.latencyMs(), outcome.errorMessage());

            log.info("[PROXY-POOL] released proxy={} job={} success={} latency={}ms",
                    proxyId, jobId, outcome.success(), outcome.latencyMs());

        } catch (Exception e) {
            log.warn("[PROXY-POOL] release failed proxy={}: {}", proxyId, e.getMessage());
        }
    }

    private Duration leaseDuration() {
        return leaseMinutes > 0 ? Duration.ofMinutes(leaseMinutes) : DEFAULT_LEASE;
    }
}