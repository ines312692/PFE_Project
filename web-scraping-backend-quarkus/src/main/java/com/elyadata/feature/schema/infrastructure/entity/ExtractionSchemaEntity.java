package com.elyadata.feature.schema.infrastructure.entity;

import com.elyadata.feature.schema.domain.model.LlmModel;
import com.elyadata.feature.schema.domain.model.SchemaField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "extraction_schemas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionSchemaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", columnDefinition = "jsonb")
    private List<SchemaField> fields;

    @Column(name = "llm_prompt", columnDefinition = "TEXT")
    private String llmPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_model")
    private LlmModel llmModel;

    @Column(name = "llm_output_format", columnDefinition = "TEXT")
    private String llmOutputFormat;

    @Column(name = "regex_pattern", columnDefinition = "TEXT")
    private String regexPattern;

    @Column(name = "regex_group")
    private String regexGroup;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}