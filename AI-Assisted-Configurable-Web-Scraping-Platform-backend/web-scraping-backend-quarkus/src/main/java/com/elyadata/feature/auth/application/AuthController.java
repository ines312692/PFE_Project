package com.elyadata.feature.auth.application;

import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.model.UserRole;
import com.elyadata.feature.user.domain.service.port.UserPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    @ConfigProperty(name = "bff.keycloak.base-url",
            defaultValue = "http://localhost:8180/realms/elyadata/protocol/openid-connect")
    String keycloakBaseUrl;

    @ConfigProperty(name = "bff.keycloak.realm-url",
            defaultValue = "http://localhost:8180/realms/elyadata")
    String keycloakRealmUrl;

    @ConfigProperty(name = "bff.client.id", defaultValue = "scraping-bff")
    String clientId;

    @ConfigProperty(name = "bff.client.secret")
    String clientSecret;

    @ConfigProperty(name = "bff.callback-uri",
            defaultValue = "http://localhost:8080/v1/auth/callback")
    String callbackUri;

    @ConfigProperty(name = "bff.frontend-url", defaultValue = "http://localhost:4200")
    String frontendUrl;

    @ConfigProperty(name = "bff.cookie.secure", defaultValue = "false")
    boolean cookieSecure;

    private final UserPort userPort;

    @Inject
    public AuthController(UserPort userPort) {
        this.userPort = userPort;
    }

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String STATE_COOKIE = "oauth_state";

    private static final String ROOT_PATH = "/";
    private static final String AUTH_PATH = "/v1/auth";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final String PARAM_REDIRECT_URI = "&redirect_uri=";
    private static final String PARAM_STATE = "&state=";
    private static final String PARAM_THEME = "&theme=";
    private static final String PARAM_CLIENT_SECRET = "&client_secret=";
    private static final String KEY_AUTHENTICATED = "authenticated";
    private static final String KEY_ERROR = "error";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String CLAIM_EMAIL = "email";

    private static final String OIDC_SCOPE = "openid profile email roles";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final SecureRandom secureRandom = new SecureRandom();

    @GET
    @Path("/login")
    public Response login(@QueryParam("theme") @DefaultValue("light") String theme) {
        String state = generateState();
        String authUrl = keycloakBaseUrl + "/auth"
                + "?client_id=" + enc(clientId)
                + PARAM_REDIRECT_URI + enc(callbackUri)
                + "&response_type=code"
                + "&scope=" + enc(OIDC_SCOPE)
                + PARAM_STATE + state
                + PARAM_THEME + enc(theme);

        log.info("[BFF] Login initiated, redirecting to Keycloak");
        return Response.status(Response.Status.FOUND)
                .location(URI.create(authUrl))
                .cookie(stateCookie(state))
                .build();
    }

    @GET
    @Path("/register")
    public Response register(@QueryParam("theme") @DefaultValue("light") String theme) {
        String state = generateState();
        String regUrl = keycloakBaseUrl + "/registrations"
                + "?client_id=" + enc(clientId)
                + PARAM_REDIRECT_URI + enc(callbackUri)
                + "&response_type=code"
                + "&scope=" + enc(OIDC_SCOPE)
                + PARAM_STATE + state
                + PARAM_THEME + enc(theme);

        log.info("[BFF] Registration initiated, redirecting to Keycloak");
        return Response.status(Response.Status.FOUND)
                .location(URI.create(regUrl))
                .cookie(stateCookie(state))
                .build();
    }

    @GET
    @Path("/forgot-password")
    public Response forgotPassword(@QueryParam("theme") @DefaultValue("light") String theme) {
        String state = generateState();
        String resetUrl = keycloakRealmUrl + "/login-actions/reset-credentials"
                + "?client_id=" + enc(clientId)
                + PARAM_REDIRECT_URI + enc(callbackUri)
                + PARAM_STATE + state
                + PARAM_THEME + enc(theme);

        log.info("[BFF] Forgot-password initiated, redirecting to Keycloak reset page");
        return Response.status(Response.Status.FOUND)
                .location(URI.create(resetUrl))
                .cookie(stateCookie(state))
                .build();
    }

    @GET
    @Path("/callback")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @CookieParam(STATE_COOKIE) String savedState
    ) {
        if (error != null) {
            log.warn("[BFF] Keycloak error: {}", error);
            return redirectToFrontend("/login?error=" + enc(error));
        }
        if (savedState == null || !savedState.equals(state)) {
            log.warn("[BFF] State mismatch — possible CSRF");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid state parameter")
                    .build();
        }

        TokenResponse tokens;
        try {
            tokens = exchangeCode(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[BFF] Token exchange interrupted", e);
            return redirectToFrontend("/login?error=token_exchange_failed");
        } catch (Exception e) {
            log.error("[BFF] Token exchange failed", e);
            return redirectToFrontend("/login?error=token_exchange_failed");
        }

        try {
            provisionUserIfAbsent(tokens.accessToken());
        } catch (Exception e) {
            log.error("[BFF] Auto-provisioning failed (non-critical)", e);
        }

        log.info("[BFF] Login successful — setting HttpOnly cookies");
        return Response.status(Response.Status.FOUND)
                .location(URI.create(frontendUrl + "/jobs"))
                .cookie(
                        accessCookie(tokens.accessToken(), tokens.expiresIn()),
                        refreshCookie(tokens.refreshToken(), tokens.refreshExpiresIn()),
                        expiredCookie(STATE_COOKIE, URI.create(callbackUri).getPath())
                )
                .build();
    }

    @GET
    @Path("/me")
    public Response me(@CookieParam(ACCESS_COOKIE) String token) {
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(KEY_AUTHENTICATED, false))
                    .build();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/userinfo"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[BFF] /userinfo rejected token: status={}", resp.statusCode());
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of(KEY_AUTHENTICATED, false))
                        .build();
            }

            var claims = objectMapper.readTree(resp.body());
            var rolesNode = claims.path("realm_access").path("roles");
            String role = "READER";
            if (rolesNode.isArray()) {
                for (var r : rolesNode) {
                    String v = r.asText();
                    if (ROLE_ADMIN.equals(v)) {
                        role = ROLE_ADMIN;
                        break;
                    }
                    if (ROLE_OPERATOR.equals(v)) {
                        role = ROLE_OPERATOR;
                    }
                }
            }

            return Response.ok(Map.of(
                    KEY_AUTHENTICATED, true,
                    "id", claims.path("sub").asText(""),
                    "username", claims.path("preferred_username").asText(""),
                    CLAIM_EMAIL, claims.path(CLAIM_EMAIL).asText(""),
                    "role", role
            )).build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[BFF] /me interrupted", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(KEY_AUTHENTICATED, false))
                    .build();
        } catch (Exception e) {
            log.error("[BFF] Failed to call /userinfo in /me", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(KEY_AUTHENTICATED, false))
                    .build();
        }
    }

    @POST
    @Path("/refresh")
    public Response refresh(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(KEY_ERROR, "No refresh token"))
                    .build();
        }

        try {
            String body = "grant_type=refresh_token"
                    + "&client_id=" + enc(clientId)
                    + PARAM_CLIENT_SECRET + enc(clientSecret)
                    + "&refresh_token=" + enc(refreshToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/token"))
                    .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_FORM)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[BFF] Refresh failed: {}", resp.body());
                return Response.status(Response.Status.UNAUTHORIZED)
                        .cookie(expiredCookie(ACCESS_COOKIE, ROOT_PATH),
                                expiredCookie(REFRESH_COOKIE, AUTH_PATH))
                        .entity(Map.of(KEY_ERROR, "Refresh failed"))
                        .build();
            }

            TokenResponse tokens = parseTokenResponse(resp.body());
            return Response.ok(Map.of("refreshed", true))
                    .cookie(
                            accessCookie(tokens.accessToken(), tokens.expiresIn()),
                            refreshCookie(tokens.refreshToken(), tokens.refreshExpiresIn())
                    )
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[BFF] Refresh interrupted", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(KEY_ERROR, "Internal error"))
                    .build();
        } catch (Exception e) {
            log.error("[BFF] Refresh error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(KEY_ERROR, "Internal error"))
                    .build();
        }
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                String body = "client_id=" + enc(clientId)
                        + PARAM_CLIENT_SECRET + enc(clientSecret)
                        + "&refresh_token=" + enc(refreshToken);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(keycloakBaseUrl + "/logout"))
                        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_FORM)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("[BFF] Token revoked at Keycloak");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[BFF] Keycloak revocation interrupted", e);
            } catch (Exception e) {
                log.warn("[BFF] Keycloak revocation failed (non-critical)", e);
            }
        }

        return Response.ok(Map.of("loggedOut", true))
                .cookie(
                        expiredCookie(ACCESS_COOKIE, ROOT_PATH),
                        expiredCookie(REFRESH_COOKIE, AUTH_PATH)
                )
                .build();
    }

    @Transactional
    void provisionUserIfAbsent(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakBaseUrl + "/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            log.warn("[BFF] provisionUserIfAbsent: /userinfo returned {}", resp.statusCode());
            return;
        }

        JsonNode claims = objectMapper.readTree(resp.body());
        String sub = claims.path("sub").asText(null);
        String email = claims.path(CLAIM_EMAIL).asText(null);
        String username = claims.path("preferred_username").asText(null);

        if (sub == null || email == null) {
            log.warn("[BFF] provisionUserIfAbsent: missing sub or email in claims");
            return;
        }

        if (userPort.findByEmail(email).isPresent()) {
            log.debug("[BFF] User already provisioned: email={}", email);
            return;
        }

        UserRole role = UserRole.READER;
        JsonNode roles = claims.path("realm_access").path("roles");
        if (roles.isArray()) {
            for (JsonNode r : roles) {
                String v = r.asText();
                if (ROLE_ADMIN.equals(v)) {
                    role = UserRole.ADMIN;
                    break;
                }
                if (ROLE_OPERATOR.equals(v)) {
                    role = UserRole.OPERATOR;
                }
            }
        }

        User newUser = User.builder()
                .id(UUID.fromString(sub))
                .username(username)
                .email(email)
                .role(role)
                .enabled(true)
                .build();

        try {
            userPort.save(newUser);
            log.info("[BFF] User provisioned from Keycloak: id={} email={} role={}", sub, email, role);
        } catch (PersistenceException e) {
            log.debug("[BFF] User already provisioned (concurrent login): email={}", email);
        }
    }

    private TokenResponse exchangeCode(String code) throws IOException, InterruptedException {
        String body = "grant_type=authorization_code"
                + "&client_id=" + enc(clientId)
                + PARAM_CLIENT_SECRET + enc(clientSecret)
                + "&code=" + enc(code)
                + PARAM_REDIRECT_URI + enc(callbackUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakBaseUrl + "/token"))
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_FORM)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Token exchange returned " + resp.statusCode() + ": " + resp.body());
        }
        return parseTokenResponse(resp.body());
    }

    private TokenResponse parseTokenResponse(String json) throws IOException {
        var tree = objectMapper.readTree(json);
        return new TokenResponse(
                tree.path(ACCESS_COOKIE).asText(),
                tree.path(REFRESH_COOKIE).asText(),
                tree.path("expires_in").asInt(900),
                tree.path("refresh_expires_in").asInt(1800)
        );
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private NewCookie accessCookie(String value, int maxAge) {
        return new NewCookie.Builder(ACCESS_COOKIE)
                .value(value).maxAge(maxAge)
                .httpOnly(true).secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .path(ROOT_PATH).build();
    }

    private NewCookie refreshCookie(String value, int maxAge) {
        return new NewCookie.Builder(REFRESH_COOKIE)
                .value(value).maxAge(maxAge)
                .httpOnly(true).secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .path(AUTH_PATH).build();
    }

    private NewCookie stateCookie(String state) {
        return new NewCookie.Builder(STATE_COOKIE)
                .value(state).maxAge(300)
                .httpOnly(true).secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .path(URI.create(callbackUri).getPath()).build();
    }

    private NewCookie expiredCookie(String name, String path) {
        return new NewCookie.Builder(name)
                .value("").maxAge(0)
                .httpOnly(true).secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .path(path).build();
    }

    private Response redirectToFrontend(String path) {
        return Response.status(Response.Status.FOUND)
                .location(URI.create(frontendUrl + path))
                .build();
    }

    private record TokenResponse(
            String accessToken,
            String refreshToken,
            int expiresIn,
            int refreshExpiresIn
    ) {
    }
}