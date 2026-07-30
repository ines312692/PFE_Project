package com.elyadata.feature.proxy.infrastructure.repository;

import com.elyadata.feature.proxy.infrastructure.entity.ProxyHealthCheckEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class ProxyHealthCheckRepository {

    @PersistenceContext
    EntityManager em;

    public void persist(ProxyHealthCheckEntity entity) {
        em.persist(entity);
    }

    public ProxyHealthCheckEntity findById(UUID id) {
        return em.find(ProxyHealthCheckEntity.class, id);
    }

    @Transactional
    public int deleteOlderThan(Instant threshold) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<ProxyHealthCheckEntity> d =
                cb.createCriteriaDelete(ProxyHealthCheckEntity.class);
        Root<ProxyHealthCheckEntity> h = d.from(ProxyHealthCheckEntity.class);
        d.where(cb.lessThan(h.get("checkedAt"), threshold));
        return em.createQuery(d).executeUpdate();
    }
}