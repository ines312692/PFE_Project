package com.elyadata.feature.project.infrastructure.adapter;

import com.elyadata.common.pagination.Page;
import com.elyadata.feature.project.domain.model.Project;
import com.elyadata.feature.project.domain.model.ProjectSearchCriteria;
import com.elyadata.feature.project.domain.model.ProjectStatus;
import com.elyadata.feature.project.domain.service.port.ProjectPort;
import com.elyadata.feature.project.infrastructure.entity.ProjectEntity;
import com.elyadata.feature.project.infrastructure.mapper.ProjectEntityMapper;
import com.elyadata.feature.project.infrastructure.repository.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ProjectAdapter implements ProjectPort {

    private final ProjectRepository   repository;
    private final ProjectEntityMapper mapper;

    @Override
    public Project save(Project project) {
        ProjectEntity entity = mapper.toEntity(project);
        repository.persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Project update(Project project) {
        ProjectEntity entity = mapper.toEntity(project);
        ProjectEntity merged = repository.findByIdOptional(project.id())
                .map(existing -> {
                    existing.setName(entity.getName());
                    existing.setDescription(entity.getDescription());
                    existing.setColor(entity.getColor());
                    existing.setStatus(entity.getStatus());
                    existing.setUpdatedBy(entity.getUpdatedBy());
                    return existing;
                })
                .orElse(entity);
        return mapper.toDomain(merged);
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return repository.findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<Project> findAll() {
        return repository.listAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Project> findByMemberId(UUID userId) {
        return repository.findByMemberId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.softDelete(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Page<Project> search(ProjectSearchCriteria criteria, int page, int size) {
        Page<ProjectEntity> entityPage = repository.search(criteria, page, size);
        if (entityPage.isEmpty()) return Page.empty(page, size);
        return entityPage.map(mapper::toDomain);
    }

    @Override
    public Map<ProjectStatus, Long> countByStatus(UUID memberId) {
        return repository.countByStatus(memberId);
    }
}