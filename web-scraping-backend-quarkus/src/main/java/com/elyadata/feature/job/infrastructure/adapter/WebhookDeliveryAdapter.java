package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.WebhookDelivery;
import com.elyadata.feature.job.domain.service.port.WebhookDeliveryPort;
import com.elyadata.feature.job.infrastructure.mapper.WebhookDeliveryEntityMapper;
import com.elyadata.feature.job.infrastructure.repository.WebhookDeliveryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class WebhookDeliveryAdapter implements WebhookDeliveryPort {

    private final WebhookDeliveryRepository repository;
    private final WebhookDeliveryEntityMapper mapper;

    @Override
    @Transactional
    public WebhookDelivery save(WebhookDelivery delivery) {
        var entity = mapper.toEntity(delivery);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        repository.persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Page<WebhookDelivery> findByJobId(UUID jobId, int page, int size) {
        long total = repository.countByJobId(jobId);
        if (total == 0L) return Page.empty(page, size);

        var content = repository.findByJobId(jobId, page, size).stream()
                .map(mapper::toDomain)
                .toList();
        return new Page<>(content, total, page, size);
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}