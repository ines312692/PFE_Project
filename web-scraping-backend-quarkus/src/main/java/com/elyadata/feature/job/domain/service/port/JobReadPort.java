package com.elyadata.feature.job.domain.service.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface JobReadPort {
    Map<UUID, Long> countByProjectIds(Collection<UUID> projectIds);
}