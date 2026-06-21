package com.elyadata.feature.audit.infrastructure.repository;

import com.elyadata.feature.audit.domain.model.AuditLogSearchCriteria;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.infrastructure.entity.AuditLogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AuditLogRepository {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_ENDPOINT = "endpoint";
    private static final String FIELD_RESOURCE_ID = "resourceId";
    private static final String FIELD_RESOURCE_TYPE = "resourceType";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_OCCURRED_AT = "occurredAt";

    @PersistenceContext
    EntityManager em;

    public void persist(AuditLogEntity entity) {
        em.persist(entity);
    }

    public AuditLogEntity findById(UUID id) {
        return em.find(AuditLogEntity.class, id);
    }

    public long countAll() {
        return em.createQuery(
                        "SELECT COUNT(a) FROM AuditLogEntity a", Long.class)
                .getSingleResult();
    }

    public long countSince(Instant from) {
        return em.createQuery(
                        "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.occurredAt >= :from",
                        Long.class)
                .setParameter("from", from)
                .getSingleResult();
    }

    public long countByOutcomeSince(AuditOutcome outcome, Instant from) {
        return em.createQuery(
                        "SELECT COUNT(a) FROM AuditLogEntity a " +
                                "WHERE a.outcome = :outcome AND a.occurredAt >= :from",
                        Long.class)
                .setParameter("outcome", outcome)
                .setParameter("from", from)
                .getSingleResult();
    }

    public long countDistinctUsersSince(Instant from) {
        return em.createQuery(
                        "SELECT COUNT(DISTINCT a.userId) FROM AuditLogEntity a " +
                                "WHERE a.occurredAt >= :from AND a.userId IS NOT NULL",
                        Long.class)
                .setParameter("from", from)
                .getSingleResult();
    }

    public long countByCriteria(AuditLogSearchCriteria criteria) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<AuditLogEntity> a = cq.from(AuditLogEntity.class);
        cq.select(cb.count(a)).where(buildPredicates(cb, a, criteria));
        return em.createQuery(cq).getSingleResult();
    }

    public List<AuditLogEntity> searchByCriteria(
            AuditLogSearchCriteria criteria,
            int page,
            int size) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AuditLogEntity> cq = cb.createQuery(AuditLogEntity.class);
        Root<AuditLogEntity> a = cq.from(AuditLogEntity.class);

        cq.where(buildPredicates(cb, a, criteria));
        cq.orderBy(buildOrder(cb, a, criteria));

        return em.createQuery(cq)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public boolean deleteById(UUID id) {
        AuditLogEntity entity = em.find(AuditLogEntity.class, id);
        if (entity == null) return false;
        em.remove(entity);
        return true;
    }

    private Predicate[] buildPredicates(
            CriteriaBuilder cb,
            Root<AuditLogEntity> a,
            AuditLogSearchCriteria criteria) {

        List<Predicate> predicates = new ArrayList<>();

        if (criteria.search() != null && !criteria.search().isBlank()) {
            String pattern = "%" + criteria.search().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(a.get(FIELD_USERNAME)), pattern),
                    cb.like(cb.lower(a.get(FIELD_ENDPOINT)), pattern),
                    cb.like(cb.lower(a.get(FIELD_RESOURCE_ID)), pattern)
            ));
        }
        if (criteria.userId() != null) {
            predicates.add(cb.equal(a.get(FIELD_USER_ID), criteria.userId()));
        }
        if (criteria.action() != null) {
            predicates.add(cb.equal(a.get(FIELD_ACTION), criteria.action()));
        }
        if (criteria.resourceType() != null) {
            predicates.add(cb.equal(a.get(FIELD_RESOURCE_TYPE), criteria.resourceType()));
        }
        if (criteria.resourceId() != null && !criteria.resourceId().isBlank()) {
            predicates.add(cb.equal(a.get(FIELD_RESOURCE_ID), criteria.resourceId()));
        }
        if (criteria.outcome() != null) {
            predicates.add(cb.equal(a.get(FIELD_OUTCOME), criteria.outcome()));
        }
        if (criteria.occurredFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(a.get(FIELD_OCCURRED_AT), criteria.occurredFrom()));
        }
        if (criteria.occurredTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(a.get(FIELD_OCCURRED_AT), criteria.occurredTo()));
        }

        return predicates.toArray(new Predicate[0]);
    }

    private Order buildOrder(
            CriteriaBuilder cb,
            Root<AuditLogEntity> a,
            AuditLogSearchCriteria criteria) {

        String field = switch (criteria.sortField() != null
                ? criteria.sortField()
                : AuditLogSearchCriteria.SortField.OCCURRED_AT) {
            case ACTION -> FIELD_ACTION;
            case RESOURCE_TYPE -> FIELD_RESOURCE_TYPE;
            case OUTCOME -> FIELD_OUTCOME;
            default -> FIELD_OCCURRED_AT;
        };

        Expression<?> path = a.get(field);

        return criteria.sortDirection() == AuditLogSearchCriteria.SortDirection.ASC
                ? cb.asc(path)
                : cb.desc(path);
    }
}