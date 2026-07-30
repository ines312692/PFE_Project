package com.elyadata.feature.project.infrastructure.adapter;

import com.elyadata.feature.project.domain.model.InvitationStatus;
import com.elyadata.feature.project.domain.model.ProjectInvitation;
import com.elyadata.feature.project.domain.service.port.ProjectInvitationPort;
import com.elyadata.feature.project.infrastructure.mapper.InvitationEntityMapper;
import com.elyadata.feature.project.infrastructure.repository.ProjectInvitationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ProjectInvitationAdapter implements ProjectInvitationPort {

    private final ProjectInvitationRepository repository;
    private final InvitationEntityMapper mapper;

    @Override
    public ProjectInvitation save(ProjectInvitation invitation) {
        if (invitation.id() == null) {
            var entity = mapper.toEntity(invitation);
            repository.persist(entity);
            return mapper.toDomain(entity);
        }
        return mapper.toDomain(repository.merge(mapper.toEntity(invitation)));
    }

    @Override
    public Optional<ProjectInvitation> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ProjectInvitation> findPendingByUser(UUID userId) {
        return repository.findByInvitedUserAndStatus(userId, InvitationStatus.PENDING)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ProjectInvitation> findByProjectAndStatus(UUID projectId, InvitationStatus status) {
        return repository.findByProjectIdAndStatus(projectId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsPending(UUID projectId, UUID invitedUser) {
        return repository.existsPending(projectId, invitedUser);
    }
}