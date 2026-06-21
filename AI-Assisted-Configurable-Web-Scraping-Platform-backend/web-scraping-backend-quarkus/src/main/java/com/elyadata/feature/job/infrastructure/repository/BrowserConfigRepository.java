package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.infrastructure.entity.BrowserConfigEntity;
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
public class BrowserConfigRepository {

    private final EntityManager em;

    public void persist(BrowserConfigEntity entity) {
        em.persist(entity);
    }

    public Optional<BrowserConfigEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT b FROM BrowserConfigEntity b WHERE b.jobId = :jobId",
                        BrowserConfigEntity.class)
                .setParameter("jobId", jobId)
                .getResultStream()
                .findFirst();
    }

    public void deleteByJobId(UUID jobId) {
        em.createQuery("DELETE FROM BrowserConfigEntity b WHERE b.jobId = :jobId")
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    public Map<UUID, BrowserConfigEntity> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        return em.createQuery(
                        "SELECT b FROM BrowserConfigEntity b WHERE b.jobId IN :jobIds",
                        BrowserConfigEntity.class)
                .setParameter("jobIds", jobIds)
                .getResultStream()
                .collect(Collectors.toMap(BrowserConfigEntity::getJobId, b -> b));
    }
}