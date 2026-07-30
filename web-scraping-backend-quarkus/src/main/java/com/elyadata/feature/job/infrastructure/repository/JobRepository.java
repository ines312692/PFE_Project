package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.JobSearchCriteria;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.infrastructure.entity.JobEntity;
import com.elyadata.feature.user.infrastructure.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class JobRepository {

    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_DELETED = "deleted";

    private final EntityManager em;

    public void persist(JobEntity entity) {
        em.persist(entity);
    }

    public List<JobEntity> listAll() {
        return em.createQuery(
                "SELECT j FROM JobEntity j WHERE j.deleted = false ORDER BY j.createdAt DESC",
                JobEntity.class).getResultList();
    }

    public List<JobEntity> findAllByCreatedBy(UUID createdBy) {
        return em.createQuery(
                "SELECT j FROM JobEntity j WHERE j.createdBy = :createdBy AND j.deleted = false ORDER BY j.createdAt DESC",
                JobEntity.class).setParameter(FIELD_CREATED_BY, createdBy).getResultList();
    }

    public List<JobEntity> findAllByProjectId(UUID projectId) {
        return em.createQuery(
                "SELECT j FROM JobEntity j WHERE j.projectId = :projectId AND j.deleted = false ORDER BY j.createdAt DESC",
                JobEntity.class).setParameter("projectId", projectId).getResultList();
    }

    public Optional<JobEntity> findByIdOptional(UUID id) {
        return em.createQuery(
                "SELECT j FROM JobEntity j WHERE j.id = :id AND j.deleted = false",
                JobEntity.class).setParameter("id", id).getResultStream().findFirst();
    }

    public Optional<JobEntity> findByIdAndCreatedBy(UUID id, UUID createdBy) {
        return em.createQuery(
                        "SELECT j FROM JobEntity j WHERE j.id = :id AND j.createdBy = :createdBy AND j.deleted = false",
                        JobEntity.class)
                .setParameter("id", id)
                .setParameter(FIELD_CREATED_BY, createdBy)
                .getResultStream().findFirst();
    }

    public void deleteById(UUID id) {
        JobEntity entity = em.find(JobEntity.class, id);
        if (entity != null) {
            entity.setDeleted(true);
            entity.setDeletedAt(Instant.now());
        }
    }

    public Page<JobEntity> search(JobSearchCriteria criteria, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<JobEntity> countRoot = countQuery.from(JobEntity.class);
        countQuery.select(cb.count(countRoot))
                .where(buildPredicates(cb, countQuery, countRoot, criteria));

        long total = em.createQuery(countQuery).getSingleResult();
        if (total == 0L) return Page.empty(page, size);

        CriteriaQuery<JobEntity> dataQuery = cb.createQuery(JobEntity.class);
        Root<JobEntity> dataRoot = dataQuery.from(JobEntity.class);
        dataQuery.select(dataRoot)
                .where(buildPredicates(cb, dataQuery, dataRoot, criteria))
                .orderBy(buildOrder(cb, dataRoot, criteria));

        List<JobEntity> content = em.createQuery(dataQuery)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        return new Page<>(content, total, page, size);
    }

    public Map<JobStatus, Long> countByStatus(UUID ownerId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<JobEntity> root = q.from(JobEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FIELD_DELETED)));
        if (ownerId != null) predicates.add(cb.equal(root.get(FIELD_CREATED_BY), ownerId));
        q.select(cb.tuple(
                        root.get(FIELD_STATUS).alias(FIELD_STATUS),
                        cb.count(root).alias("count")))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(root.get(FIELD_STATUS));

        Map<JobStatus, Long> result = new EnumMap<>(JobStatus.class);
        for (JobStatus s : JobStatus.values()) result.put(s, 0L);
        em.createQuery(q).getResultList().forEach(t ->
                result.put((JobStatus) t.get(FIELD_STATUS), (Long) t.get("count")));
        return result;
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb,
                                        CriteriaQuery<?> query,
                                        Root<JobEntity> root,
                                        JobSearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FIELD_DELETED)));

        if (c.ownerId() != null) predicates.add(cb.equal(root.get(FIELD_CREATED_BY), c.ownerId()));
        if (c.projectId() != null) predicates.add(cb.equal(root.get("projectId"), c.projectId()));
        if (c.status() != null) predicates.add(cb.equal(root.get(FIELD_STATUS), c.status()));
        if (c.priority() != null) predicates.add(cb.equal(root.get("priority"), c.priority()));
        if (c.triggerType() != null) predicates.add(cb.equal(root.get("triggerType"), c.triggerType()));
        if (c.jobType() != null) predicates.add(cb.equal(root.get("jobType"), c.jobType()));

        if (c.createdFrom() != null)
            predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_CREATED_AT), c.createdFrom()));
        if (c.createdTo() != null)
            predicates.add(cb.lessThanOrEqualTo(root.get(FIELD_CREATED_AT), c.createdTo()));

        if (c.search() != null && !c.search().isBlank()) {
            String pattern = "%" + c.search().toLowerCase().trim() + "%";

            Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
            Predicate urlMatch = cb.like(cb.lower(root.get("targetUrl")), pattern);

            Subquery<UUID> userSub = query.subquery(UUID.class);
            Root<UserEntity> userRoot = userSub.from(UserEntity.class);
            userSub.select(userRoot.get("id"))
                    .where(cb.like(cb.lower(userRoot.get("username")), pattern));

            Predicate userMatch = root.get(FIELD_CREATED_BY).in(userSub);
            predicates.add(cb.or(nameMatch, urlMatch, userMatch));
        }

        return predicates.toArray(new Predicate[0]);
    }

    private List<Order> buildOrder(CriteriaBuilder cb, Root<JobEntity> root, JobSearchCriteria c) {
        String fieldName = switch (c.sortField()) {
            case UPDATED_AT -> "updatedAt";
            case NAME -> "name";
            case CREATED_AT -> FIELD_CREATED_AT;
        };
        Path<Object> field = root.get(fieldName);
        Order primary = c.sortDirection() == JobSearchCriteria.SortDirection.ASC
                ? cb.asc(field) : cb.desc(field);
        return List.of(primary, cb.asc(root.get("id")));
    }

    public Optional<JobEntity> findByWebhookToken(String token) {
        return em.createQuery(
                        "SELECT j FROM JobEntity j " +
                                "WHERE j.webhookToken = :token " +
                                "AND j.deleted = false",
                        JobEntity.class)
                .setParameter("token", token)
                .getResultStream()
                .findFirst();
    }

    public List<JobEntity> findStaleByStatus(Set<JobStatus> statuses,
                                             Instant updatedBefore,
                                             int limit) {
        return em.createQuery(
                        "SELECT j FROM JobEntity j " +
                                "WHERE j.deleted = false " +
                                "AND j.status IN :statuses " +
                                "AND j.updatedAt < :updatedBefore " +
                                "ORDER BY j.updatedAt ASC",
                        JobEntity.class)
                .setParameter("statuses", statuses)
                .setParameter("updatedBefore", updatedBefore)
                .setMaxResults(limit)
                .getResultList();
    }


}