package com.elyadata.feature.job.domain.service.port;

import java.util.UUID;

public interface JobDispatchPort {
    void dispatch(UUID jobId);
    void dispatchEtl(UUID jobId);
}