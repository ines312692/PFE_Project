package com.elyadata.feature.user.domain.service;

import com.elyadata.feature.user.domain.model.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User createUser(User user, UUID createdBy);

    List<User> getAllUsers();

    User getUserById(UUID id);

    User updateUser(UUID id, User user, UUID updatedBy);

    void deleteUser(UUID id);

    User updateAvatar(UUID userId, String avatarUrl);
}