package com.elyadata.feature.project.infrastructure.mapper;

import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.infrastructure.entity.ProjectEntity;
import com.elyadata.feature.project.infrastructure.entity.ProjectMemberEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProjectEntityMapper {

    public Project toDomain(ProjectEntity entity) {
        return Project.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .color(entity.getColor())
                .status(entity.getStatus())
                .ownerId(entity.getOwnerId())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ProjectEntity toEntity(Project domain) {
        return ProjectEntity.builder()
                .id(domain.id())
                .name(domain.name())
                .description(domain.description())
                .color(domain.color())
                .status(domain.status())
                .ownerId(domain.ownerId())
                .createdBy(domain.createdBy())
                .updatedBy(domain.updatedBy())
                .build();
    }

    public ProjectMember toDomain(ProjectMemberEntity entity) {
        return ProjectMember.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .userId(entity.getUserId())
                .role(entity.getRole())
                .joinedAt(entity.getJoinedAt())
                .build();
    }

    public ProjectMemberEntity toEntity(ProjectMember domain) {
        return ProjectMemberEntity.builder()
                .id(domain.id())
                .projectId(domain.projectId())
                .userId(domain.userId())
                .role(domain.role())
                .build();
    }
}