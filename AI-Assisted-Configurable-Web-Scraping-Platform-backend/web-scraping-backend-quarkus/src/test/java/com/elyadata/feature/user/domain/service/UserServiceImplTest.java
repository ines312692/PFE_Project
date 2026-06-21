package com.elyadata.feature.user.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.model.UserRole;
import com.elyadata.feature.user.domain.service.port.UserPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserPort userPort;

    @InjectMocks UserServiceImpl sut;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private User aUser() {
        return User.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .role(UserRole.OPERATOR)
                .enabled(true)
                .createdBy(ADMIN_ID)
                .createdAt(Instant.now())
                .build();
    }

    @Nested @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user with enabled=true regardless of input")
        void createUser_validRequest_persistsWithEnabledTrue() {
            User input = User.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .role(UserRole.READER)
                    .build();

            when(userPort.existsByUsername("newuser")).thenReturn(false);
            when(userPort.existsByEmail("new@example.com")).thenReturn(false);
            when(userPort.save(any())).thenReturn(aUser());

            sut.createUser(input, ADMIN_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userPort).save(captor.capture());
            assertThat(captor.getValue().enabled()).isTrue();
            assertThat(captor.getValue().createdBy()).isEqualTo(ADMIN_ID);
            assertThat(captor.getValue().id()).isNull(); // ID must not be set before save
        }

        @Test
        @DisplayName("should throw when username is already taken")
        void createUser_duplicateUsername_throwsDomainException() {
            User input = User.builder()
                    .username("taken")
                    .email("unique@example.com")
                    .role(UserRole.READER)
                    .build();

            when(userPort.existsByUsername("taken")).thenReturn(true);

            assertThatThrownBy(() -> sut.createUser(input, ADMIN_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Username already taken");

            verify(userPort, never()).save(any());
        }

        @Test
        @DisplayName("should throw when email is already registered")
        void createUser_duplicateEmail_throwsDomainException() {
            User input = User.builder()
                    .username("newuser")
                    .email("taken@example.com")
                    .role(UserRole.READER)
                    .build();

            when(userPort.existsByUsername("newuser")).thenReturn(false);
            when(userPort.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> sut.createUser(input, ADMIN_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Email already registered");

            verify(userPort, never()).save(any());
        }
    }

    @Nested @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("should return user when found")
        void getUserById_existingId_returnsUser() {
            when(userPort.findById(USER_ID)).thenReturn(Optional.of(aUser()));

            User result = sut.getUserById(USER_ID);

            assertThat(result.id()).isEqualTo(USER_ID);
            assertThat(result.username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should throw DomainException when user not found")
        void getUserById_unknownId_throwsDomainException() {
            UUID unknownId = UUID.randomUUID();
            when(userPort.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getUserById(unknownId))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("should return all users")
        void getAllUsers_multipleUsers_returnsList() {
            when(userPort.findAll()).thenReturn(List.of(aUser(), aUser()));

            List<User> result = sut.getAllUsers();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no users")
        void getAllUsers_noUsers_returnsEmptyList() {
            when(userPort.findAll()).thenReturn(Collections.emptyList());

            assertThat(sut.getAllUsers()).isEmpty();
        }
    }

    @Nested @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("should update only provided fields, keeping existing ones")
        void updateUser_partialUpdate_mergesWithExisting() {
            User existing = aUser();
            User updateRequest = User.builder()
                    .username("updateduser")
                    .build(); // email/role/enabled are null

            when(userPort.findById(USER_ID)).thenReturn(Optional.of(existing));
            when(userPort.existsByUsername("updateduser")).thenReturn(false);
            when(userPort.update(any())).thenReturn(existing.toBuilder().username("updateduser").build());

            sut.updateUser(USER_ID, updateRequest, ADMIN_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userPort).update(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.username()).isEqualTo("updateduser");
            assertThat(saved.email()).isEqualTo(existing.email());     // preserved
            assertThat(saved.role()).isEqualTo(existing.role());       // preserved
            assertThat(saved.enabled()).isEqualTo(existing.enabled()); // preserved
            assertThat(saved.createdBy()).isEqualTo(existing.createdBy()); // never changed
            assertThat(saved.updatedBy()).isEqualTo(ADMIN_ID);
        }

        @Test
        @DisplayName("should not validate username uniqueness if username unchanged")
        void updateUser_sameUsername_skipsUniquenessCheck() {
            User existing = aUser();
            User updateRequest = User.builder()
                    .username("testuser") // same as existing
                    .email("newemail@example.com")
                    .build();

            when(userPort.findById(USER_ID)).thenReturn(Optional.of(existing));
            when(userPort.existsByEmail("newemail@example.com")).thenReturn(false);
            when(userPort.update(any())).thenReturn(existing);

            sut.updateUser(USER_ID, updateRequest, ADMIN_ID);

            verify(userPort, never()).existsByUsername(any());
        }

        @Test
        @DisplayName("should throw when new username is already taken by another user")
        void updateUser_duplicateUsername_throwsDomainException() {
            User existing = aUser();
            User updateRequest = User.builder().username("othertaken").build();

            when(userPort.findById(USER_ID)).thenReturn(Optional.of(existing));
            when(userPort.existsByUsername("othertaken")).thenReturn(true);

            assertThatThrownBy(() -> sut.updateUser(USER_ID, updateRequest, ADMIN_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Username already taken");

            verify(userPort, never()).update(any());
        }

        @Test
        @DisplayName("should throw when updating non-existent user")
        void updateUser_unknownId_throwsDomainException() {
            UUID unknownId = UUID.randomUUID();
            when(userPort.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.updateUser(unknownId, aUser(), ADMIN_ID))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should delete existing user")
        void deleteUser_existingId_callsDeleteById() {
            when(userPort.findById(USER_ID)).thenReturn(Optional.of(aUser()));

            sut.deleteUser(USER_ID);

            verify(userPort).deleteById(USER_ID);
        }

        @Test
        @DisplayName("should be idempotent when user does not exist")
        void deleteUser_nonExistentId_doesNothing() {
            UUID unknownId = UUID.randomUUID();
            when(userPort.findById(unknownId)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> sut.deleteUser(unknownId));
            verify(userPort, never()).deleteById(any());
        }
    }
}
