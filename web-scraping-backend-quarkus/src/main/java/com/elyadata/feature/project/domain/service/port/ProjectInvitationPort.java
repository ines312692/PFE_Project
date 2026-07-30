package com.elyadata.feature.project.domain.service.port;

import com.elyadata.feature.project.domain.model.InvitationStatus;
import com.elyadata.feature.project.domain.model.ProjectInvitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectInvitationPort {

    ProjectInvitation save(ProjectInvitation invitation);

    Optional<ProjectInvitation> findById(UUID id);

    List<ProjectInvitation> findPendingByUser(UUID userId);

    List<ProjectInvitation> findByProjectAndStatus(UUID projectId, InvitationStatus status);

    boolean existsPending(UUID projectId, UUID invitedUser);
}