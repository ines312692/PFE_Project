package com.elyadata.feature.job.infrastructure.repository;

import com.elyadata.feature.job.infrastructure.entity.RetryPolicyEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class RetryPolicyRepository {

    private final EntityManager em;

    public void persist(RetryPolicyEntity entity) {
        em.persist(entity);
    }

    public Optional<RetryPolicyEntity> findByJobId(UUID jobId) {
        return em.createQuery(
                        "SELECT r FROM RetryPolicyEntity r WHERE r.jobId = :jobId",
                        RetryPolicyEntity.class)
                .setParameter("jobId", jobId)
                .getResultStream()
                .findFirst();
    }

    public void deleteByJobId(UUID jobId) {
        em.createQuery("DELETE FROM RetryPolicyEntity r WHERE r.jobId = :jobId")
                .setParameter("jobId", jobId)
                .executeUpdate();
    }

    public Map<UUID, RetryPolicyEntity> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        return em.createQuery(
                        "SELECT r FROM RetryPolicyEntity r WHERE r.jobId IN :jobIds",
                        RetryPolicyEntity.class)
                .setParameter("jobIds", jobIds)
                .getResultStream()
                .collect(Collectors.toMap(RetryPolicyEntity::getJobId, r -> r));
    }
}