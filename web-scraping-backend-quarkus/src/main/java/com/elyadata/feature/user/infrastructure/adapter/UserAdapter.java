package com.elyadata.feature.user.infrastructure.adapter;

import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import com.elyadata.feature.user.infrastructure.mapper.UserEntityMapper;
import com.elyadata.feature.user.infrastructure.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;

    @Override
    @Transactional
    public User save(User user) {
        var entity = userEntityMapper.toEntity(user);
        userRepository.persist(entity);
        return userEntityMapper.toDomain(entity);
    }

    @Override
    public List<User> findAll() {
        return userRepository.listAll().stream().map(userEntityMapper::toDomain).toList();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userRepository.findByIdOptional(id).map(userEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username).map(userEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email).map(userEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public User update(User user) {
        var entity = userRepository.findById(user.id());
        if (entity == null) throw new jakarta.ws.rs.NotFoundException("User not found: " + user.id());

        entity.setUsername(user.username());
        entity.setEmail(user.email());
        entity.setRole(user.role());
        entity.setEnabled(user.enabled());
        entity.setAvatarUrl(user.avatarUrl());
        entity.setCreatedBy(user.createdBy());
        entity.setUpdatedBy(user.updatedBy());
        return userEntityMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        userRepository.deleteById(id);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public List<User> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllByIds(ids).stream()
                .map(userEntityMapper::toDomain)
                .toList();
    }

}