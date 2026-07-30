package com.elyadata.feature.notification.infrastructure.repository;

import com.elyadata.feature.notification.infrastructure.entity.NotificationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class NotificationRepository {

    private static final String PARAM_USER_ID = "userId";

    private final EntityManager em;

    public void persist(NotificationEntity entity) {
        em.persist(entity);
    }

    public List<NotificationEntity> findByUserId(UUID userId) {
        return em.createQuery(
                        "SELECT n FROM NotificationEntity n WHERE n.userId = :userId ORDER BY n.createdAt DESC",
                        NotificationEntity.class)
                .setParameter(PARAM_USER_ID, userId)
                .setMaxResults(50)
                .getResultList();
    }

    public long countUnreadByUserId(UUID userId) {
        return em.createQuery(
                        "SELECT COUNT(n) FROM NotificationEntity n WHERE n.userId = :userId AND n.read = false",
                        Long.class)
                .setParameter(PARAM_USER_ID, userId)
                .getSingleResult();
    }

    public void markAsRead(UUID id) {
        em.createQuery("UPDATE NotificationEntity n SET n.read = true WHERE n.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    public void markAllAsReadByUserId(UUID userId) {
        em.createQuery("UPDATE NotificationEntity n SET n.read = true WHERE n.userId = :userId AND n.read = false")
                .setParameter(PARAM_USER_ID, userId)
                .executeUpdate();
    }

    public void deleteByIdAndUserId(UUID id, UUID userId) {
        em.createQuery("DELETE FROM NotificationEntity n WHERE n.id = :id AND n.userId = :userId")
                .setParameter("id", id)
                .setParameter(PARAM_USER_ID, userId)
                .executeUpdate();
    }

    public void deleteAllByUserId(UUID userId) {
        em.createQuery("DELETE FROM NotificationEntity n WHERE n.userId = :userId")
                .setParameter(PARAM_USER_ID, userId)
                .executeUpdate();
    }
}