package com.elyadata.feature.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a valid hex code like #3B82F6")
        String color
) {}