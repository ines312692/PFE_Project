package com.elyadata.feature.audit.domain.model;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    LAUNCH,
    PAUSE,
    DUPLICATE,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    ROLE_CHANGE,
    STATUS_CHANGE,
    SECRET_ROTATION,
    EXPORT,
    OTHER;

    public static AuditAction fromHttpMethod(String method) {
        if (method == null) return OTHER;
        return switch (method.toUpperCase()) {
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> OTHER;
        };
    }
}