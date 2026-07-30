package com.elyadata.feature.project.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.domain.model.ProjectStatus;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.project.domain.service.port.ProjectPort;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.ProjectSearchCriteria;
import com.elyadata.feature.project.domain.model.ProjectStats;

import java.util.Map;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectPort projectPort;
    private final ProjectMemberPort memberPort;
    private final UserPort userPort;
    private static final String MSG_PROJECT_NOT_FOUND = "Project not found: ";
    private static final String MSG_CANNOT_ASSIGN_OWNER = "Cannot assign OWNER role  transfer ownership instead.";
    private static final String MSG_USER_NOT_MEMBER = "User %s is not a member of project %s";

    @Override
    @Transactional
    public Project createProject(Project project, UUID createdBy) {
        Project toCreate = Project.builder()
                .name(project.name())
                .description(project.description())
                .color(project.color())
                .status(ProjectStatus.ACTIVE)
                .ownerId(createdBy)
                .createdBy(createdBy)
                .build();

        Project created = projectPort.save(toCreate);
        memberPort.save(ProjectMember.builder()
                .projectId(created.id())
                .userId(createdBy)
                .role(ProjectRole.OWNER)
                .build());

        log.info("[PROJECT] Created id={} name='{}' owner={}", created.id(), created.name(), createdBy);
        return created;
    }

    @Override
    public List<Project> getAllProjects() {
        return projectPort.findAll();
    }

    @Override
    public List<Project> getProjectsByMember(UUID userId) {
        return projectPort.findByMemberId(userId);
    }

    @Override
    public Project getProjectById(UUID id) {
        return projectPort.findById(id)
                .orElseThrow(() -> DomainException.notFound(MSG_PROJECT_NOT_FOUND + id));
    }

    @Override
    @Transactional
    public Project updateProject(UUID id, Project project, UUID updatedBy) {
        Project existing = getProjectById(id);

        Project updated = projectPort.update(Project.builder()
                .id(id)
                .name(project.name())
                .description(project.description())
                .color(project.color())
                .status(existing.status())
                .ownerId(existing.ownerId())
                .createdBy(existing.createdBy())
                .updatedBy(updatedBy)
                .build());

        log.info("[PROJECT] Updated id={} by={}", id, updatedBy);
        return updated;
    }

    @Override
    @Transactional
    public void archiveProject(UUID id, UUID requestedBy) {
        Project existing = getProjectById(id);
        assertMinimumRole(id, requestedBy, ProjectRole.OWNER);

        projectPort.update(existing.toBuilder()
                .status(ProjectStatus.ARCHIVED)
                .updatedBy(requestedBy)
                .build());

        log.info("[PROJECT] Archived id={} by={}", id, requestedBy);
    }

    @Override
    @Transactional
    public void deleteProject(UUID id, UUID requestedBy) {
        getProjectById(id); // assert exists
        assertMinimumRole(id, requestedBy, ProjectRole.OWNER);

        memberPort.deleteAllByProjectId(id);
        projectPort.deleteById(id);

        log.info("[PROJECT] Deleted id={} by={}", id, requestedBy);
    }

    @Override
    @Transactional
    public ProjectMember addMember(UUID projectId, UUID targetUserId, ProjectRole role, UUID requestedBy) {
        getProjectById(projectId);
        assertMinimumRole(projectId, requestedBy, ProjectRole.OWNER);

        if (role == ProjectRole.OWNER) {
            throw DomainException.notValid(MSG_CANNOT_ASSIGN_OWNER);
        }

        User targetUser = userPort.findById(targetUserId)
                .orElseThrow(() -> DomainException.notFound("User not found: " + targetUserId));

        if (memberPort.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw DomainException.notValid(
                    "User '" + targetUser.username() + "' is already a member of this project.");
        }

        ProjectMember saved = memberPort.save(ProjectMember.builder()
                .projectId(projectId)
                .userId(targetUserId)
                .role(role)
                .build());

        log.info("[PROJECT] Member added projectId={} userId={} role={} by={}",
                projectId, targetUserId, role, requestedBy);

        return saved.toBuilder()
                .username(targetUser.username())
                .email(targetUser.email())
                .build();
    }

    @Override
    @Transactional
    public void removeMember(UUID projectId, UUID targetUserId, UUID requestedBy) {
        getProjectById(projectId);
        assertMinimumRole(projectId, requestedBy, ProjectRole.OWNER);

        ProjectRole targetRole = memberPort.findRoleByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> DomainException.notFound(
                        MSG_USER_NOT_MEMBER.formatted(targetUserId, projectId)));

        if (targetRole == ProjectRole.OWNER) {
            throw DomainException.notValid("Cannot remove the project owner.");
        }

        memberPort.deleteByProjectIdAndUserId(projectId, targetUserId);
        log.info("[PROJECT] Member removed projectId={} userId={} by={}",
                projectId, targetUserId, requestedBy);
    }

    @Override
    @Transactional
    public ProjectMember updateMemberRole(UUID projectId, UUID targetUserId, ProjectRole newRole, UUID requestedBy) {
        getProjectById(projectId);
        assertMinimumRole(projectId, requestedBy, ProjectRole.OWNER);

        if (newRole == ProjectRole.OWNER) {
            throw DomainException.notValid("Cannot assign OWNER role — transfer ownership instead.");
        }

        ProjectMember existing = memberPort.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> DomainException.notFound(
                        "User " + targetUserId + " is not a member of project " + projectId));

        if (existing.role() == ProjectRole.OWNER) {
            throw DomainException.notValid("Cannot change the owner's role.");
        }

        ProjectMember updated = memberPort.save(existing.toBuilder().role(newRole).build());

        log.info("[PROJECT] Role updated projectId={} userId={} newRole={} by={}",
                projectId, targetUserId, newRole, requestedBy);
        return updated;
    }

    @Override
    public List<ProjectMember> getProjectMembers(UUID projectId) {
        getProjectById(projectId);
        List<ProjectMember> members = memberPort.findByProjectId(projectId);

        return members.stream()
                .map(m -> userPort.findById(m.userId())
                        .map(u -> m.toBuilder().username(u.username()).email(u.email()).build())
                        .orElse(m))
                .toList();
    }

    @Override
    public void assertMemberAccess(UUID projectId, UUID userId) {
        if (!memberPort.existsByProjectIdAndUserId(projectId, userId)) {
            throw DomainException.notFound(MSG_PROJECT_NOT_FOUND + projectId);
        }
    }

    @Override
    public void assertMinimumRole(UUID projectId, UUID userId, ProjectRole minimumRole) {
        ProjectRole actual = memberPort.findRoleByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> DomainException.notFound(MSG_PROJECT_NOT_FOUND + projectId));

        if (!hasMinimumRole(actual, minimumRole)) {
            throw DomainException.notValid(
                    "Insufficient permissions. Required: " + minimumRole + ", actual: " + actual);
        }
    }

    private boolean hasMinimumRole(ProjectRole actual, ProjectRole minimum) {
        return roleOrdinal(actual) >= roleOrdinal(minimum);
    }

    private int roleOrdinal(ProjectRole role) {
        return switch (role) {
            case VIEWER -> 0;
            case MEMBER -> 1;
            case OWNER -> 2;
        };
    }

    @Override
    public Page<Project> search(ProjectSearchCriteria criteria, int page, int size) {
        return projectPort.search(criteria, page, size);
    }

    @Override
    public ProjectStats getStats(UUID memberId) {
        Map<ProjectStatus, Long> counts = projectPort.countByStatus(memberId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return ProjectStats.builder()
                .total(total)
                .active(counts.getOrDefault(ProjectStatus.ACTIVE, 0L))
                .archived(counts.getOrDefault(ProjectStatus.ARCHIVED, 0L))
                .build();
    }
}