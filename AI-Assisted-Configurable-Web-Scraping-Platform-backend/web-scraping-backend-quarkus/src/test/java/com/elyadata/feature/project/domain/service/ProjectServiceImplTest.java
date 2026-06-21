package com.elyadata.feature.project.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.*;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.project.domain.service.port.ProjectPort;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectServiceImpl")
class ProjectServiceImplTest {

    @Mock private ProjectPort projectPort;
    @Mock private ProjectMemberPort memberPort;
    @Mock private UserPort userPort;

    private ProjectServiceImpl projectService;

    private static final UUID PROJECT_ID  = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID    = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID MEMBER_ID   = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID VIEWER_ID   = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID STRANGER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectPort, memberPort, userPort);
    }

    private Project activeProject() {
        return Project.builder()
                .id(PROJECT_ID)
                .name("My Project")
                .description("desc")
                .color("#FF0000")
                .status(ProjectStatus.ACTIVE)
                .ownerId(OWNER_ID)
                .createdBy(OWNER_ID)
                .build();
    }

    private User stubUser() {
        return User.builder()
                .id(MEMBER_ID)
                .username("alice")
                .email("alice@test.com")
                .build();
    }

    private void stubExistingProjectWithOwner() {
        lenient().when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
        lenient().when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .thenReturn(Optional.of(ProjectRole.OWNER));
    }

    @Nested
    @DisplayName("createProject")
    class CreateProject {

        @Test
        @DisplayName("forces status to ACTIVE regardless of input status")
        void forcesActiveStatus() {
            Project input = activeProject().toBuilder().status(ProjectStatus.ARCHIVED).build();
            when(projectPort.save(any())).thenReturn(activeProject());

            projectService.createProject(input, OWNER_ID);

            verify(projectPort).save(argThat(p -> p.status() == ProjectStatus.ACTIVE));
        }

        @Test
        @DisplayName("sets ownerId and createdBy from the parameter, not from the input object")
        void setsOwnerAndCreatedBy() {
            Project input = activeProject().toBuilder().ownerId(STRANGER_ID).createdBy(STRANGER_ID).build();
            when(projectPort.save(any())).thenReturn(activeProject());

            projectService.createProject(input, OWNER_ID);

            verify(projectPort).save(argThat(p ->
                    OWNER_ID.equals(p.ownerId()) && OWNER_ID.equals(p.createdBy())));
        }

        @Test
        @DisplayName("saves an OWNER member record for the creator after project creation")
        void savesOwnerMember() {
            when(projectPort.save(any())).thenReturn(activeProject());

            projectService.createProject(activeProject(), OWNER_ID);

            verify(memberPort).save(argThat(m ->
                    PROJECT_ID.equals(m.projectId())
                            && OWNER_ID.equals(m.userId())
                            && m.role() == ProjectRole.OWNER));
        }

        @Test
        @DisplayName("returns the persisted project")
        void returnsSavedProject() {
            when(projectPort.save(any())).thenReturn(activeProject());

            Project result = projectService.createProject(activeProject(), OWNER_ID);

            assertThat(result.id()).isEqualTo(PROJECT_ID);
            assertThat(result.name()).isEqualTo("My Project");
        }
    }

    @Nested
    @DisplayName("getProjectById")
    class GetProjectById {

        @Test
        @DisplayName("returns project when it exists")
        void returnsProject() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));

            Project result = projectService.getProjectById(PROJECT_ID);

            assertThat(result.id()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("throws DomainException when project does not exist")
        void throwsWhenNotFound() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectById(PROJECT_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining(PROJECT_ID.toString());
        }
    }

    @Nested
    @DisplayName("updateProject")
    class UpdateProject {

        @Test
        @DisplayName("preserves existing status on update")
        void preservesExistingStatus() {
            Project existing = activeProject().toBuilder().status(ProjectStatus.ARCHIVED).build();
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
            when(projectPort.update(any())).thenReturn(existing);

            projectService.updateProject(PROJECT_ID, activeProject(), OWNER_ID);

            verify(projectPort).update(argThat(p -> p.status() == ProjectStatus.ARCHIVED));
        }

        @Test
        @DisplayName("preserves existing ownerId on update")
        void preservesOwnerId() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(projectPort.update(any())).thenReturn(activeProject());

            Project updates = activeProject().toBuilder().ownerId(STRANGER_ID).build();
            projectService.updateProject(PROJECT_ID, updates, OWNER_ID);

            verify(projectPort).update(argThat(p -> OWNER_ID.equals(p.ownerId())));
        }

        @Test
        @DisplayName("sets updatedBy from the requestedBy parameter")
        void setsUpdatedBy() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(projectPort.update(any())).thenReturn(activeProject());

            projectService.updateProject(PROJECT_ID, activeProject(), MEMBER_ID);

            verify(projectPort).update(argThat(p -> MEMBER_ID.equals(p.updatedBy())));
        }

        @Test
        @DisplayName("throws DomainException when project does not exist")
        void throwsWhenProjectNotFound() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.updateProject(PROJECT_ID, activeProject(), OWNER_ID))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("archiveProject")
    class ArchiveProject {

        @Test
        @DisplayName("sets status to ARCHIVED when requester is OWNER")
        void archivesProject() {
            stubExistingProjectWithOwner();
            when(projectPort.update(any())).thenReturn(activeProject());

            projectService.archiveProject(PROJECT_ID, OWNER_ID);

            verify(projectPort).update(argThat(p -> p.status() == ProjectStatus.ARCHIVED));
        }

        @Test
        @DisplayName("throws DomainException when requester has insufficient role")
        void throwsWhenNotOwner() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ProjectRole.MEMBER));

            assertThatThrownBy(() -> projectService.archiveProject(PROJECT_ID, MEMBER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Insufficient permissions");
        }

        @Test
        @DisplayName("throws DomainException when requester is not a member")
        void throwsWhenNotMember() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, STRANGER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.archiveProject(PROJECT_ID, STRANGER_ID))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("deleteProject")
    class DeleteProject {

        @Test
        @DisplayName("deletes members and then project when requester is OWNER")
        void deletesAllRelated() {
            stubExistingProjectWithOwner();

            projectService.deleteProject(PROJECT_ID, OWNER_ID);

            InOrder order = inOrder(memberPort, projectPort);
            order.verify(memberPort).deleteAllByProjectId(PROJECT_ID);
            order.verify(projectPort).deleteById(PROJECT_ID);
        }

        @Test
        @DisplayName("throws DomainException when project does not exist")
        void throwsWhenNotFound() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.deleteProject(PROJECT_ID, OWNER_ID))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("throws DomainException when requester is only MEMBER")
        void throwsWhenMemberRole() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ProjectRole.MEMBER));

            assertThatThrownBy(() -> projectService.deleteProject(PROJECT_ID, MEMBER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Insufficient permissions");
        }
    }

    @Nested
    @DisplayName("addMember")
    class AddMember {

        @Test
        @DisplayName("saves new MEMBER and returns it enriched with username + email")
        void addsMember() {
            stubExistingProjectWithOwner();
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, MEMBER_ID)).thenReturn(false);
            when(userPort.findById(MEMBER_ID)).thenReturn(Optional.of(stubUser()));

            ProjectMember saved = ProjectMember.builder()
                    .projectId(PROJECT_ID).userId(MEMBER_ID).role(ProjectRole.MEMBER).build();
            when(memberPort.save(any())).thenReturn(saved);

            ProjectMember result = projectService.addMember(PROJECT_ID, MEMBER_ID, ProjectRole.MEMBER, OWNER_ID);

            assertThat(result.username()).isEqualTo("alice");
            assertThat(result.email()).isEqualTo("alice@test.com");
            assertThat(result.role()).isEqualTo(ProjectRole.MEMBER);
        }

        @Test
        @DisplayName("throws DomainException when trying to assign OWNER role")
        void throwsWhenAssigningOwnerRole() {
            stubExistingProjectWithOwner();

            assertThatThrownBy(() ->
                    projectService.addMember(PROJECT_ID, MEMBER_ID, ProjectRole.OWNER, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("OWNER");
        }

        @Test
        @DisplayName("throws DomainException when target user is already a member")
        void throwsWhenAlreadyMember() {
            stubExistingProjectWithOwner();
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, MEMBER_ID)).thenReturn(true);
            when(userPort.findById(MEMBER_ID)).thenReturn(Optional.of(stubUser()));

            assertThatThrownBy(() ->
                    projectService.addMember(PROJECT_ID, MEMBER_ID, ProjectRole.MEMBER, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already a member");
        }

        @Test
        @DisplayName("throws DomainException when target user does not exist")
        void throwsWhenUserNotFound() {
            // userPort.findById is called before memberPort — no need to stub memberPort here
            stubExistingProjectWithOwner();
            when(userPort.findById(STRANGER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    projectService.addMember(PROJECT_ID, STRANGER_ID, ProjectRole.MEMBER, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("throws DomainException when requester has insufficient role (MEMBER tries to add)")
        void throwsWhenRequesterNotOwner() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ProjectRole.MEMBER));

            assertThatThrownBy(() ->
                    projectService.addMember(PROJECT_ID, VIEWER_ID, ProjectRole.VIEWER, MEMBER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Insufficient permissions");
        }
    }


    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("removes member when requester is OWNER and target is not OWNER")
        void removesMember() {
            stubExistingProjectWithOwner();
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ProjectRole.MEMBER));

            projectService.removeMember(PROJECT_ID, MEMBER_ID, OWNER_ID);

            verify(memberPort).deleteByProjectIdAndUserId(PROJECT_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("throws DomainException when trying to remove the project owner")
        void throwsWhenRemovingOwner() {
            stubExistingProjectWithOwner();
            // target is also OWNER
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                    .thenReturn(Optional.of(ProjectRole.OWNER));

            assertThatThrownBy(() -> projectService.removeMember(PROJECT_ID, OWNER_ID, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Cannot remove the project owner");
        }

        @Test
        @DisplayName("throws DomainException when target user is not a member")
        void throwsWhenTargetNotMember() {
            stubExistingProjectWithOwner();
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, STRANGER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.removeMember(PROJECT_ID, STRANGER_ID, OWNER_ID))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("updateMemberRole")
    class UpdateMemberRole {

        @Test
        @DisplayName("updates role to VIEWER successfully")
        void updatesRole() {
            stubExistingProjectWithOwner();
            ProjectMember existing = ProjectMember.builder()
                    .projectId(PROJECT_ID).userId(MEMBER_ID).role(ProjectRole.MEMBER).build();
            when(memberPort.findByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(memberPort.save(any())).thenReturn(existing.toBuilder().role(ProjectRole.VIEWER).build());

            ProjectMember result = projectService.updateMemberRole(PROJECT_ID, MEMBER_ID, ProjectRole.VIEWER, OWNER_ID);

            assertThat(result.role()).isEqualTo(ProjectRole.VIEWER);
            verify(memberPort).save(argThat(m -> m.role() == ProjectRole.VIEWER));
        }

        @Test
        @DisplayName("throws DomainException when new role is OWNER")
        void throwsWhenNewRoleIsOwner() {
            stubExistingProjectWithOwner();

            assertThatThrownBy(() ->
                    projectService.updateMemberRole(PROJECT_ID, MEMBER_ID, ProjectRole.OWNER, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("OWNER");
        }

        @Test
        @DisplayName("throws DomainException when trying to change the owner's role")
        void throwsWhenChangingOwnersRole() {
            stubExistingProjectWithOwner();
            ProjectMember ownerMember = ProjectMember.builder()
                    .projectId(PROJECT_ID).userId(MEMBER_ID).role(ProjectRole.OWNER).build();
            when(memberPort.findByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() ->
                    projectService.updateMemberRole(PROJECT_ID, MEMBER_ID, ProjectRole.VIEWER, OWNER_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("owner");
        }

        @Test
        @DisplayName("throws DomainException when target user is not a member")
        void throwsWhenTargetNotMember() {
            stubExistingProjectWithOwner();
            when(memberPort.findByProjectIdAndUserId(PROJECT_ID, STRANGER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    projectService.updateMemberRole(PROJECT_ID, STRANGER_ID, ProjectRole.VIEWER, OWNER_ID))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("getProjectMembers")
    class GetProjectMembers {

        @Test
        @DisplayName("enriches members with username and email from UserPort")
        void enrichesMembersWithUserInfo() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            ProjectMember raw = ProjectMember.builder()
                    .projectId(PROJECT_ID).userId(MEMBER_ID).role(ProjectRole.MEMBER).build();
            when(memberPort.findByProjectId(PROJECT_ID)).thenReturn(List.of(raw));
            when(userPort.findById(MEMBER_ID))
                    .thenReturn(Optional.of(stubUser()));

            List<ProjectMember> result = projectService.getProjectMembers(PROJECT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().username()).isEqualTo("alice");
            assertThat(result.getFirst().email()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("returns member without enrichment when user not found in UserPort")
        void returnsMemberEvenWhenUserNotFound() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            ProjectMember raw = ProjectMember.builder()
                    .projectId(PROJECT_ID).userId(STRANGER_ID).role(ProjectRole.VIEWER).build();
            when(memberPort.findByProjectId(PROJECT_ID)).thenReturn(List.of(raw));
            when(userPort.findById(STRANGER_ID)).thenReturn(Optional.empty());

            List<ProjectMember> result = projectService.getProjectMembers(PROJECT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().username()).isNull();
        }

        @Test
        @DisplayName("returns empty list when project has no members")
        void returnsEmptyList() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.of(activeProject()));
            when(memberPort.findByProjectId(PROJECT_ID)).thenReturn(List.of());

            List<ProjectMember> result = projectService.getProjectMembers(PROJECT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws DomainException when project does not exist")
        void throwsWhenProjectNotFound() {
            when(projectPort.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectMembers(PROJECT_ID))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("assertMemberAccess")
    class AssertMemberAccess {

        @Test
        @DisplayName("does not throw when user is a member")
        void doesNotThrowWhenMember() {
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, MEMBER_ID)).thenReturn(true);

            assertThatCode(() -> projectService.assertMemberAccess(PROJECT_ID, MEMBER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws DomainException when user is not a member")
        void throwsWhenNotMember() {
            when(memberPort.existsByProjectIdAndUserId(PROJECT_ID, STRANGER_ID)).thenReturn(false);

            assertThatThrownBy(() -> projectService.assertMemberAccess(PROJECT_ID, STRANGER_ID))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("assertMinimumRole")
    class AssertMinimumRole {

        @Test
        @DisplayName("OWNER satisfies OWNER minimum")
        void ownerSatisfiesOwner() {
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                    .thenReturn(Optional.of(ProjectRole.OWNER));

            assertThatCode(() -> projectService.assertMinimumRole(PROJECT_ID, OWNER_ID, ProjectRole.OWNER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OWNER satisfies MEMBER minimum")
        void ownerSatisfiesMember() {
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                    .thenReturn(Optional.of(ProjectRole.OWNER));

            assertThatCode(() -> projectService.assertMinimumRole(PROJECT_ID, OWNER_ID, ProjectRole.MEMBER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MEMBER does not satisfy OWNER minimum")
        void memberDoesNotSatisfyOwner() {
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, MEMBER_ID))
                    .thenReturn(Optional.of(ProjectRole.MEMBER));

            assertThatThrownBy(() -> projectService.assertMinimumRole(PROJECT_ID, MEMBER_ID, ProjectRole.OWNER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Insufficient permissions");
        }

        @Test
        @DisplayName("VIEWER does not satisfy MEMBER minimum")
        void viewerDoesNotSatisfyMember() {
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, VIEWER_ID))
                    .thenReturn(Optional.of(ProjectRole.VIEWER));

            assertThatThrownBy(() -> projectService.assertMinimumRole(PROJECT_ID, VIEWER_ID, ProjectRole.MEMBER))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("throws DomainException when user is not in project")
        void throwsWhenUserNotInProject() {
            when(memberPort.findRoleByProjectIdAndUserId(PROJECT_ID, STRANGER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.assertMinimumRole(PROJECT_ID, STRANGER_ID, ProjectRole.VIEWER))
                    .isInstanceOf(DomainException.class);
        }
    }


    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("aggregates counts and computes total")
        void aggregatesCountsCorrectly() {
            Map<ProjectStatus, Long> counts = new EnumMap<>(ProjectStatus.class);
            counts.put(ProjectStatus.ACTIVE, 4L);
            counts.put(ProjectStatus.ARCHIVED, 2L);
            when(projectPort.countByStatus(OWNER_ID)).thenReturn(counts);

            ProjectStats stats = projectService.getStats(OWNER_ID);

            assertThat(stats.total()).isEqualTo(6L);
            assertThat(stats.active()).isEqualTo(4L);
            assertThat(stats.archived()).isEqualTo(2L);
        }

        @Test
        @DisplayName("defaults missing statuses to zero")
        void defaultsMissingStatusesToZero() {
            when(projectPort.countByStatus(OWNER_ID)).thenReturn(new EnumMap<>(ProjectStatus.class));

            ProjectStats stats = projectService.getStats(OWNER_ID);

            assertThat(stats.active()).isZero();
            assertThat(stats.archived()).isZero();
            assertThat(stats.total()).isZero();
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("delegates to port and returns its page")
        void delegatesToPort() {
            ProjectSearchCriteria criteria = ProjectSearchCriteria.builder()
                    .memberId(OWNER_ID)
                    .status(ProjectStatus.ACTIVE)
                    .build();
            Page<Project> portResult = new Page<>(List.of(activeProject()), 1L, 0, 10);
            when(projectPort.search(criteria, 0, 10)).thenReturn(portResult);

            Page<Project> result = projectService.search(criteria, 0, 10);

            assertThat(result.totalElements()).isEqualTo(1L);
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("returns empty page when port returns no results")
        void returnsEmptyPage() {
            ProjectSearchCriteria criteria = ProjectSearchCriteria.builder().build();
            when(projectPort.search(criteria, 0, 10)).thenReturn(Page.empty(0, 10));

            Page<Project> result = projectService.search(criteria, 0, 10);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }


    @Nested
    @DisplayName("list methods")
    class ListMethods {

        @Test
        @DisplayName("getAllProjects delegates to port")
        void getAllProjects() {
            when(projectPort.findAll()).thenReturn(List.of(activeProject()));

            List<Project> result = projectService.getAllProjects();

            assertThat(result).hasSize(1);
            verify(projectPort).findAll();
        }

        @Test
        @DisplayName("getProjectsByMember filters by userId")
        void getProjectsByMember() {
            when(projectPort.findByMemberId(MEMBER_ID)).thenReturn(List.of(activeProject()));

            List<Project> result = projectService.getProjectsByMember(MEMBER_ID);

            assertThat(result).hasSize(1);
            verify(projectPort).findByMemberId(MEMBER_ID);
        }

        @Test
        @DisplayName("returns empty list when no projects match")
        void returnsEmptyList() {
            when(projectPort.findAll()).thenReturn(List.of());

            List<Project> result = projectService.getAllProjects();

            assertThat(result).isEmpty();
        }
    }
}