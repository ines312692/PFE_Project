package com.elyadata.feature.user.application.dto;

import com.elyadata.feature.user.domain.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email String email,
        UserRole role
) {
    public CreateUserRequest {
        if (role == null) role = UserRole.READER;
    }
}