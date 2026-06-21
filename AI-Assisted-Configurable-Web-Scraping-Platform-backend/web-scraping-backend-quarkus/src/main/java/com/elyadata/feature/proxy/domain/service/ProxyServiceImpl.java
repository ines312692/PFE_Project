package com.elyadata.feature.proxy.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;
import com.elyadata.feature.proxy.domain.service.antibot.ProxyAntiBotDetectorRegistry;
import com.elyadata.feature.proxy.domain.service.port.ProxyPort;
import com.elyadata.feature.proxy.domain.service.source.ProxySourceRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@ApplicationScoped
public class ProxyServiceImpl implements ProxyService {

    private final ProxyPort proxyPort;
    private final ProxySourceRegistry sources;
    private final ProxyHealthChecker healthChecker;
    private final ProxyAntiBotDetectorRegistry detectorRegistry;

    public ProxyServiceImpl(ProxyPort proxyPort,
                            ProxySourceRegistry sources,
                            ProxyHealthChecker healthChecker,
                            ProxyAntiBotDetectorRegistry detectorRegistry) {
        this.proxyPort = proxyPort;
        this.sources = sources;
        this.healthChecker = healthChecker;
        this.detectorRegistry = detectorRegistry;
    }

    @Override
    public Page<Proxy> findAll(ProxySearchCriteria criteria, PageParam pageParam) {
        return proxyPort.findAll(criteria, pageParam);
    }

    @Override
    public Proxy findById(UUID id) {
        return proxyPort.findById(id)
                .orElseThrow(() -> DomainException.notFound(
                        "Proxy not found: " + id, "/v1/proxies/" + id));
    }

    @Override
    public List<ProxyAntiBotStatus> getAntiBotStatuses(UUID id) {
        findById(id);
        return proxyPort.findAntiBotStatusesForProxy(id);
    }


    @Override
    @Transactional
    public int refreshPool() {
        long currentCount = proxyPort.countByStatus(ProxyStatus.ACTIVE)
                + proxyPort.countByStatus(ProxyStatus.INACTIVE)
                + proxyPort.countByStatus(ProxyStatus.BLACKLISTED);

        int maxPoolSize = 1500;
        if (currentCount >= maxPoolSize) {
            log.warn("[PROXY-SERVICE] refreshPool skipped — pool at capacity ({}/{})",
                    currentCount, maxPoolSize);
            return 0;
        }

        List<Proxy> fetched = sources.fetchAll();
        if (fetched.isEmpty()) {
            log.warn("[PROXY-SERVICE] refreshPool — sources returned 0 proxies");
            return 0;
        }
        int saved = proxyPort.saveAll(fetched);
        log.info("[PROXY-SERVICE] refreshPool — fetched={} saved(new)={}", fetched.size(), saved);
        return saved;
    }

    @Override
    @Transactional
    public Proxy createManual(String host, Integer port, ProxyProtocol protocol,
                              String username, String password,
                              String country, ProxyAnonymity anonymity) {

        proxyPort.findByEndpoint(host, port, protocol).ifPresent(existing -> {
            throw DomainException.alreadyExists(
                    "Proxy already exists: " + host + ":" + port + " (" + protocol + ")"
            );
        });

        Proxy proxy = Proxy.builder()
                .host(host)
                .port(port)
                .protocol(protocol)
                .username(username)
                .password(password)
                .country(country == null ? null : country.toUpperCase())
                .anonymity(anonymity == null ? ProxyAnonymity.UNKNOWN : anonymity)
                .source(ProxySource.MANUAL)
                .status(ProxyStatus.ACTIVE)
                .successRate(0.0)
                .totalUses(0L)
                .successfulUses(0L)
                .build();

        Proxy saved = proxyPort.save(proxy);
        log.info("[PROXY-SERVICE] manual proxy added: {}", saved.endpoint());
        return saved;
    }

    @Override
    @Transactional
    public void blacklist(UUID id) {
        updateStatus(id, ProxyStatus.BLACKLISTED);
    }

    @Override
    @Transactional
    public void reactivate(UUID id) {
        updateStatus(id, ProxyStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        findById(id);
        proxyPort.delete(id);
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, ProxyStatus status) {
        findById(id);
        proxyPort.updateStatus(id, status);
    }


    @Override
    public ProxyHealthCheck triggerHealthCheck(UUID id) {
        Proxy proxy = findById(id);
        return healthChecker.check(proxy);
    }

    @Override
    public ProxyHealthCheck triggerFullAntiBotCheck(UUID id) {
        Proxy proxy = findById(id);
        return healthChecker.checkFull(proxy);
    }

    @Override
    public DetectionResult triggerSingleAntiBotCheck(UUID id, AntiBotProvider provider) {
        Proxy proxy = findById(id);
        return healthChecker.checkAntiBot(proxy, provider);
    }

    @Override
    public PoolStats stats() {
        long active = proxyPort.countByStatus(ProxyStatus.ACTIVE);
        long inactive = proxyPort.countByStatus(ProxyStatus.INACTIVE);
        long blacklisted = proxyPort.countByStatus(ProxyStatus.BLACKLISTED);
        long manual = proxyPort.countBySource(ProxySource.MANUAL);

        Map<AntiBotProvider, Map<ProtectionStatus, Long>> breakdown = new EnumMap<>(AntiBotProvider.class);
        for (AntiBotProvider p : detectorRegistry.registeredProviders()) {
            Map<ProtectionStatus, Long> perStatus = new EnumMap<>(ProtectionStatus.class);
            for (ProtectionStatus s : ProtectionStatus.values()) {
                perStatus.put(s, proxyPort.countByAntiBotStatus(p, s));
            }
            breakdown.put(p, perStatus);
        }

        long cloudflareClean = breakdown
                .getOrDefault(AntiBotProvider.CLOUDFLARE, Map.of())
                .getOrDefault(ProtectionStatus.CLEAN, 0L);

        return new PoolStats(active, inactive, blacklisted, cloudflareClean, manual, breakdown);
    }

    @Override
    public Map<UUID, List<ProxyAntiBotStatus>> getAntiBotStatusesGrouped(List<UUID> ids) {
        return proxyPort.findAntiBotStatusesForProxies(ids);
    }

    @Override
    public List<AntiBotProvider> registeredAntiBotProviders() {
        return detectorRegistry.registeredProviders().stream().sorted().toList();
    }

    @Override
    public ProxyDashboard dashboard() {
        return proxyPort.fetchDashboard();
    }

    @Override
    public List<ProxyCountryStat> availableCountries() {
        return proxyPort.countByCountry();
    }
}