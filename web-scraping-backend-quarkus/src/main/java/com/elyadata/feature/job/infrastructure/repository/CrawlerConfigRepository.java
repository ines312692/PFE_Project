package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.infrastructure.entity.CrawlerConfigEntity;
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
public class CrawlerConfigRepository {

    private final EntityManager em;

    public void persist(CrawlerConfigEntity entity) {
        em.persist(entity);
    }

    public Optional<CrawlerConfigEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                "SELECT c FROM CrawlerConfigEntity c WHERE c.jobId = :jobId",
                CrawlerConfigEntity.class
        ).setParameter("jobId", jobId).getResultStream().findFirst();
    }

    public void deleteByJobId(UUID jobId) {
        em.createQuery("DELETE FROM CrawlerConfigEntity c WHERE c.jobId = :jobId")
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    public Map<UUID, CrawlerConfigEntity> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        return em.createQuery(
                        "SELECT c FROM CrawlerConfigEntity c WHERE c.jobId IN :jobIds",
                        CrawlerConfigEntity.class)
                .setParameter("jobIds", jobIds)
                .getResultStream()
                .collect(Collectors.toMap(CrawlerConfigEntity::getJobId, c -> c));
    }
}