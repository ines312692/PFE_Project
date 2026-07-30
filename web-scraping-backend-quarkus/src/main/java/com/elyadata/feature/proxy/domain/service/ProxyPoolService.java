package com.elyadata.feature.proxy.domain.service;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.model.ProxyOutcome;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProxyPoolService {

    Optional<Proxy> acquire(ProxyConfig config, UUID jobId, List<UUID> excludedIds);

    void release(UUID proxyId, UUID jobId, ProxyOutcome outcome);
}