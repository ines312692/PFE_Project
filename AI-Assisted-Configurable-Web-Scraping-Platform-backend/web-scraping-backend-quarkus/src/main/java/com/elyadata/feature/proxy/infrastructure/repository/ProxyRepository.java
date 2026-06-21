package com.elyadata.feature.proxy.infrastructure.repository;

import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyAntiBotStatusEntity;
import com.elyadata.feature.proxy.infrastructure.entity.ProxyEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProxyRepository {
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LAST_CHECKED_AT = "lastCheckedAt";
    private static final String FIELD_LEASED_UNTIL = "leasedUntil";

    @PersistenceContext
    EntityManager em;

    public void persist(ProxyEntity entity) {
        em.persist(entity);
    }

    public ProxyEntity findById(UUID id) {
        return em.find(ProxyEntity.class, id);
    }

    public void deleteById(UUID id) {
        ProxyEntity e = em.find(ProxyEntity.class, id);
        if (e != null) em.remove(e);
    }

    public Optional<ProxyEntity> findByEndpoint(String host, int port, ProxyProtocol protocol) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProxyEntity> q = cb.createQuery(ProxyEntity.class);
        Root<ProxyEntity> p = q.from(ProxyEntity.class);
        q.select(p).where(
                cb.equal(p.get("host"), host),
                cb.equal(p.get("port"), port),
                cb.equal(p.get("protocol"), protocol)
        );
        return em.createQuery(q).getResultStream().findFirst();
    }

    public SearchResult search(ProxySearchCriteria criteria, int pageIndex, int pageSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<ProxyEntity> countRoot = countQ.from(ProxyEntity.class);
        countQ.select(cb.count(countRoot))
                .where(buildPredicates(cb, countQ, countRoot, criteria));
        long total = em.createQuery(countQ).getSingleResult();

        CriteriaQuery<ProxyEntity> dataQ = cb.createQuery(ProxyEntity.class);
        Root<ProxyEntity> root = dataQ.from(ProxyEntity.class);
        dataQ.select(root)
                .where(buildPredicates(cb, dataQ, root, criteria))
                .orderBy(buildOrder(cb, root, criteria));

        List<ProxyEntity> rows = em.createQuery(dataQ)
                .setFirstResult(pageIndex * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        return new SearchResult(rows, total);
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb,
                                        CriteriaQuery<?> query,
                                        Root<ProxyEntity> p,
                                        ProxySearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();

        if (c.search() != null && !c.search().isBlank()) {
            String like = "%" + c.search().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(p.get("host")), like),
                    cb.like(cb.lower(p.get("country")), like)
            ));
            if (c.country() != null && !c.country().isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(p.get("country")),
                        c.country().toUpperCase()
                ));
            }
        }
        if (c.status() != null) predicates.add(cb.equal(p.get(FIELD_STATUS), c.status()));
        if (c.protocol() != null) predicates.add(cb.equal(p.get("protocol"), c.protocol()));
        if (c.source() != null) predicates.add(cb.equal(p.get("source"), c.source()));

        if (c.antiBotProvider() != null || c.antiBotStatus() != null) {
            Subquery<UUID> sq = query.subquery(UUID.class);
            Root<ProxyAntiBotStatusEntity> s = sq.from(ProxyAntiBotStatusEntity.class);
            sq.select(s.get("proxyId"));
            List<Predicate> subPredicates = new ArrayList<>();
            if (c.antiBotProvider() != null) subPredicates.add(cb.equal(s.get("provider"), c.antiBotProvider()));
            if (c.antiBotStatus() != null) subPredicates.add(cb.equal(s.get(FIELD_STATUS), c.antiBotStatus()));
            sq.where(subPredicates.toArray(new Predicate[0]));
            predicates.add(p.get("id").in(sq));
        }

        return predicates.toArray(new Predicate[0]);
    }

    private List<Order> buildOrder(CriteriaBuilder cb,
                                   Root<ProxyEntity> p,
                                   ProxySearchCriteria c) {
        String field = switch (c.sortBy() == null ? "createdAt" : c.sortBy()) {
            case "latency" -> "latencyMs";
            case "lastChecked" -> FIELD_LAST_CHECKED_AT;
            case "successRate" -> "successRate";
            case "host" -> "host";
            default -> "createdAt";
        };
        boolean asc = "asc".equalsIgnoreCase(c.sortOrder());
        return List.of(asc ? cb.asc(p.get(field)) : cb.desc(p.get(field)));
    }

    public List<ProxyEntity> findActiveOldestCheckedFirst() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProxyEntity> q = cb.createQuery(ProxyEntity.class);
        Root<ProxyEntity> p = q.from(ProxyEntity.class);
        q.select(p)
                .where(cb.equal(p.get(FIELD_STATUS), ProxyStatus.ACTIVE))
                .orderBy(
                        cb.desc(cb.selectCase()
                                .when(cb.isNull(p.get(FIELD_LAST_CHECKED_AT)), 1)
                                .otherwise(0)),
                        cb.asc(p.get(FIELD_LAST_CHECKED_AT))
                );
        return em.createQuery(q).getResultList();
    }

    public long countByStatus(ProxyStatus status) {
        return countByEqual(FIELD_STATUS, status);
    }

    public long countBySource(ProxySource source) {
        return countByEqual("source", source);
    }

    public List<ProxyCountryStat> countByCountry() {
        return em.createQuery("""
                        SELECT new com.elyadata.feature.proxy.domain.model.ProxyCountryStat(
                            p.country, COUNT(p))
                        FROM ProxyEntity p
                        WHERE p.country IS NOT NULL
                          AND p.status = :status
                        GROUP BY p.country
                        ORDER BY COUNT(p) DESC
                        """, ProxyCountryStat.class)
                .setParameter(FIELD_STATUS, ProxyStatus.ACTIVE)
                .getResultList();
    }

    public record CountryCount(String code, long count) {
    }

    private long countByEqual(String field, Object value) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<ProxyEntity> p = q.from(ProxyEntity.class);
        q.select(cb.count(p)).where(cb.equal(p.get(field), value));
        return em.createQuery(q).getSingleResult();
    }

    @Transactional
    public void updateStatus(UUID id, ProxyStatus status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<ProxyEntity> u = cb.createCriteriaUpdate(ProxyEntity.class);
        Root<ProxyEntity> p = u.from(ProxyEntity.class);
        u.set(p.get(FIELD_STATUS), status).where(cb.equal(p.get("id"), id));
        em.createQuery(u).executeUpdate();
    }

    @Transactional
    public void clearExpiredLeases(Instant now) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<ProxyEntity> u = cb.createCriteriaUpdate(ProxyEntity.class);
        Root<ProxyEntity> p = u.from(ProxyEntity.class);
        u.set(p.get(FIELD_LEASED_UNTIL), (Instant) null)
                .where(cb.and(
                        cb.isNotNull(p.get(FIELD_LEASED_UNTIL)),
                        cb.lessThan(p.get(FIELD_LEASED_UNTIL), now)   // type inferred
                ));
        em.createQuery(u).executeUpdate();
    }

    @Transactional
    public int deleteInactiveOlderThan(Instant threshold) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<ProxyEntity> d = cb.createCriteriaDelete(ProxyEntity.class);
        Root<ProxyEntity> p = d.from(ProxyEntity.class);
        d.where(cb.and(
                cb.equal(p.get(FIELD_STATUS), ProxyStatus.INACTIVE),
                cb.isNotNull(p.get(FIELD_LAST_CHECKED_AT)),
                cb.lessThan(p.get(FIELD_LAST_CHECKED_AT), threshold)  // type inferred
        ));
        return em.createQuery(d).executeUpdate();
    }

    public record SearchResult(List<ProxyEntity> content, long total) {
    }
}