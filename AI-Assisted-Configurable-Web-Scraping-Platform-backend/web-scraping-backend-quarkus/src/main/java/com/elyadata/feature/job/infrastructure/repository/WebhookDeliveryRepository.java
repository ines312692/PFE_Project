package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.infrastructure.entity.WebhookDeliveryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class WebhookDeliveryRepository {

    private final EntityManager em;

    public void persist(WebhookDeliveryEntity entity) {
        em.persist(entity);
    }

    public long countByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT COUNT(d) FROM WebhookDeliveryEntity d WHERE d.jobId = :jobId",
                        Long.class)
                .setParameter("jobId", jobId)
                .getSingleResult();
    }

    public List<WebhookDeliveryEntity> findByJobId(UUID jobId, int page, int size) {
        return em.createQuery(
                        "SELECT d FROM WebhookDeliveryEntity d " +
                                "WHERE d.jobId = :jobId " +
                                "ORDER BY d.receivedAt DESC",
                        WebhookDeliveryEntity.class)
                .setParameter("jobId", jobId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public int deleteOlderThan(Instant cutoff) {
        return em.createQuery(
                        "DELETE FROM WebhookDeliveryEntity d WHERE d.receivedAt < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}