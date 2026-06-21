package com.elyadata.feature.job.domain.service.port;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.WebhookDelivery;

import java.util.UUID;

public interface WebhookDeliveryPort {

    WebhookDelivery save(WebhookDelivery delivery);

    Page<WebhookDelivery> findByJobId(UUID jobId, int page, int size);

    int deleteOlderThan(java.time.Instant cutoff);
}