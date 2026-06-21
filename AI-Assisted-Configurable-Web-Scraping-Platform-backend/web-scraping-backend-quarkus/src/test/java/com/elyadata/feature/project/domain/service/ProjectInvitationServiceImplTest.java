package com.elyadata.feature.project.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.project.domain.model.*;
import com.elyadata.feature.project.domain.service.port.ProjectInvitationPort;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.project.domain.service.port.ProjectPort;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectInvitationServiceImpl")
class ProjectInvitationServiceImplTest {

    @Mock
    private ProjectInvitationPort invitationPort;
    @Mock
    private ProjectMemberPort memberPort;
    @Mock
    private ProjectPort projectPort;
    @Mock
    private UserPort userPort;
    @Mock
    private NotificationService notificationService;

    private ProjectInvitationServiceImpl invitationService;

    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID INVITER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID INVITEE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID INVITATION_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final String INVITEE_EMAIL = "bob@test.com";

    @BeforeEach
    void setUp() {
        invitationService = new ProjectInvitationServiceImpl(
                invitationPort, memberPort, projectPort, userPort, notificationService);
    }

    private User inviterUser() {
        return User.builder().id(INVITER_ID).username("alice").email("alice@test.com").build();
    }

    private User inviteeUser() {
        return User.builder().id(INVITEE_ID).username("bob").email(INVITEE_EMAIL).build();
    }

    private com.elyadata.feature.project.domain.model.Project activeProject() {
        return com.elyadata.feature.project.domain.model.Project.builder()
                .id(PROJECT_ID)
                .name("My Project")
                .status(ProjectStatus.ACTIVE)
                .ownerId(INVITER_ID)
                .createdBy(INVITER_ID)
                .build();
    }

