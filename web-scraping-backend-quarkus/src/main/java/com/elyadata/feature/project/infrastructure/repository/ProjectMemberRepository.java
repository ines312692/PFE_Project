package com.elyadata.feature.project.infrastructure.repository;

import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class ProjectMemberRepository {

    private final EntityManager em;

    public void persist(ProjectMemberEntity entity) {
        em.persist(entity);
    }

    public void persistOrMerge(ProjectMemberEntity entity) {
        if (entity.getId() == null) {
            em.persist(entity);
        } else {
            em.merge(entity);
        }
    }

    public Optional<ProjectMemberEntity> findByProjectIdAndUserId(UUID projectId, UUID userId) {
        return em.createQuery(
                        "SELECT m FROM ProjectMemberEntity m WHERE m.projectId = :pid AND m.userId = :uid",
                        ProjectMemberEntity.class)
                .setParameter("pid", projectId)
                .setParameter("uid", userId)
                .getResultStream()
                .findFirst();
    }

    public Optional<ProjectRole> findRoleByProjectIdAndUserId(UUID projectId, UUID userId) {
        return em.createQuery(
                        "SELECT m.role FROM ProjectMemberEntity m WHERE m.projectId = :pid AND m.userId = :uid",
                        ProjectRole.class)
                .setParameter("pid", projectId)
                .setParameter("uid", userId)
                .getResultStream()
                .findFirst();
    }

    public List<ProjectMemberEntity> findByProjectId(UUID projectId) {
        return em.createQuery(
                        "SELECT m FROM ProjectMemberEntity m WHERE m.projectId = :pid ORDER BY m.joinedAt ASC",
                        ProjectMemberEntity.class)
                .setParameter("pid", projectId)
                .getResultList();
    }

    public boolean existsByProjectIdAndUserId(UUID projectId, UUID userId) {
        return em.createQuery(
                        "SELECT COUNT(m) FROM ProjectMemberEntity m WHERE m.projectId = :pid AND m.userId = :uid",
                        Long.class)
                .setParameter("pid", projectId)
                .setParameter("uid", userId)
                .getSingleResult() > 0;
    }

    public void deleteByProjectIdAndUserId(UUID projectId, UUID userId) {
        em.createQuery(
                        "DELETE FROM ProjectMemberEntity m WHERE m.projectId = :pid AND m.userId = :uid")
                .setParameter("pid", projectId)
                .setParameter("uid", userId)
                .executeUpdate();
    }

    public void deleteAllByProjectId(UUID projectId) {
        em.createQuery(
                        "DELETE FROM ProjectMemberEntity m WHERE m.projectId = :pid")
                .setParameter("pid", projectId)
                .executeUpdate();
    }

    public Map<UUID, Long> countByProjectIds(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Map.of();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<ProjectMemberEntity> root = q.from(ProjectMemberEntity.class);

        q.multiselect(root.get("projectId").alias("pid"), cb.count(root).alias("cnt"))
                .where(root.<UUID>get("projectId").in(projectIds))
                .groupBy(root.get("projectId"));

        Map<UUID, Long> result = new HashMap<>();
        em.createQuery(q).getResultList().forEach(t ->
                result.put((UUID) t.get("pid"), (Long) t.get("cnt")));
        return result;
    }
}