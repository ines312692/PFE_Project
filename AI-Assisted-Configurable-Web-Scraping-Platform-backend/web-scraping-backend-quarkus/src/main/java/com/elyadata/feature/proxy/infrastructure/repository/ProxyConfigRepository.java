package com.elyadata.feature.proxy.infrastructure.repository;

import com.elyadata.feature.proxy.infrastructure.entity.ProxyConfigEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProxyConfigRepository {

    @PersistenceContext
    EntityManager em;

    public void persist(ProxyConfigEntity entity) {
        em.persist(entity);
    }

    public void flush() {
        em.flush();
    }

    public Optional<ProxyConfigEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT c FROM ProxyConfigEntity c WHERE c.jobId = :jobId",
                        ProxyConfigEntity.class)
                .setParameter("jobId", jobId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public long deleteByJobId(UUID jobId) {
        return em.createQuery("DELETE FROM ProxyConfigEntity c WHERE c.jobId = :jobId")
                .setParameter("jobId", jobId)
                .executeUpdate();
    }
}