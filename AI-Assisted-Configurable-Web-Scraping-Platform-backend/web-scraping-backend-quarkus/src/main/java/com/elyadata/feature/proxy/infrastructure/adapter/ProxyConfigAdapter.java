package com.elyadata.feature.proxy.infrastructure.adapter;

import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.service.port.ProxyConfigPort;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyConfigEntity;
import com.elyadata.feature.proxy.infrastructure.mapper.ProxyEntityMapper;
import com.elyadata.feature.proxy.infrastructure.repository.ProxyConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;


@ApplicationScoped
public class ProxyConfigAdapter implements ProxyConfigPort {

    private final ProxyConfigRepository repository;
    private final ProxyEntityMapper mapper;

    public ProxyConfigAdapter(ProxyConfigRepository repository, ProxyEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProxyConfig saveOrUpdate(UUID jobId, ProxyConfig config) {
        ProxyConfig safe = config != null ? config : ProxyConfig.disabled();
        ProxyConfigEntity entity = repository.findByJobId(jobId).orElse(null);

        if (entity == null) {
            entity = mapper.toEntity(safe, jobId);
            repository.persist(entity);
        } else {
            ProxyConfigEntity updated = mapper.toEntity(safe, jobId);
            entity.setEnabled(updated.getEnabled());
            entity.setRotationStrategy(updated.getRotationStrategy());
            entity.setCountries(updated.getCountries());
            entity.setProtocols(updated.getProtocols());
            entity.setAnonymityLevels(updated.getAnonymityLevels());
            entity.setMaxLatencyMs(updated.getMaxLatencyMs());
            entity.setMinSuccessRate(updated.getMinSuccessRate());
            repository.flush();
        }
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<ProxyConfig> findByJobId(UUID jobId) {
        return repository.findByJobId(jobId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByJobId(UUID jobId) {
        repository.deleteByJobId(jobId);
    }
}