package com.elyadata.feature.project.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.project.domain.model.*;
import com.elyadata.feature.project.domain.service.port.ProjectInvitationPort;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.project.domain.service.port.ProjectPort;
import com.elyadata.feature.user.domain.service.port.UserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ProjectInvitationServiceImpl implements ProjectInvitationService {

    private final ProjectInvitationPort invitationPort;
    private final ProjectMemberPort memberPort;
    private final ProjectPort projectPort;
    private final UserPort userPort;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ProjectInvitation send(UUID projectId, UUID invitedBy,
                                  String invitedEmail, ProjectRole role) {
        var invitedUser = userPort.findByEmail(invitedEmail)
                .orElseThrow(() -> DomainException.notFound("No user found with email: " + invitedEmail));

        if (memberPort.existsByProjectIdAndUserId(projectId, invitedUser.id())) {
            throw DomainException.notValid("User is already a member of this project.");
        }
        if (invitationPort.existsPending(projectId, invitedUser.id())) {
            throw DomainException.notValid("A pending invitation already exists for this user.");
        }

        Project project = projectPort.findById(projectId)
                .orElseThrow(() -> DomainException.notFound("Project not found: " + projectId));

        var inviter = userPort.findById(invitedBy)
                .orElseThrow(() -> DomainException.notFound("Inviter not found: " + invitedBy));

        ProjectInvitation invitation = ProjectInvitation.builder()
                .projectId(projectId)
                .projectName(project.name())
                .invitedBy(invitedBy)
                .invitedByUsername(inviter.username())
                .invitedUser(invitedUser.id())
                .role(role)
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7L * 24 * 3600))
                .build();

        ProjectInvitation saved = invitationPort.save(invitation);

        String title = "Invitation au projet \"" + project.name() + "\"";
        String message = String.format(
                "%s vous a invité à rejoindre le projet \"%s\" en tant que %s.",
                inviter.username(), project.name(), role.name());

        notificationService.createAndBroadcast(
                invitedUser.id(),
                NotificationType.PROJECT_INVITATION,
                title,
                message,
                saved.id().toString());

        log.info("[INVITATION] Sent projectId={} from={} to={}({}) role={}",
                projectId, invitedBy, invitedUser.id(), invitedEmail, role);
        return saved;
    }

    @Override
    @Transactional
    public ProjectInvitation accept(UUID invitationId, UUID respondingUser) {
        ProjectInvitation invitation = resolveValid(invitationId, respondingUser);
        if (memberPort.existsByProjectIdAndUserId(invitation.projectId(), respondingUser)) {
            throw DomainException.notValid("User is already a member of this project.");
        }

        ProjectInvitation accepted = invitation.toBuilder()
                .status(InvitationStatus.ACCEPTED)
                .respondedAt(Instant.now())
                .build();

        invitationPort.save(accepted);

        memberPort.save(ProjectMember.builder()
                .projectId(invitation.projectId())
                .userId(respondingUser)
                .role(invitation.role())
                .joinedAt(Instant.now())
                .build());

        log.info("[INVITATION] Accepted id={} user={}", invitationId, respondingUser);
        return accepted;
    }

    @Override
    @Transactional
    public ProjectInvitation decline(UUID invitationId, UUID respondingUser) {
        ProjectInvitation invitation = resolveValid(invitationId, respondingUser);

        ProjectInvitation declined = invitation.toBuilder()
                .status(InvitationStatus.DECLINED)
                .respondedAt(Instant.now())
                .build();

        log.info("[INVITATION] Declined id={} user={}", invitationId, respondingUser);
        return invitationPort.save(declined);
    }

    @Override
    public List<ProjectInvitation> getPendingForUser(UUID userId) {
        return invitationPort.findPendingByUser(userId);
    }

    @Override
    public List<ProjectInvitation> getPendingForProject(UUID projectId) {
        return invitationPort.findByProjectAndStatus(projectId, InvitationStatus.PENDING);
    }


    private ProjectInvitation resolveValid(UUID invitationId, UUID userId) {
        ProjectInvitation invitation = invitationPort.findById(invitationId)
                .orElseThrow(() -> DomainException.notFound("Invitation not found: " + invitationId));

        if (!invitation.invitedUser().equals(userId)) {
            throw DomainException.notValid("You are not the recipient of this invitation.");
        }

        if (invitation.status() != InvitationStatus.PENDING) {
            throw DomainException.notValid("Invitation is no longer pending.");
        }

        if (invitation.expiresAt().isBefore(Instant.now())) {
            invitationPort.save(invitation.toBuilder().status(InvitationStatus.EXPIRED).build());
            throw DomainException.notValid("Invitation has expired.");
        }

        return invitation;
    }
}