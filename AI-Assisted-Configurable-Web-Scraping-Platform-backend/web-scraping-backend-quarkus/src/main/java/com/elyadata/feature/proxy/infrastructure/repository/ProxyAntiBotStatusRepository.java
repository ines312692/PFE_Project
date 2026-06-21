package com.elyadata.feature.proxy.infrastructure.repository;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyAntiBotStatusEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProxyAntiBotStatusRepository {

    private static final String FIELD_PROXY_ID = "proxyId";
    private static final String FIELD_PROVIDER = "provider";
    private static final String FIELD_STATUS = "status";

    @PersistenceContext
    EntityManager em;

    public void persist(ProxyAntiBotStatusEntity entity) {
        em.persist(entity);
    }

    public List<ProxyAntiBotStatusEntity> findByProxyId(UUID proxyId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProxyAntiBotStatusEntity> q =
                cb.createQuery(ProxyAntiBotStatusEntity.class);
        Root<ProxyAntiBotStatusEntity> s = q.from(ProxyAntiBotStatusEntity.class);
        q.select(s).where(cb.equal(s.get(FIELD_PROXY_ID), proxyId));
        return em.createQuery(q).getResultList();
    }

    public List<ProxyAntiBotStatusEntity> findByProxyIds(List<UUID> proxyIds) {
        if (proxyIds == null || proxyIds.isEmpty()) return List.of();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProxyAntiBotStatusEntity> q =
                cb.createQuery(ProxyAntiBotStatusEntity.class);
        Root<ProxyAntiBotStatusEntity> s = q.from(ProxyAntiBotStatusEntity.class);
        q.select(s).where(s.get(FIELD_PROXY_ID).in(proxyIds));
        return em.createQuery(q).getResultList();
    }

    public Optional<ProxyAntiBotStatusEntity> findOne(UUID proxyId, AntiBotProvider provider) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProxyAntiBotStatusEntity> q =
                cb.createQuery(ProxyAntiBotStatusEntity.class);
        Root<ProxyAntiBotStatusEntity> s = q.from(ProxyAntiBotStatusEntity.class);
        q.select(s).where(
                cb.equal(s.get(FIELD_PROXY_ID), proxyId),
                cb.equal(s.get(FIELD_PROVIDER), provider)
        );
        return em.createQuery(q).getResultStream().findFirst();
    }

    public long countByProviderAndStatus(AntiBotProvider provider, ProtectionStatus status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<ProxyAntiBotStatusEntity> s = q.from(ProxyAntiBotStatusEntity.class);
        q.select(cb.count(s)).where(
                cb.equal(s.get(FIELD_PROVIDER), provider),
                cb.equal(s.get(FIELD_STATUS), status)
        );
        return em.createQuery(q).getSingleResult();
    }
}