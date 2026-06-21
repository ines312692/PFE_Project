package com.elyadata.common.configurations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Provider
@ApplicationScoped
public class SecurityFilter implements ContainerRequestFilter {

    private static final String ACCESS_COOKIE = "access_token";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/q/health",
            "/q/health/live",
            "/q/health/ready",
            "/q/metrics",
            "/q/swagger-ui",
            "/q/openapi",
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/callback",
            "/v1/auth/refresh",
            "/v1/auth/logout",
            "/v1/auth/me",
            "/v1/templates"
    );

    @Override
    public void filter(ContainerRequestContext ctx) {
        String method = ctx.getMethod();
        String path = ctx.getUriInfo().getPath();

        if ("OPTIONS".equalsIgnoreCase(method)) return;
        if (isExcluded(path)) return;

        jakarta.ws.rs.core.Cookie accessCookie = ctx.getCookies().get(ACCESS_COOKIE);
        if (accessCookie != null && !accessCookie.getValue().isBlank()) {
            log.debug("[SECURITY] Valid access_token cookie found for path={}", path);
            return;
        }

        String authHeader = ctx.getHeaderString("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("[SECURITY] Valid Bearer token found for path={}", path);
            return;
        }

        log.warn("[SECURITY] Unauthorized — no token found for path={}", path);

        String origin = ctx.getHeaderString("Origin");
        Response.ResponseBuilder rb = Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Unauthorized", "path", path));

        if (origin != null) {
            rb.header("Access-Control-Allow-Origin", origin)
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Vary", "Origin");
        }

        ctx.abortWith(rb.build());
    }

    private boolean isExcluded(String path) {
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return true;
        if (path.startsWith("/v1/notifications")) return true;
        if (path.startsWith("/v1/webhooks/jobs/")) return true;
        if (path.startsWith("/v1/captcha-interventions/")) return true;
        return path.startsWith("/v1/jobs/") && path.endsWith("/scrape-result");
    }
}