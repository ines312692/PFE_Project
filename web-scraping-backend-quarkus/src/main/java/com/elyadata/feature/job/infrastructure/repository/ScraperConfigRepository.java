package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.infrastructure.entity.ScraperConfigEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class ScraperConfigRepository {

    private final EntityManager em;

    public void persist(ScraperConfigEntity entity) {
        em.persist(entity);
    }

    public Optional<ScraperConfigEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                "SELECT s FROM ScraperConfigEntity s WHERE s.jobId = :jobId",
                ScraperConfigEntity.class
        ).setParameter("jobId", jobId).getResultStream().findFirst();
    }

    public void deleteByJobId(UUID jobId) {
        em.createQuery("DELETE FROM ScraperConfigEntity s WHERE s.jobId = :jobId")
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    public Map<UUID, ScraperConfigEntity> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        return em.createQuery(
                        "SELECT s FROM ScraperConfigEntity s WHERE s.jobId IN :jobIds",
                        ScraperConfigEntity.class)
                .setParameter("jobIds", jobIds)
                .getResultStream()
                .collect(Collectors.toMap(ScraperConfigEntity::getJobId, s -> s));
    }
}