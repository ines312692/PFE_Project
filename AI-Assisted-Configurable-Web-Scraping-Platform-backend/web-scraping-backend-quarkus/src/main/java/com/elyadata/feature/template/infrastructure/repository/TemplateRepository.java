package com.elyadata.feature.template.infrastructure.repository;

import com.elyadata.feature.template.infrastructure.entity.TemplateEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class TemplateRepository {

    private final EntityManager em;

    public List<TemplateEntity> findAll() {
        return em.createQuery(
                "SELECT t FROM TemplateEntity t ORDER BY t.popularity DESC",
                TemplateEntity.class
        ).getResultList();
    }

    public Optional<TemplateEntity> findById(UUID id) {
        return em.createQuery(
                "SELECT t FROM TemplateEntity t WHERE t.id = :id",
                TemplateEntity.class
        ).setParameter("id", id).getResultStream().findFirst();
    }
}