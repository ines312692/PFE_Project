package com.elyadata.feature.proxy.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.domain.model.antibot.ProxyAntiBotStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProxyService {

    Page<Proxy> findAll(ProxySearchCriteria criteria, PageParam pageParam);

    Proxy findById(UUID id);

    List<ProxyAntiBotStatus> getAntiBotStatuses(UUID id);

    int refreshPool();

    Proxy createManual(String host, Integer port, ProxyProtocol protocol,
                       String username, String password,
                       String country, ProxyAnonymity anonymity);

    void blacklist(UUID id);

    void reactivate(UUID id);

    void delete(UUID id);

    void updateStatus(UUID id, ProxyStatus status);

    ProxyHealthCheck triggerHealthCheck(UUID id);

    ProxyHealthCheck triggerFullAntiBotCheck(UUID id);

    DetectionResult triggerSingleAntiBotCheck(UUID id, AntiBotProvider provider);

    PoolStats stats();


    record PoolStats(
            long active,
            long inactive,
            long blacklisted,
            long cloudflareClean,
            long manual,
            Map<AntiBotProvider, Map<ProtectionStatus, Long>> antiBotBreakdown
    ) {
        public long total() {
            return active + inactive + blacklisted;
        }
    }

    Map<UUID, List<ProxyAntiBotStatus>> getAntiBotStatusesGrouped(List<UUID> ids);

    List<AntiBotProvider> registeredAntiBotProviders();

    ProxyDashboard dashboard();


    List<ProxyCountryStat> availableCountries();

}