package com.elyadata.feature.job.domain.service.port;

import java.util.UUID;

public interface EtlRunPort {
    void runEtlSync(UUID jobId);
}