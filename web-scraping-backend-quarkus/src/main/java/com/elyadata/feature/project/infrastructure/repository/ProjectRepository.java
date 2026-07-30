package com.elyadata.feature.project.infrastructure.repository;

import com.elyadata.feature.project.infrastructure.entity.ProjectEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.ProjectSearchCriteria;
import com.elyadata.feature.project.domain.model.ProjectStatus;
import com.elyadata.feature.user.infrastructure.entity.UserEntity;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;


@ApplicationScoped
@RequiredArgsConstructor
public class ProjectRepository {

    private final EntityManager em;

    public void persist(ProjectEntity entity) {
        em.persist(entity);
    }

    public Optional<ProjectEntity> findByIdOptional(UUID id) {
        return em.createQuery(
                        "SELECT p FROM ProjectEntity p WHERE p.id = :id AND p.deleted = false",
                        ProjectEntity.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<ProjectEntity> listAll() {
        return em.createQuery(
                        "SELECT p FROM ProjectEntity p WHERE p.deleted = false ORDER BY p.createdAt DESC",
                        ProjectEntity.class)
                .getResultList();
    }

    public List<ProjectEntity> findByMemberId(UUID userId) {
        return em.createQuery(
                        """
                                SELECT p FROM ProjectEntity p
                                WHERE p.deleted = false
                                  AND EXISTS (
                                      SELECT 1 FROM ProjectMemberEntity m
                                      WHERE m.projectId = p.id
                                        AND m.userId = :userId
                                  )
                                ORDER BY p.createdAt DESC
                                """,
                        ProjectEntity.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public boolean existsById(UUID id) {
        return em.createQuery(
                        "SELECT COUNT(p) FROM ProjectEntity p WHERE p.id = :id AND p.deleted = false",
                        Long.class)
                .setParameter("id", id)
                .getSingleResult() > 0;
    }

    public void softDelete(UUID id) {
        ProjectEntity entity = em.find(ProjectEntity.class, id);
        if (entity != null) {
            entity.setDeleted(true);
            entity.setDeletedAt(Instant.now());
        }
    }

    public Page<ProjectEntity> search(ProjectSearchCriteria criteria, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ProjectEntity> countRoot = countQuery.from(ProjectEntity.class);
        countQuery.select(cb.count(countRoot))
                .where(buildPredicates(cb, countQuery, countRoot, criteria));

        long total = em.createQuery(countQuery).getSingleResult();
        if (total == 0L) return Page.empty(page, size);
        CriteriaQuery<ProjectEntity> dataQuery = cb.createQuery(ProjectEntity.class);
        Root<ProjectEntity> dataRoot = dataQuery.from(ProjectEntity.class);
        dataQuery.select(dataRoot)
                .where(buildPredicates(cb, dataQuery, dataRoot, criteria))
                .orderBy(buildOrder(cb, dataRoot, criteria));

        List<ProjectEntity> content = em.createQuery(dataQuery)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        return new Page<>(content, total, page, size);
    }

    public Map<ProjectStatus, Long> countByStatus(UUID memberId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<ProjectEntity> root = q.from(ProjectEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("deleted")));

        if (memberId != null) {
            Subquery<UUID> memberSub = q.subquery(UUID.class);
            Root<com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity> mRoot =
                    memberSub.from(com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity.class);
            memberSub.select(mRoot.get("projectId"))
                    .where(cb.equal(mRoot.get("userId"), memberId));
            predicates.add(root.get("id").in(memberSub));
        }

        q.multiselect(root.get("status").alias("status"), cb.count(root).alias("count"))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(root.get("status"));

        Map<ProjectStatus, Long> result = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus s : ProjectStatus.values()) result.put(s, 0L);
        em.createQuery(q).getResultList().forEach(t ->
                result.put((ProjectStatus) t.get("status"), (Long) t.get("count")));
        return result;
    }


    private Predicate[] buildPredicates(CriteriaBuilder cb,
                                        CriteriaQuery<?> query,
                                        Root<ProjectEntity> root,
                                        ProjectSearchCriteria c) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("deleted")));

        if (c.status() != null) predicates.add(cb.equal(root.get("status"), c.status()));
        if (c.createdFrom() != null)
            predicates.add(cb.greaterThanOrEqualTo(root.<java.time.Instant>get("createdAt"), c.createdFrom()));
        if (c.createdTo() != null)
            predicates.add(cb.lessThanOrEqualTo(root.<java.time.Instant>get("createdAt"), c.createdTo()));

        if (c.memberId() != null) {
            Subquery<UUID> memberSub = query.subquery(UUID.class);
            Root<com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity> mRoot =
                    memberSub.from(com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity.class);
            memberSub.select(mRoot.get("projectId"))
                    .where(cb.equal(mRoot.get("userId"), c.memberId()));
            predicates.add(root.get("id").in(memberSub));
        }

        if (c.search() != null && !c.search().isBlank()) {
            String pattern = "%" + c.search().toLowerCase().trim() + "%";

            Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
            Predicate descMatch = cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern);

            Subquery<UUID> userSub = query.subquery(UUID.class);
            Root<UserEntity> userRoot = userSub.from(UserEntity.class);
            userSub.select(userRoot.get("id"))
                    .where(cb.like(cb.lower(userRoot.get("username")), pattern));

            Predicate ownerMatch = root.get("ownerId").in(userSub);

            predicates.add(cb.or(nameMatch, descMatch, ownerMatch));
        }

        return predicates.toArray(new Predicate[0]);
    }

    private List<Order> buildOrder(CriteriaBuilder cb, Root<ProjectEntity> root, ProjectSearchCriteria c) {
        String fieldName = switch (c.sortField()) {
            case UPDATED_AT -> "updatedAt";
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
        };
        Path<Object> field = root.get(fieldName);
        Order primary = c.sortDirection() == ProjectSearchCriteria.SortDirection.ASC
                ? cb.asc(field) : cb.desc(field);
        return List.of(primary, cb.asc(root.get("id")));
    }
}