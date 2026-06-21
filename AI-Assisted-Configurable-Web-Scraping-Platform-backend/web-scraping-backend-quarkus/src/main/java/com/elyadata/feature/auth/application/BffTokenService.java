package com.elyadata.feature.auth.application;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.user.domain.service.port.UserPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequestScoped
public class BffTokenService {

    private static final String ACCESS_COOKIE = "access_token";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final String keycloakBaseUrl;
    private final HttpHeaders httpHeaders;
    private final UserPort userPort;

    private JsonNode cachedClaims;

    @Inject
    public BffTokenService(
            @ConfigProperty(name = "bff.keycloak.base-url",
                    defaultValue = "http://localhost:8180/realms/elyadata/protocol/openid-connect")
            String keycloakBaseUrl,
            HttpHeaders httpHeaders,
            UserPort userPort
    ) {
        this.keycloakBaseUrl = keycloakBaseUrl;
        this.httpHeaders = httpHeaders;
        this.userPort = userPort;
    }

    public Optional<JsonNode> getClaims() {
        if (cachedClaims != null) return Optional.of(cachedClaims);

        Cookie cookie = httpHeaders.getCookies().get(ACCESS_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            log.debug("[BffToken] No access_token cookie found");
            return Optional.empty();
        }

        String token = cookie.getValue();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/userinfo"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[BffToken] /userinfo rejected token: status={}", resp.statusCode());
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[BffToken] /userinfo call interrupted", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[BffToken] Failed to validate token via /userinfo", e);
            return Optional.empty();
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return Optional.empty();

            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );
            cachedClaims = MAPPER.readTree(payload);
            return Optional.of(cachedClaims);
        } catch (Exception e) {
            log.warn("[BffToken] Failed to decode JWT payload after /userinfo validation", e);
            return Optional.empty();
        }
    }

    public Optional<String> getEmail() {
        return getClaims().map(c -> c.path("email").asText(null));
    }

    public Optional<String> getUsername() {
        return getClaims().map(c -> c.path("preferred_username").asText(null));
    }

    public boolean hasRole(String role) {
        return getClaims().map(claims -> {
            JsonNode roles = claims.path("realm_access").path("roles");
            if (roles.isArray()) {
                for (JsonNode r : roles) {
                    if (role.equals(r.asText())) return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public UUID resolveCurrentUserId(String contextPath) {
        JsonNode claims = getClaims().orElseThrow(() ->
                DomainException.unauthorized(
                        "Missing or invalid access token",
                        contextPath));
        String sub = claims.path("sub").asText(null);
        if (sub != null) {
            try {
                UUID subUuid = UUID.fromString(sub);
                if (userPort.findById(subUuid).isPresent()) return subUuid;
            } catch (IllegalArgumentException ignored) {
                log.warn("[BffToken] sub claim is not a valid UUID: {}", sub);
            }
        }

        String email = claims.path("email").asText(null);
        if (email != null) {
            var byEmail = userPort.findByEmail(email);
            if (byEmail.isPresent()) return byEmail.get().id();
        }

        String username = claims.path("preferred_username").asText(null);
        if (username != null) {
            var byUsername = userPort.findByUsername(username);
            if (byUsername.isPresent()) return byUsername.get().id();
        }
        throw DomainException.notFound(
                "Authenticated user not found in database (sub=" + sub + ")",
                contextPath);
    }
}