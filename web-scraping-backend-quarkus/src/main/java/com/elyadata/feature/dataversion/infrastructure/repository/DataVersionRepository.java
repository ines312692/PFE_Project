package com.elyadata.feature.dataversion.infrastructure.repository;

import com.elyadata.feature.dataversion.infrastructure.entity.DataVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class DataVersionRepository {

    private final EntityManager em;

    public DataVersionEntity save(DataVersionEntity entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<DataVersionEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(DataVersionEntity.class, id));
    }

    public List<DataVersionEntity> findByScrapeResultIdOrderByVersionNumberAsc(UUID scrapeResultId) {
        return em.createQuery(
                        "SELECT d FROM DataVersionEntity d " +
                                "WHERE d.scrapeResultId = :sid ORDER BY d.versionNumber ASC",
                        DataVersionEntity.class)
                .setParameter("sid", scrapeResultId)
                .getResultList();
    }

    public Optional<DataVersionEntity> findFirstByScrapeResultIdOrderByVersionNumberDesc(UUID scrapeResultId) {
        return em.createQuery(
                        "SELECT d FROM DataVersionEntity d " +
                                "WHERE d.scrapeResultId = :sid ORDER BY d.versionNumber DESC",
                        DataVersionEntity.class)
                .setParameter("sid", scrapeResultId)
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    public Optional<DataVersionEntity> findByScrapeResultIdAndVersionNumber(UUID scrapeResultId, int versionNumber) {
        return em.createQuery(
                        "SELECT d FROM DataVersionEntity d " +
                                "WHERE d.scrapeResultId = :sid AND d.versionNumber = :v",
                        DataVersionEntity.class)
                .setParameter("sid", scrapeResultId)
                .setParameter("v", versionNumber)
                .getResultStream().findFirst();
    }
}