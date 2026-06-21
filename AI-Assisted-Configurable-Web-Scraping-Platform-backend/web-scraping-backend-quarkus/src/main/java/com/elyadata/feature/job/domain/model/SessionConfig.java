package com.elyadata.feature.job.domain.model;

import lombok.Builder;


@Builder(toBuilder = true)
public record SessionConfig(
        boolean enabled,
        LoginMode mode,
        String loginUrl,
        String usernameSelector,
        String passwordSelector,
        String submitSelector,
        String successSelector,
        String usernameEnc,
        String passwordEnc,
        String importedStorageState
) {

    public enum LoginMode {
        FORM,
        COOKIE_IMPORT
    }

    public static SessionConfig disabled() {
        return SessionConfig.builder().enabled(false).build();
    }

    public boolean isUsable() {
        return enabled && mode != null;
    }
}