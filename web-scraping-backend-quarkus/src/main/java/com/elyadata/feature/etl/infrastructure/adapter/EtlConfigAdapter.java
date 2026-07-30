package com.elyadata.feature.etl.infrastructure.adapter;

import com.elyadata.feature.etl.domain.model.EtlConfig;
import com.elyadata.feature.etl.domain.model.EtlStepConfig;
import com.elyadata.feature.etl.domain.service.port.EtlConfigPort;
import com.elyadata.feature.etl.infrastructure.entity.JobEtlConfigEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class EtlConfigAdapter implements EtlConfigPort {

    private static final TypeReference<List<EtlStepConfig>> STEPS_TYPE = new TypeReference<>() {
    };

    private final EntityManager em;
    private final ObjectMapper mapper;

    @Override
    public Optional<EtlConfig> findByJobId(UUID jobId) {
        JobEtlConfigEntity entity = em.find(JobEtlConfigEntity.class, jobId);
        if (entity == null || entity.stepsJson == null || entity.stepsJson.isBlank()) {
            return Optional.empty();
        }
        try {
            List<EtlStepConfig> steps = mapper.readValue(entity.stepsJson, STEPS_TYPE);
            return Optional.of(new EtlConfig(steps));
        } catch (Exception e) {
            log.error("[ETL-CONFIG] JSON illisible pour job={}: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void save(UUID jobId, EtlConfig config) {
        String json = writeJson(config);
        JobEtlConfigEntity entity = em.find(JobEtlConfigEntity.class, jobId);
        if (entity == null) {
            entity = new JobEtlConfigEntity();
            entity.jobId = jobId;
            entity.stepsJson = json;
            entity.updatedAt = Instant.now();
            em.persist(entity);
        } else {
            entity.stepsJson = json;
            entity.updatedAt = Instant.now();
            em.merge(entity);
        }
        log.info("[ETL-CONFIG] steps enregistrés pour job={}", jobId);
    }

    private String writeJson(EtlConfig config) {
        try {
            List<EtlStepConfig> steps = config != null && config.steps() != null ? config.steps() : List.of();
            return mapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation EtlConfig impossible", e);
        }
    }
}