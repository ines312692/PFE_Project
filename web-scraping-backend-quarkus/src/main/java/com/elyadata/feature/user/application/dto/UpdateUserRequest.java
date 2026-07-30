package com.elyadata.feature.user.application.dto;

import com.elyadata.feature.user.domain.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateUserRequest(
        @Size(min = 3, max = 50) String username,
        @Email String email,
        UserRole role,
        Boolean enabled
) {
}