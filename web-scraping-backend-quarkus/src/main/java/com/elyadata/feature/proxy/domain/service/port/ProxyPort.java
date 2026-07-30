package com.elyadata.feature.proxy.domain.service.port;

import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.notification.application.controller.NotificationController;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProxyPort {

    Proxy save(Proxy proxy);

    int saveAll(List<Proxy> proxies);

    Optional<Proxy> findById(UUID id);

    Optional<Proxy> findByEndpoint(String host, Integer port, ProxyProtocol protocol);

    Page<Proxy> findAll(ProxySearchCriteria criteria, PageParam pageParam);

    List<Proxy> findActive();

    void delete(UUID id);

    int deleteInactiveOlderThan(Instant threshold);

    Optional<Proxy> tryAcquire(List<String> countries,
                               List<ProxyProtocol> protocols,
                               List<ProxyAnonymity> anonymityLevels,
                               Integer maxLatencyMs,
                               Double minSuccessRate,
                               List<UUID> excludedIds,
                               String orderByClause,
                               Instant leasedUntil);

    void release(UUID proxyId, boolean success, Integer latencyMs);

    void recordUsage(UUID proxyId, UUID jobId, boolean success,
                     Integer latencyMs, String errorMessage);

    void clearExpiredLeases(Instant now);

    void updateStatus(UUID id, ProxyStatus status);

    void saveHealthCheck(ProxyHealthCheck check);

    int purgeHealthChecksOlderThan(Instant threshold);

    void upsertAntiBotStatus(ProxyAntiBotStatus status);

    List<ProxyAntiBotStatus> findAntiBotStatusesForProxy(UUID proxyId);

    Map<UUID, List<ProxyAntiBotStatus>> findAntiBotStatusesForProxies(List<UUID> proxyIds);

    List<Proxy> findActiveNeedingAntiBotCheck(AntiBotProvider provider,
                                              Instant olderThan,
                                              int limit);

    long countByStatus(ProxyStatus status);

    long countBySource(ProxySource source);

    long countByAntiBotStatus(AntiBotProvider provider, ProtectionStatus status);

    ProxyDashboard fetchDashboard();

    List<ProxyCountryStat> countByCountry();


}