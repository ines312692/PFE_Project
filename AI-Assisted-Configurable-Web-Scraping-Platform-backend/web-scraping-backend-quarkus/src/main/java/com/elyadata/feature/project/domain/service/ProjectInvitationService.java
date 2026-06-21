package com.elyadata.feature.project.domain.service;


import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.model.ProjectRole;

import java.util.List;
import java.util.UUID;

public interface ProjectInvitationService {

    ProjectInvitation send(UUID projectId, UUID invitedBy, String invitedEmail, ProjectRole role);

    ProjectInvitation accept(UUID invitationId, UUID respondingUser);

    ProjectInvitation decline(UUID invitationId, UUID respondingUser);

    List<ProjectInvitation> getPendingForUser(UUID userId);

    List<ProjectInvitation> getPendingForProject(UUID projectId);
}