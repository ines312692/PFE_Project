package com.elyadata.feature.etl.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_etl_configs")
public class JobEtlConfigEntity {

    @Id
    @Column(name = "job_id")
    public UUID jobId;

    @Column(name = "steps_json", columnDefinition = "text", nullable = false)
    public String stepsJson;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}