    private ProjectInvitation pendingInvitation() {
        return ProjectInvitation.builder()
                .id(INVITATION_ID)
                .projectId(PROJECT_ID)
                .projectName("My Project")
                .invitedBy(INVITER_ID)
                .invitedByUsername("alice")
                .invitedUser(INVITEE_ID)
                .role(ProjectRole.MEMBER)
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(7L * 24 * 3600))
                .build();
    }

    private void stubSendHappyPath() {
        lenient().when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.of(inviteeUser()));
        lenient().when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
        lenient().when(invitationPort.existsPending(PROJECT_ID, INVITEE_ID)).thenReturn(false);
        lenient().when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        lenient().when(userPort.findById(INVITER_ID)).thenReturn(Optional.of(inviterUser()));
    }


    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("saves invitation with PENDING status and returns it")
        void savesInvitationWithPendingStatus() {
            stubSendHappyPath();
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            ProjectInvitation result = invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER);

            assertThat(result.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(result.invitedUser()).isEqualTo(INVITEE_ID);
            assertThat(result.role()).isEqualTo(ProjectRole.MEMBER);
            verify(invitationPort).save(argThat(i -> i.status() == InvitationStatus.PENDING));
        }

        @Test
        @DisplayName("sets projectName from the loaded project")
        void setsProjectName() {
            stubSendHappyPath();
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER);

            verify(invitationPort).save(argThat(i -> "My Project".equals(i.projectName())));
        }

        @Test
        @DisplayName("sets invitedByUsername from the loaded inviter")
        void setsInvitedByUsername() {
            stubSendHappyPath();
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER);

            verify(invitationPort).save(argThat(i -> "alice".equals(i.invitedByUsername())));
        }

        @Test
        @DisplayName("sets expiry to 7 days from now")
        void setsExpiresAt() {
            stubSendHappyPath();
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            Instant before = Instant.now();
            invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER);
            Instant after = Instant.now();

            verify(invitationPort).save(argThat(i -> {
                long sevenDaysSeconds = 7L * 24 * 3600;
                return i.expiresAt().isAfter(before.plusSeconds(sevenDaysSeconds - 2))
                        && i.expiresAt().isBefore(after.plusSeconds(sevenDaysSeconds + 2));
            }));
        }

        @Test
        @DisplayName("broadcasts a PROJECT_INVITATION notification to the invitee")
        void broadcastsNotification() {
            stubSendHappyPath();
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER);

            verify(notificationService).createAndBroadcast(
                    eq(INVITEE_ID),
                    eq(NotificationType.PROJECT_INVITATION),
                    anyString(),
                    anyString(),
                    eq(INVITATION_ID.toString()));
        }

        @Test
        @DisplayName("throws DomainException when invitee email is not found")
        void throwsWhenEmailNotFound() {
            when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining(INVITEE_EMAIL);
        }

        @Test
        @DisplayName("throws DomainException when invitee is already a member")
        void throwsWhenAlreadyMember() {
            when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.of(inviteeUser()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(true);

            assertThatThrownBy(() ->
                    invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already a member");
        }

        @Test
        @DisplayName("throws DomainException when a pending invitation already exists")
        void throwsWhenPendingInvitationExists() {
            when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.of(inviteeUser()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(invitationPort.existsPending(PROJECT_ID, INVITEE_ID)).thenReturn(true);

            assertThatThrownBy(() ->
                    invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("pending invitation");
        }

        @Test
        @DisplayName("throws DomainException when project does not exist")
        void throwsWhenProjectNotFound() {
            when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.of(inviteeUser()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(invitationPort.existsPending(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Project not found");
        }

        @Test
        @DisplayName("throws DomainException when inviter does not exist")
        void throwsWhenInviterNotFound() {
            when(userPort.findByEmail(INVITEE_EMAIL)).thenReturn(Optional.of(inviteeUser()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(invitationPort.existsPending(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(userPort.findById(INVITER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    invitationService.send(PROJECT_ID, INVITER_ID, INVITEE_EMAIL, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Inviter not found");
        }
    }


    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("sets status to ACCEPTED and creates a membership")
        void acceptsAndCreatesMembership() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            ProjectInvitation result = invitationService.accept(INVITATION_ID, INVITEE_ID);

            assertThat(result.status()).isEqualTo(InvitationStatus.ACCEPTED);
            verify(memberPort).save(argThat(m ->
                    PROJECT_ID.equals(m.projectId())
                            && INVITEE_ID.equals(m.userId())
                            && m.role() == ProjectRole.MEMBER));
        }

        @Test
        @DisplayName("sets respondedAt on the accepted invitation")
        void setsRespondedAt() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(false);
            when(invitationPort.save(any())).thenReturn(pendingInvitation());

            Instant before = Instant.now();
            invitationService.accept(INVITATION_ID, INVITEE_ID);

            verify(invitationPort).save(argThat(i ->
                    i.respondedAt() != null && !i.respondedAt().isBefore(before)));
        }

        @Test
        @DisplayName("throws DomainException when invitation is not found")
        void throwsWhenInvitationNotFound() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Invitation not found");
        }

        @Test
        @DisplayName("throws DomainException when responding user is not the invitee")
        void throwsWhenWrongUser() {
            UUID anotherUser = UUID.randomUUID();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, anotherUser))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not the recipient");
        }

        @Test
        @DisplayName("throws DomainException when invitation is already ACCEPTED")
        void throwsWhenAlreadyAccepted() {
            ProjectInvitation alreadyAccepted = pendingInvitation().toBuilder()
                    .status(InvitationStatus.ACCEPTED).build();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(alreadyAccepted));

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no longer pending");
        }

        @Test
        @DisplayName("throws DomainException when invitation is already DECLINED")
        void throwsWhenAlreadyDeclined() {
            ProjectInvitation declined = pendingInvitation().toBuilder()
                    .status(InvitationStatus.DECLINED).build();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(declined));

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no longer pending");
        }

        @Test
        @DisplayName("marks invitation as EXPIRED and throws when it is past its expiry date")
        void throwsAndMarksExpiredWhenExpired() {
            ProjectInvitation expired = pendingInvitation().toBuilder()
                    .expiresAt(Instant.now().minusSeconds(1)).build();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(expired));
            when(invitationPort.save(any())).thenReturn(expired);

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("expired");

            verify(invitationPort).save(argThat(i -> i.status() == InvitationStatus.EXPIRED));
        }

        @Test
        @DisplayName("throws DomainException when invitee is already a member of the project")
        void throwsWhenAlreadyMember() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, INVITEE_ID)).thenReturn(true);

            assertThatThrownBy(() -> invitationService.accept(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already a member");
        }
    }


    @Nested
    @DisplayName("decline")
    class Decline {

        @Test
        @DisplayName("sets status to DECLINED and returns updated invitation")
        void declinesInvitation() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));
            when(invitationPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProjectInvitation result = invitationService.decline(INVITATION_ID, INVITEE_ID);

            assertThat(result.status()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(result.respondedAt()).isNotNull();
        }

        @Test
        @DisplayName("does NOT create a membership when invitation is declined")
        void doesNotCreateMembership() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));
            when(invitationPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

            invitationService.decline(INVITATION_ID, INVITEE_ID);

            verify(memberPort, never()).save(any());
        }

        @Test
        @DisplayName("throws DomainException when invitation is not found")
        void throwsWhenInvitationNotFound() {
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.decline(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Invitation not found");
        }

        @Test
        @DisplayName("throws DomainException when responding user is not the invitee")
        void throwsWhenWrongUser() {
            UUID anotherUser = UUID.randomUUID();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(pendingInvitation()));

            assertThatThrownBy(() -> invitationService.decline(INVITATION_ID, anotherUser))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not the recipient");
        }

        @Test
        @DisplayName("throws DomainException when invitation is expired")
        void throwsWhenExpired() {
            ProjectInvitation expired = pendingInvitation().toBuilder()
                    .expiresAt(Instant.now().minusSeconds(1)).build();
            when(invitationPort.findById(INVITATION_ID)).thenReturn(Optional.of(expired));
            when(invitationPort.save(any())).thenReturn(expired);

            assertThatThrownBy(() -> invitationService.decline(INVITATION_ID, INVITEE_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("getPendingForUser")
    class GetPendingForUser {

        @Test
        @DisplayName("delegates to port and returns list of pending invitations")
        void returnsPendingInvitations() {
            when(invitationPort.findPendingByUser(INVITEE_ID)).thenReturn(List.of(pendingInvitation()));

            List<ProjectInvitation> result = invitationService.getPendingForUser(INVITEE_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().invitedUser()).isEqualTo(INVITEE_ID);
            verify(invitationPort).findPendingByUser(INVITEE_ID);
        }

        @Test
        @DisplayName("returns empty list when user has no pending invitations")
        void returnsEmptyList() {
            when(invitationPort.findPendingByUser(INVITEE_ID)).thenReturn(List.of());

            List<ProjectInvitation> result = invitationService.getPendingForUser(INVITEE_ID);

            assertThat(result).isEmpty();
        }
    }


    @Nested
    @DisplayName("getPendingForProject")
    class GetPendingForProject {

        @Test
        @DisplayName("delegates to port with PENDING status and returns list")
        void returnsPendingInvitations() {
            when(invitationPort.findByProjectAndStatus(PROJECT_ID, InvitationStatus.PENDING))
                    .thenReturn(List.of(pendingInvitation()));

            List<ProjectInvitation> result = invitationService.getPendingForProject(PROJECT_ID);

            assertThat(result).hasSize(1);
            verify(invitationPort).findByProjectAndStatus(PROJECT_ID, InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("returns empty list when project has no pending invitations")
        void returnsEmptyList() {
            when(invitationPort.findByProjectAndStatus(PROJECT_ID, InvitationStatus.PENDING))
                    .thenReturn(List.of());

            List<ProjectInvitation> result = invitationService.getPendingForProject(PROJECT_ID);

            assertThat(result).isEmpty();
        }
    }
}
