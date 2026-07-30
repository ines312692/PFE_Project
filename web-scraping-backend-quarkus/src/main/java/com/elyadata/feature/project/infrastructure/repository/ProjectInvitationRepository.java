package com.elyadata.feature.project.infrastructure.repository;

import com.elyadata.feature.project.domain.model.InvitationStatus;
import com.elyadata.feature.project.infrastructure.entity.ProjectInvitationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ProjectInvitationRepository {

    private final EntityManager em;

    public void persist(ProjectInvitationEntity entity) {
        em.persist(entity);
    }

    public ProjectInvitationEntity merge(ProjectInvitationEntity entity) {
        return em.merge(entity);
    }

    public Optional<ProjectInvitationEntity> findById(UUID id) {
        return em.createQuery(
                        "SELECT i FROM ProjectInvitationEntity i WHERE i.id = :id",
                        ProjectInvitationEntity.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<ProjectInvitationEntity> findByInvitedUserAndStatus(UUID invitedUser,
                                                                    InvitationStatus status) {
        return em.createQuery(
                        """
                                SELECT i FROM ProjectInvitationEntity i
                                WHERE i.invitedUser = :invitedUser
                                  AND i.status      = :status
                                ORDER BY i.createdAt DESC
                                """,
                        ProjectInvitationEntity.class)
                .setParameter("invitedUser", invitedUser)
                .setParameter("status", status)
                .getResultList();
    }

    public List<ProjectInvitationEntity> findByProjectIdAndStatus(UUID projectId,
                                                                  InvitationStatus status) {
        return em.createQuery(
                        """
                                SELECT i FROM ProjectInvitationEntity i
                                WHERE i.projectId = :projectId
                                  AND i.status    = :status
                                ORDER BY i.createdAt DESC
                                """,
                        ProjectInvitationEntity.class)
                .setParameter("projectId", projectId)
                .setParameter("status", status)
                .getResultList();
    }

    public boolean existsPending(UUID projectId, UUID invitedUser) {
        return em.createQuery(
                        """
                                SELECT COUNT(i) FROM ProjectInvitationEntity i
                                WHERE i.projectId   = :projectId
                                  AND i.invitedUser = :invitedUser
                                  AND i.status      = :status
                                """,
                        Long.class)
                .setParameter("projectId", projectId)
                .setParameter("invitedUser", invitedUser)
                .setParameter("status", InvitationStatus.PENDING)
                .getSingleResult() > 0;
    }
}