package com.elyadata.feature.project.domain.service;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.*;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    Project createProject(Project project, UUID createdBy);

    Page<Project> search(ProjectSearchCriteria criteria, int page, int size);

    ProjectStats getStats(UUID memberId);

    Project getProjectById(UUID id);

    Project updateProject(UUID id, Project project, UUID updatedBy);

    void archiveProject(UUID id, UUID requestedBy);

    void deleteProject(UUID id, UUID requestedBy);

    ProjectMember addMember(UUID projectId, UUID targetUserId, ProjectRole role, UUID requestedBy);

    void removeMember(UUID projectId, UUID targetUserId, UUID requestedBy);

    ProjectMember updateMemberRole(UUID projectId, UUID targetUserId, ProjectRole newRole, UUID requestedBy);

    List<ProjectMember> getProjectMembers(UUID projectId);

    void assertMemberAccess(UUID projectId, UUID userId);

    void assertMinimumRole(UUID projectId, UUID userId, ProjectRole minimumRole);

    List<Project> getAllProjects();

    List<Project> getProjectsByMember(UUID userId);
}