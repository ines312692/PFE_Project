package com.elyadata.feature.audit.domain.model;

public enum AuditResource {
    JOB,
    USER,
    PROJECT,
    PROJECT_INVITATION,
    PROXY,
    PROXY_CONFIG,
    SCHEMA,
    TEMPLATE,
    WEBHOOK,
    ETL,
    EXPORT,
    AUTH,
    OTHER;

    public static AuditResource fromPath(String path) {
        if (path == null || path.isBlank()) return OTHER;
        String p = path.toLowerCase();
        if (p.contains("/jobs")) return JOB;
        if (p.contains("/users")) return USER;
        if (p.contains("/account")) return USER;
        if (p.contains("/invitations")) return PROJECT_INVITATION;
        if (p.contains("/projects")) return PROJECT;
        if (p.contains("/proxies")) return PROXY;
        if (p.contains("/proxy-config")) return PROXY_CONFIG;
        if (p.contains("/schema")) return SCHEMA;
        if (p.contains("/templates")) return TEMPLATE;
        if (p.contains("/webhook")) return WEBHOOK;
        if (p.contains("/etl")) return ETL;
        if (p.contains("/export")) return EXPORT;
        if (p.contains("/auth")) return AUTH;
        return OTHER;
    }
}