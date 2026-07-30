package com.elyadata.feature.project.application.mapper;

import com.elyadata.feature.project.application.dto.InvitationResponse;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class InvitationRestMapper {

    public InvitationResponse toRest(ProjectInvitation domain) {
        return InvitationResponse.builder()
                .id(domain.id())
                .projectId(domain.projectId())
                .projectName(domain.projectName())
                .invitedBy(domain.invitedBy())
                .invitedByUsername(domain.invitedByUsername())
                .role(domain.role())
                .status(domain.status())
                .createdAt(domain.createdAt())
                .expiresAt(domain.expiresAt())
                .build();
    }

    public List<InvitationResponse> toRest(List<ProjectInvitation> domains) {
        return domains.stream().map(this::toRest).toList();
    }
}