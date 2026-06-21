package com.elyadata.feature.job.infrastructure.adapter;

import com.elyadata.feature.job.domain.service.port.JobReadPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.elyadata.feature.job.infrastructure.entity.JobEntity;

@ApplicationScoped
@RequiredArgsConstructor
public class JobReadAdapter implements JobReadPort {

    private static final String PROJECT_ID = "projectId";

    private final EntityManager em;

    @Override
    public Map<UUID, Long> countByProjectIds(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Map.of();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<JobEntity> root = q.from(JobEntity.class);

        q.select(cb.tuple(
                        root.get(PROJECT_ID).alias("pid"),
                        cb.count(root).alias("cnt")
                ))
                .where(
                        cb.isFalse(root.get("deleted")),
                        root.<UUID>get(PROJECT_ID).in(projectIds)
                )
                .groupBy(root.get(PROJECT_ID));

        Map<UUID, Long> result = new HashMap<>();
        em.createQuery(q).getResultList().forEach(t ->
                result.put((UUID) t.get("pid"), (Long) t.get("cnt")));
        return result;
    }
}