package com.elyadata.feature.schema.infrastructure.adapter;

import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.elyadata.feature.schema.infrastructure.entity.ExtractionSchemaEntity;
import com.elyadata.feature.schema.infrastructure.mapper.ExtractionSchemaEntityMapper;
import com.elyadata.feature.schema.infrastructure.repository.ExtractionSchemaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Transactional
@ApplicationScoped
@RequiredArgsConstructor
public class ExtractionSchemaAdapter implements ExtractionSchemaPort {

    private final ExtractionSchemaRepository repository;
    private final ExtractionSchemaEntityMapper mapper;

    @Override
    public ExtractionSchema saveOrUpdate(ExtractionSchema schema) {
        ExtractionSchemaEntity entity = mapper.toEntity(schema);
        ExtractionSchemaEntity saved = repository.saveOrUpdate(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ExtractionSchema> findByJobId(UUID jobId) {
        Optional<ExtractionSchemaEntity> entity = repository.findByJobId(jobId);
        return entity.map(mapper::toDomain);
    }

    @Override
    public void deleteByJobId(UUID jobId) {
        repository.deleteByJobId(jobId);
    }

    @Override
    public Optional<ExtractionSchema> findById(UUID schemaId) {
        return repository.findById(schemaId)
                .map(mapper::toDomain);
    }
}