package com.elyadata.feature.schema.infrastructure.repository;

import com.elyadata.feature.schema.infrastructure.entity.ExtractionSchemaEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ExtractionSchemaRepository {

    private final EntityManager em;

    public ExtractionSchemaEntity save(ExtractionSchemaEntity entity) {
        em.persist(entity);
        return entity;
    }

    public Optional<ExtractionSchemaEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                "SELECT e FROM ExtractionSchemaEntity e WHERE e.jobId = :jobId",
                ExtractionSchemaEntity.class
        ).setParameter("jobId", jobId).getResultStream().findFirst();
    }

    public ExtractionSchemaEntity saveOrUpdate(ExtractionSchemaEntity entity) {
        return findByJobId(entity.getJobId())
                .map(existing -> {
                    existing.setFields(entity.getFields());
                    existing.setLlmPrompt(entity.getLlmPrompt());
                    existing.setLlmModel(entity.getLlmModel());
                    existing.setLlmOutputFormat(entity.getLlmOutputFormat());
                    existing.setRegexPattern(entity.getRegexPattern());
                    existing.setRegexGroup(entity.getRegexGroup());
                    return existing;
                })
                .orElseGet(() -> save(entity));
    }

    public void deleteByJobId(UUID jobId) {
        em.createQuery(
                "DELETE FROM ExtractionSchemaEntity e WHERE e.jobId = :jobId"
        ).setParameter("jobId", jobId).executeUpdate();
    }

    public Optional<ExtractionSchemaEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(ExtractionSchemaEntity.class, id));
    }
}