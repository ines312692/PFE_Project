package com.elyadata.feature.audit.infrastructure.interceptor;

import com.elyadata.common.logging.HttpLogInterceptor;
import com.elyadata.feature.audit.domain.model.AuditAction;
import com.elyadata.feature.audit.domain.model.AuditLog;
import com.elyadata.feature.audit.domain.model.AuditOutcome;
import com.elyadata.feature.audit.domain.model.AuditResource;
import com.elyadata.feature.audit.domain.service.AuditLogService;
import com.elyadata.feature.auth.application.BffTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Provider
@Priority(Priorities.USER + 100)
public class AuditLogFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String USER_SNAPSHOT_PROP = "audit.userSnapshot";

    private static final Pattern UUID_IN_PATH = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private final AuditLogService auditLogService;
    private final BffTokenService bffToken;

    @Context
    ResourceInfo resourceInfo;

    public AuditLogFilter(AuditLogService auditLogService, BffTokenService bffToken) {
        this.auditLogService = auditLogService;
        this.bffToken = bffToken;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (shouldAudit(request)) return;
        UserSnapshot snapshot = readUserSnapshot();
        if (snapshot != null) {
            request.setProperty(USER_SNAPSHOT_PROP, snapshot);
        }
    }


    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (shouldAudit(request)) return;
        try {
            Auditable annotation = readAnnotation();
            if (annotation != null && annotation.skip()) return;

            AuditLog auditLog = buildAuditLog(request, response, annotation);
            auditLogService.record(auditLog);
        } catch (Exception e) {
            log.warn("[AUDIT] Filter failed silently: {}", e.getMessage());
        }
    }

    private AuditLog buildAuditLog(ContainerRequestContext request,
                                   ContainerResponseContext response,
                                   Auditable annotation) {
        String method = request.getMethod();
        String path = request.getUriInfo().getPath();
        int status = response.getStatus();

        AuditAction action = resolveAction(annotation, method, path, status);
        AuditResource resource = resolveResource(annotation, path);
        AuditOutcome outcome = status < 400 ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE;

        UserSnapshot snapshot = resolveUserSnapshot(request);

        return AuditLog.builder()
                .occurredAt(Instant.now())
                .userId(snapshot != null ? snapshot.userId() : null)
                .username(snapshot != null ? snapshot.username() : null)
                .userRole(snapshot != null ? snapshot.userRole() : null)
                .action(action)
                .resourceType(resource)
                .resourceId(extractResourceId(path))
                .httpMethod(method)
                .endpoint(truncate("/" + path, 500))
                .httpStatus(status)
                .outcome(outcome)
                .sourceIp(extractClientIp(request))
                .userAgent(truncate(request.getHeaderString("User-Agent"), 500))
                .correlationId(getMdcValue())
                .requestSummary(SensitiveDataMasker.mask(
                        (String) request.getProperty(RequestBodyCaptureFilter.CAPTURED_BODY_PROP)))
                .errorMessage(outcome == AuditOutcome.FAILURE ? extractErrorMessage(response) : null)
                .build();
    }

    private AuditAction resolveAction(Auditable annotation, String method, String path, int status) {
        if (annotation != null && annotation.action() != AuditAction.OTHER) {
            return annotation.action();
        }
        if (path.contains("/auth/login") || path.contains("/auth/callback")) {
            return status < 400 ? AuditAction.LOGIN : AuditAction.LOGIN_FAILED;
        }
        if (path.contains("/auth/logout")) {
            return AuditAction.LOGOUT;
        }
        return inferActionFromPath(method, path);
    }

    private AuditResource resolveResource(Auditable annotation, String path) {
        return (annotation != null && annotation.resource() != AuditResource.OTHER)
                ? annotation.resource()
                : AuditResource.fromPath(path);
    }

    private UserSnapshot resolveUserSnapshot(ContainerRequestContext request) {
        UserSnapshot cached = (UserSnapshot) request.getProperty(USER_SNAPSHOT_PROP);
        return cached != null ? cached : readUserSnapshot();
    }

    private UserSnapshot readUserSnapshot() {
        JsonNode claims = safeGetClaims();
        if (claims == null) return null;

        UUID userId = parseUserId(claims.path("sub").asText(null));
        String username = resolveUsername(claims);
        String userRole = resolveFirstRole(claims);

        return new UserSnapshot(userId, username, userRole);
    }

    private JsonNode safeGetClaims() {
        try {
            return bffToken.getClaims().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private UUID parseUserId(String sub) {
        if (sub == null) return null;
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveUsername(JsonNode claims) {
        String username = claims.path("preferred_username").asText(null);
        return username != null ? username : claims.path("email").asText(null);
    }

    private String resolveFirstRole(JsonNode claims) {
        JsonNode roles = claims.path("realm_access").path("roles");
        return (roles.isArray() && !roles.isEmpty()) ? roles.get(0).asText(null) : null;
    }

    private String extractErrorMessage(ContainerResponseContext response) {
        if (response.getEntity() == null) return null;
        try {
            return truncate(response.getEntity().toString(), 1000);
        } catch (Exception e) {
            return null;
        }
    }


    private boolean shouldAudit(ContainerRequestContext request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getUriInfo().getPath();
        return path == null
                || path.startsWith("q/")
                || path.startsWith("v1/audit-logs");
    }

    private Auditable readAnnotation() {
        try {
            Method m = resourceInfo.getResourceMethod();
            if (m != null && m.isAnnotationPresent(Auditable.class)) {
                return m.getAnnotation(Auditable.class);
            }
            Class<?> klass = resourceInfo.getResourceClass();
            return (klass != null && klass.isAnnotationPresent(Auditable.class))
                    ? klass.getAnnotation(Auditable.class)
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    private AuditAction inferActionFromPath(String method, String path) {
        String p = path.toLowerCase();
        if (p.endsWith("/launch")) return AuditAction.LAUNCH;
        if (p.endsWith("/pause")) return AuditAction.PAUSE;
        if (p.endsWith("/duplicate")) return AuditAction.DUPLICATE;
        if (p.contains("/webhook/rotate") || p.contains("/secret/rotate")) return AuditAction.SECRET_ROTATION;
        if (p.contains("/export")) return AuditAction.EXPORT;
        if (p.contains("/role")) return AuditAction.ROLE_CHANGE;
        if (p.contains("/enabled") || p.contains("/status")) return AuditAction.STATUS_CHANGE;
        return AuditAction.fromHttpMethod(method);
    }


    private String extractResourceId(String path) {
        if (path == null) return null;
        Matcher m = UUID_IN_PATH.matcher(path);
        return m.find() ? m.group(1) : null;
    }

    private String extractClientIp(ContainerRequestContext request) {
        String xff = request.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return truncate(xff.split(",")[0].trim(), 45);
        }
        String real = request.getHeaderString("X-Real-IP");
        return (real != null && !real.isBlank()) ? truncate(real, 45) : null;
    }

    private String getMdcValue() {
        try {
            return org.slf4j.MDC.get(HttpLogInterceptor.CORRELATION_ID_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record UserSnapshot(UUID userId, String username, String userRole) {
    }
}