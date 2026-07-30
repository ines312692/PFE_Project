package com.elyadata.feature.user.domain.service.port;

import com.elyadata.feature.user.domain.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPort {
    User save(User user);

    List<User> findAll();

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    User update(User user);

    void deleteById(UUID id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findAllByIds(Collection<UUID> ids);
}