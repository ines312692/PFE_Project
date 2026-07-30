package com.elyadata.feature.template.infrastructure.entity;

import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.template.domain.model.TemplateCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_templates")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @Column(nullable = false)
    private int popularity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}