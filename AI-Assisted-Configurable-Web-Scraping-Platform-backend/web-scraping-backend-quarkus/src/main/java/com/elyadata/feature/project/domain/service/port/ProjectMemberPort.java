package com.elyadata.feature.project.domain.service.port;

import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.domain.model.ProjectRole;

import java.util.*;

public interface ProjectMemberPort {

    ProjectMember save(ProjectMember member);

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectRole> findRoleByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findByProjectId(UUID projectId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteAllByProjectId(UUID projectId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    Map<UUID, Long> countByProjectIds(Collection<UUID> projectIds);
}