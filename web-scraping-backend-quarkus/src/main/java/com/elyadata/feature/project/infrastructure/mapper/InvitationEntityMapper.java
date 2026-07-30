package com.elyadata.feature.project.infrastructure.mapper;

import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.infrastructure.entity.ProjectInvitationEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InvitationEntityMapper {

    public ProjectInvitation toDomain(ProjectInvitationEntity e) {
        return ProjectInvitation.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .invitedBy(e.getInvitedBy())
                .invitedUser(e.getInvitedUser())
                .role(e.getRole())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .respondedAt(e.getRespondedAt())
                .expiresAt(e.getExpiresAt())
                .build();
    }

    public ProjectInvitationEntity toEntity(ProjectInvitation d) {
        ProjectInvitationEntity e = new ProjectInvitationEntity();
        e.setId(d.id());
        e.setProjectId(d.projectId());
        e.setInvitedBy(d.invitedBy());
        e.setInvitedUser(d.invitedUser());
        e.setRole(d.role());
        e.setStatus(d.status());
        e.setCreatedAt(d.createdAt());
        e.setRespondedAt(d.respondedAt());
        e.setExpiresAt(d.expiresAt());
        return e;
    }
}