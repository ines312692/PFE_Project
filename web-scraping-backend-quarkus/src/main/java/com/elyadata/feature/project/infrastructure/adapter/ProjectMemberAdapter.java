package com.elyadata.feature.project.infrastructure.adapter;

import com.elyadata.feature.project.domain.model.ProjectMember;
import com.elyadata.feature.project.domain.model.ProjectRole;
import com.elyadata.feature.project.domain.service.port.ProjectMemberPort;
import com.elyadata.feature.project.infrastructure.mapper.ProjectEntityMapper;
import com.elyadata.feature.project.infrastructure.repository.ProjectMemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class ProjectMemberAdapter implements ProjectMemberPort {

    private final ProjectMemberRepository repository;
    private final ProjectEntityMapper     mapper;

    @Override
    public ProjectMember save(ProjectMember member) {
        var entity = mapper.toEntity(member);
        repository.persistOrMerge(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId) {
        return repository.findByProjectIdAndUserId(projectId, userId).map(mapper::toDomain);
    }

    @Override
    public Optional<ProjectRole> findRoleByProjectIdAndUserId(UUID projectId, UUID userId) {
        return repository.findRoleByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public List<ProjectMember> findByProjectId(UUID projectId) {
        return repository.findByProjectId(projectId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteByProjectIdAndUserId(UUID projectId, UUID userId) {
        repository.deleteByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public void deleteAllByProjectId(UUID projectId) {
        repository.deleteAllByProjectId(projectId);
    }

    @Override
    public boolean existsByProjectIdAndUserId(UUID projectId, UUID userId) {
        return repository.existsByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public Map<UUID, Long> countByProjectIds(Collection<UUID> projectIds) {
        return repository.countByProjectIds(projectIds);
    }
}