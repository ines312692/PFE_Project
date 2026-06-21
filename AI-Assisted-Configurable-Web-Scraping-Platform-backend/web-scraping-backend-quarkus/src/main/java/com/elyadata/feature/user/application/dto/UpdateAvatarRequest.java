package com.elyadata.feature.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateAvatarRequest(
        @NotBlank
        @Size(max = 500_000, message = "Avatar image is too large (max ~500KB)")
        String avatarUrl
) {
}