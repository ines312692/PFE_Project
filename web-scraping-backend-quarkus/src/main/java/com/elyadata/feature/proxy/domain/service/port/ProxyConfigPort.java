package com.elyadata.feature.proxy.domain.service.port;

import com.elyadata.feature.proxy.domain.model.ProxyConfig;

import java.util.Optional;
import java.util.UUID;

public interface ProxyConfigPort {
    ProxyConfig saveOrUpdate(UUID jobId, ProxyConfig config);

    Optional<ProxyConfig> findByJobId(UUID jobId);

    void deleteByJobId(UUID jobId);
}