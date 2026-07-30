package com.elyadata.feature.user.infrastructure.repository;

import com.elyadata.feature.user.domain.model.UserRole;
import com.elyadata.feature.user.infrastructure.entity.UserEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserRepositoryTest {

    @Inject UserRepository repository;
    @Inject EntityManager  em;

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private UserEntity persistNew(String username, String email, UserRole role) {
        UserEntity e = new UserEntity();
        e.setUsername(username);
        e.setEmail(email);
        e.setRole(role);
        e.setEnabled(true);
        e.setCreatedBy(ADMIN_ID);
        em.persist(e);
        em.flush();
        return e;
    }

    @Test @TestTransaction @Order(1)
    @DisplayName("findByIdOptional - should return user when exists")
    void findByIdOptional_existingId_returnsUser() {
        UserEntity saved = persistNew("alice", "alice@example.com", UserRole.OPERATOR);

        Optional<UserEntity> found = repository.findByIdOptional(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test @TestTransaction @Order(2)
    @DisplayName("findByIdOptional - should return empty for unknown id")
    void findByIdOptional_unknownId_returnsEmpty() {
        assertThat(repository.findByIdOptional(UUID.randomUUID())).isEmpty();
    }


    @Test @TestTransaction @Order(3)
    @DisplayName("listAll - should return all persisted users")
    void listAll_multipleUsers_returnsAll() {
        persistNew("bob",   "bob@example.com",   UserRole.READER);
        persistNew("carol", "carol@example.com", UserRole.ADMIN);

        List<UserEntity> result = repository.listAll();

        assertThat(result).extracting(UserEntity::getUsername)
                          .contains("bob", "carol");
    }

    @Test @TestTransaction @Order(4)
    @DisplayName("findByUsername - should return user when username matches")
    void findByUsername_existingUsername_returnsUser() {
        persistNew("dave", "dave@example.com", UserRole.OPERATOR);

        Optional<UserEntity> result = repository.findByUsername("dave");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("dave@example.com");
    }

    @Test @TestTransaction @Order(5)
    @DisplayName("findByUsername - should return empty for unknown username")
    void findByUsername_unknownUsername_returnsEmpty() {
        assertThat(repository.findByUsername("nobody")).isEmpty();
    }

    @Test @TestTransaction @Order(6)
    @DisplayName("findByEmail - should return user when email matches")
    void findByEmail_existingEmail_returnsUser() {
        persistNew("eve", "eve@example.com", UserRole.READER);

        Optional<UserEntity> result = repository.findByEmail("eve@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("eve");
    }

    @Test @TestTransaction @Order(7)
    @DisplayName("existsByUsername - should return true when username exists")
    void existsByUsername_existingUsername_returnsTrue() {
        persistNew("frank", "frank@example.com", UserRole.READER);

        assertThat(repository.existsByUsername("frank")).isTrue();
    }

    @Test @TestTransaction @Order(8)
    @DisplayName("existsByUsername - should return false when username not found")
    void existsByUsername_unknownUsername_returnsFalse() {
        assertThat(repository.existsByUsername("ghost")).isFalse();
    }

    @Test @TestTransaction @Order(9)
    @DisplayName("existsByEmail - should return true when email exists")
    void existsByEmail_existingEmail_returnsTrue() {
        persistNew("grace", "grace@example.com", UserRole.OPERATOR);

        assertThat(repository.existsByEmail("grace@example.com")).isTrue();
    }

    @Test @TestTransaction @Order(10)
    @DisplayName("existsByEmail - should return false when email not found")
    void existsByEmail_unknownEmail_returnsFalse() {
        assertThat(repository.existsByEmail("unknown@example.com")).isFalse();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // deleteById
    // ═════════════════════════════════════════════════════════════════════════

    @Test @TestTransaction @Order(11)
    @DisplayName("deleteById - should hard-delete the entity")
    void deleteById_existingEntity_removesFromDatabase() {
        UserEntity saved = persistNew("henry", "henry@example.com", UserRole.READER);
        UUID id = saved.getId();

        repository.deleteById(id);
        em.flush();

        assertThat(repository.findByIdOptional(id)).isEmpty();
    }

    @Test @TestTransaction @Order(12)
    @DisplayName("deleteById - should be safe when entity does not exist")
    void deleteById_nonExistentId_doesNotThrow() {
        assertThatNoException()
                .isThrownBy(() -> repository.deleteById(UUID.randomUUID()));
    }
}
