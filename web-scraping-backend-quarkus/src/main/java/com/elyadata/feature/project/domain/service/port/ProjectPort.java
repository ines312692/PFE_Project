package com.elyadata.feature.project.domain.service.port;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectSearchCriteria;
import com.elyadata.feature.project.domain.model.ProjectStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPort {

    Project save(Project project);

    Project update(Project project);

    Optional<Project> findById(UUID id);

    Page<Project> search(ProjectSearchCriteria criteria, int page, int size);

    Map<ProjectStatus, Long> countByStatus(UUID memberId);

    List<Project> findAll();

    List<Project> findByMemberId(UUID userId);

    void deleteById(UUID id);

    boolean existsById(UUID id);
}