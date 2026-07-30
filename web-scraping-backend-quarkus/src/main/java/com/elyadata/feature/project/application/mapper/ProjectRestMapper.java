package com.elyadata.feature.project.application.mapper;

import com.elyadata.feature.project.application.dto.*;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectMember;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectRestMapper {

    public Project toDomain(CreateProjectRequest req) {
        return Project.builder()
                .name(req.name())
                .description(req.description())
                .color(req.color())
                .build();
    }

    public Project toDomain(UpdateProjectRequest req) {
        return Project.builder()
                .name(req.name())
                .description(req.description())
                .color(req.color())
                .build();
    }

    public ProjectResponse toRest(Project domain) {
        return ProjectResponse.builder()
                .id(domain.id())
                .name(domain.name())
                .description(domain.description())
                .color(domain.color())
                .status(domain.status())
                .ownerId(domain.ownerId())
                .createdBy(domain.createdBy())
                .updatedBy(domain.updatedBy())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }

    public List<ProjectResponse> toRest(List<Project> domains) {
        return domains.stream().map(this::toRest).toList();
    }

    public ProjectMemberResponse toRest(ProjectMember domain) {
        return ProjectMemberResponse.builder()
                .id(domain.id())
                .projectId(domain.projectId())
                .userId(domain.userId())
                .username(domain.username())
                .email(domain.email())
                .role(domain.role())
                .joinedAt(domain.joinedAt())
                .build();
    }

    public List<ProjectMemberResponse> toRestMembers(List<ProjectMember> members) {
        return members.stream().map(this::toRest).toList();
    }
}