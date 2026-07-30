package com.elyadata.feature.scraperesult.infrastructure.repository;

import com.elyadata.feature.scraperesult.infrastructure.entity.ScrapeResultEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ScrapeResultRepository {

    private final EntityManager em;

    public ScrapeResultEntity save(ScrapeResultEntity entity) {
        em.persist(entity);
        return entity;
    }

    public Optional<ScrapeResultEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(ScrapeResultEntity.class, id));
    }

    public Optional<ScrapeResultEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT r FROM ScrapeResultEntity r WHERE r.jobId = :jobId ORDER BY r.scrapedAt DESC",
                        ScrapeResultEntity.class)
                .setParameter("jobId", jobId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public List<ScrapeResultEntity> findAllByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT r FROM ScrapeResultEntity r WHERE r.jobId = :jobId ORDER BY r.scrapedAt DESC",
                        ScrapeResultEntity.class)
                .setParameter("jobId", jobId)
                .getResultList();
    }
}