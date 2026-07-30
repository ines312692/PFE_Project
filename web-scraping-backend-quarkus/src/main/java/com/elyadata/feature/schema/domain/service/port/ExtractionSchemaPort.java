package com.elyadata.feature.schema.domain.service.port;

import com.elyadata.feature.schema.domain.model.ExtractionSchema;

import java.util.Optional;
import java.util.UUID;

public interface ExtractionSchemaPort {
    ExtractionSchema saveOrUpdate(ExtractionSchema schema);

    Optional<ExtractionSchema> findByJobId(UUID jobId);

    Optional<ExtractionSchema> findById(UUID schemaId);

    void deleteByJobId(UUID jobId);
}