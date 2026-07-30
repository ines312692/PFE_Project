package com.elyadata.feature.auth.application;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.domain.service.port.UserPort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BffTokenServiceTest {

    @Mock
    HttpHeaders httpHeaders;

    @Mock
    UserPort userPort;

    BffTokenService service;

    @BeforeEach
    void setUp() {
        service = new BffTokenService(
                "http://keycloak:8080/realms/test/protocol/openid-connect",
                httpHeaders,
                userPort
        );
    }

    @Nested
    @DisplayName("getClaims")
    class GetClaims {

        @Test
        @DisplayName("returns empty when no access_token cookie present")
        void noCookie_returnsEmpty() {
            when(httpHeaders.getCookies()).thenReturn(Map.of());

            Optional<JsonNode> result = service.getClaims();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when access_token cookie value is blank")
        void blankCookieValue_returnsEmpty() {
            Cookie blankCookie = new Cookie.Builder("access_token").value("").build();
            when(httpHeaders.getCookies()).thenReturn(Map.of("access_token", blankCookie));

            Optional<JsonNode> result = service.getClaims();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasRole")
    class HasRole {

        @Test
        @DisplayName("returns false when no claims available (no cookie)")
        void noClaims_returnsFalse() {
            when(httpHeaders.getCookies()).thenReturn(Map.of());

            boolean result = service.hasRole("ADMIN");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getEmail")
    class GetEmail {

        @Test
        @DisplayName("returns empty when no claims available")
        void noClaims_returnsEmpty() {
            when(httpHeaders.getCookies()).thenReturn(Map.of());

            Optional<String> result = service.getEmail();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUsername")
    class GetUsername {

        @Test
        @DisplayName("returns empty when no claims available")
        void noClaims_returnsEmpty() {
            when(httpHeaders.getCookies()).thenReturn(Map.of());

            Optional<String> result = service.getUsername();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveCurrentUserId")
    class ResolveCurrentUserId {

        @Test
        @DisplayName("throws DomainException when no cookie present")
        void noCookie_throwsUnauthorized() {
            when(httpHeaders.getCookies()).thenReturn(Map.of());

            assertThatThrownBy(() -> service.resolveCurrentUserId("/v1/test"))
                    .isInstanceOf(DomainException.class);
        }
    }
}