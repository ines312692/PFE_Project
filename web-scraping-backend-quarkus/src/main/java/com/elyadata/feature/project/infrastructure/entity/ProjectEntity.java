package com.elyadata.feature.project.infrastructure.entity;

import com.elyadata.feature.project.domain.model.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "color", length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}