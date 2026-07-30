package com.elyadata.feature.etl.domain.service.port;

import com.elyadata.feature.etl.domain.model.EtlConfig;

import java.util.Optional;
import java.util.UUID;

public interface EtlConfigPort {
    Optional<EtlConfig> findByJobId(UUID jobId);

    void save(UUID jobId, EtlConfig config);
}