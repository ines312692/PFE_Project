package com.elyadata.feature.scraper.browser.session.repository;

import com.elyadata.feature.scraper.browser.session.entity.BrowserSessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor
public class BrowserSessionRepository {

    private final EntityManager em;

    public void persist(BrowserSessionEntity entity) {
        em.persist(entity);
    }

    public BrowserSessionEntity merge(BrowserSessionEntity entity) {
        return em.merge(entity);
    }

    public Optional<BrowserSessionEntity> findByDomain(String domain) {
        return em.createQuery(
                        "SELECT s FROM BrowserSessionEntity s WHERE s.domain = :domain",
                        BrowserSessionEntity.class)
                .setParameter("domain", domain)
                .getResultStream()
                .findFirst();
    }

    public void deleteByDomain(String domain) {
        em.createQuery(
                        "DELETE FROM BrowserSessionEntity s WHERE s.domain = :domain")
                .setParameter("domain", domain)
                .executeUpdate();
    }
}