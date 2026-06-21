package com.elyadata.feature.user.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserPort userPort;

    @Override
    @Transactional
    public User createUser(User user, UUID createdBy) {
        validateUniqueUsername(user.username());
        validateUniqueEmail(user.email());

        User toCreate = User.builder()
                .username(user.username())
                .email(user.email())
                .role(user.role())
                .enabled(true)
                .createdBy(createdBy)
                .build();

        User created = userPort.save(toCreate);
        log.info("[USER] Created id={} username={} by={}", created.id(), created.username(), createdBy);
        return created;
    }

    @Override
    public List<User> getAllUsers() {
        return userPort.findAll();
    }

    @Override
    public User getUserById(UUID id) {
        return findUserOrThrow(id);
    }

    @Override
    @Transactional
    public User updateUser(UUID id, User user, UUID updatedBy) {
        User existing = findUserOrThrow(id);

        if (user.username() != null && !user.username().equals(existing.username())) {
            validateUniqueUsername(user.username());
        }
        if (user.email() != null && !user.email().equals(existing.email())) {
            validateUniqueEmail(user.email());
        }

        User toUpdate = User.builder()
                .id(id)
                .username(user.username() != null ? user.username() : existing.username())
                .email(user.email() != null ? user.email() : existing.email())
                .role(user.role() != null ? user.role() : existing.role())
                .enabled(user.enabled() != null ? user.enabled() : existing.enabled())
                .createdBy(existing.createdBy())
                .updatedBy(updatedBy)
                .createdAt(existing.createdAt())
                .build();

        User updated = userPort.update(toUpdate);
        log.info("[USER] Updated id={} by={}", id, updatedBy);
        return updated;
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        if (userPort.findById(id).isEmpty()) {
            log.debug("[USER] Delete called on non-existent id={}, ignoring (idempotent)", id);
            return;
        }
        userPort.deleteById(id);
        log.info("[USER] Deleted id={}", id);
    }

    private User findUserOrThrow(UUID id) {
        return userPort.findById(id)
                .orElseThrow(() -> DomainException.notFound("User not found: " + id));
    }

    private void validateUniqueUsername(String username) {
        if (userPort.existsByUsername(username)) {
            throw DomainException.alreadyExists("Username already taken: " + username);
        }
    }

    private void validateUniqueEmail(String email) {
        if (userPort.existsByEmail(email)) {
            throw DomainException.alreadyExists("Email already registered: " + email);
        }
    }

    @Override
    @Transactional
    public User updateAvatar(UUID userId, String avatarUrl) {
        User existing = findUserOrThrow(userId);
        User toUpdate = existing.toBuilder()
                .avatarUrl(avatarUrl)
                .updatedBy(userId)
                .build();
        User updated = userPort.update(toUpdate);
        log.info("[USER] Avatar updated id={}", userId);
        return updated;
    }
}