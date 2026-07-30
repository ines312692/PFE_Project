package com.elyadata.feature.proxy.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyTest {

    private Proxy.ProxyBuilder base() {
        return Proxy.builder()
                .id(UUID.randomUUID())
                .host("192.168.1.1")
                .port(8080)
                .protocol(ProxyProtocol.HTTP)
                .status(ProxyStatus.ACTIVE)
                .createdAt(Instant.now());
    }


    @Nested
    class Endpoint {

        @Test
        void combines_host_and_port() {
            Proxy proxy = base().host("10.0.0.1").port(3128).build();
            assertThat(proxy.endpoint()).isEqualTo("10.0.0.1:3128");
        }

        @Test
        void endpoint_uses_actual_port_value() {
            Proxy proxy = base().port(9999).build();
            assertThat(proxy.endpoint()).endsWith(":9999");
        }
    }


    @Nested
    class Url {

        @Test
        void http_proxy_url_has_correct_scheme() {
            Proxy proxy = base().protocol(ProxyProtocol.HTTP).build();
            assertThat(proxy.url()).startsWith("http://");
        }

        @Test
        void https_proxy_url_has_correct_scheme() {
            Proxy proxy = base().protocol(ProxyProtocol.HTTPS).build();
            assertThat(proxy.url()).startsWith("https://");
        }

        @Test
        void socks5_proxy_url_has_correct_scheme() {
            Proxy proxy = base().protocol(ProxyProtocol.SOCKS5).build();
            assertThat(proxy.url()).startsWith("socks5://");
        }

        @Test
        void url_includes_host_and_port() {
            Proxy proxy = base().host("proxy.example.com").port(1080)
                    .protocol(ProxyProtocol.HTTP).build();
            assertThat(proxy.url()).isEqualTo("http://proxy.example.com:1080");
        }
    }


    @Nested
    class HasCredentials {

        @Test
        void returns_true_when_username_is_set() {
            Proxy proxy = base().username("user123").password("pass").build();
            assertThat(proxy.hasCredentials()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void returns_false_when_username_is_blank_or_null(String username) {
            Proxy proxy = base().username(username).build();
            assertThat(proxy.hasCredentials()).isFalse();
        }

        @Test
        void returns_true_even_when_password_is_null() {
            Proxy proxy = base().username("user").password(null).build();
            assertThat(proxy.hasCredentials()).isTrue();
        }
    }


    @Nested
    class Builder {

        @Test
        void toBuilder_preserves_all_fields() {
            UUID id = UUID.randomUUID();
            Proxy original = base()
                    .id(id).host("1.2.3.4").port(8888)
                    .username("u").password("p")
                    .build();

            Proxy copy = original.toBuilder().build();

            assertThat(copy.id()).isEqualTo(id);
            assertThat(copy.host()).isEqualTo("1.2.3.4");
            assertThat(copy.port()).isEqualTo(8888);
            assertThat(copy.username()).isEqualTo("u");
        }

        @Test
        void toBuilder_allows_field_override() {
            Proxy original = base().host("old.host").build();
            Proxy updated = original.toBuilder().host("new.host").build();

            assertThat(updated.host()).isEqualTo("new.host");
            assertThat(updated.port()).isEqualTo(original.port());
        }
    }
}
