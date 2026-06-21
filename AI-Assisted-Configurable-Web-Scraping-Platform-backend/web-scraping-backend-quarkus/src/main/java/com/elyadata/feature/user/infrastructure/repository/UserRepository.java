package com.elyadata.feature.user.infrastructure.repository;

import com.elyadata.feature.user.infrastructure.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserRepository {

    private final EntityManager em;

    public void persist(UserEntity entity) {
        em.persist(entity);
    }

    public List<UserEntity> listAll() {
        return em.createQuery("SELECT u FROM UserEntity u", UserEntity.class)
                .getResultList();
    }

    public Optional<UserEntity> findByIdOptional(UUID id) {
        return Optional.ofNullable(em.find(UserEntity.class, id));
    }

    public UserEntity findById(UUID id) {
        return em.find(UserEntity.class, id);
    }

    public Optional<UserEntity> findByUsername(String username) {
        return em.createQuery("SELECT u FROM UserEntity u WHERE u.username = :username", UserEntity.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }

    public Optional<UserEntity> findByEmail(String email) {
        return em.createQuery("SELECT u FROM UserEntity u WHERE u.email = :email", UserEntity.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    public boolean existsByUsername(String username) {
        Long count = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult();
        return count > 0;
    }

    public boolean existsByEmail(String email) {
        Long count = em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }

    public void deleteById(UUID id) {
        UserEntity entity = em.find(UserEntity.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public List<UserEntity> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return em.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.id IN :ids",
                        UserEntity.class)
                .setParameter("ids", ids)
                .getResultList();
    }
}
