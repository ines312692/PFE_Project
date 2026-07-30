package com.elyadata.feature.dataversion.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "data_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scrape_result_id", nullable = false)
    private UUID scrapeResultId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "records", columnDefinition = "jsonb")
    private List<Map<String, Object>> records;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_steps", columnDefinition = "jsonb")
    private List<Map<String, Object>> appliedSteps;

    @Column(name = "record_count")
    private int recordCount;

    @Column(name = "label")
    private String label;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;


}
