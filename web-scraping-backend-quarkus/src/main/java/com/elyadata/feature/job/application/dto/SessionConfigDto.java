package com.elyadata.feature.job.application.dto;

public record SessionConfigDto(
        boolean enabled,
        String mode,
        String loginUrl,
        String usernameSelector,
        String passwordSelector,
        String submitSelector,
        String successSelector,
        String username,
        String password,
        String importedStorageState
) {
}