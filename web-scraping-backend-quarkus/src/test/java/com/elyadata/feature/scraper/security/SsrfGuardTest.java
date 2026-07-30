package com.elyadata.feature.scraper.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class SsrfGuardTest {

    private SsrfGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SsrfGuard();
        guard.enabled = true;
        guard.allowPrivateNetworks = false;
        guard.blockedPortsCsv = "22,3306,6379";
        guard.dnsCacheTtlSeconds = 300;
    }

    @ParameterizedTest(name = "blocks internal target: {0}")
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://localhost/admin",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/internal",
            "http://192.168.1.1/",
            "http://172.16.4.4/",
            "http://100.64.0.1/",
            "https://[::1]/",
            "https://[fd00::1]/",
            "http://[::ffff:127.0.0.1]/"
    })
    void blocksInternalAddresses(String url) {
        assertThat(guard.isAllowed(url)).isFalse();
    }

    @ParameterizedTest(name = "allows public target: {0}")
    @ValueSource(strings = {
            "http://8.8.8.8/",
            "https://1.1.1.1/",
            "https://[2606:4700:4700::1111]/"
    })
    void allowsPublicAddresses(String url) {
        assertThat(guard.isAllowed(url)).isTrue();
    }

    @Test
    @DisplayName("rejects non-http(s) schemes")
    void rejectsBadSchemes() {
        assertThat(guard.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(guard.isAllowed("ftp://8.8.8.8/")).isFalse();
        assertThat(guard.isAllowed("gopher://8.8.8.8/")).isFalse();
    }

    @Test
    @DisplayName("rejects blocked ports even on public IPs")
    void rejectsBlockedPorts() {
        assertThat(guard.isAllowed("http://8.8.8.8:22/")).isFalse();
        assertThat(guard.isAllowed("http://8.8.8.8:6379/")).isFalse();
        assertThat(guard.isAllowed("http://8.8.8.8:8080/")).isTrue();
    }

    @Test
    @DisplayName("verdict carries a human-readable reason")
    void verdictExposesReason() {
        SsrfGuard.SsrfVerdict verdict = guard.evaluate("http://169.254.169.254/");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.reason()).contains("169.254.169.254");
    }

    @Test
    @DisplayName("assertAllowed throws on a blocked URL")
    void assertAllowedThrows() {
        assertThatThrownBy(() -> guard.assertAllowed("http://127.0.0.1/"))
                .hasMessageContaining("SSRF");
    }

    @Test
    @DisplayName("allow-private-networks lets internal hosts through (staging escape hatch)")
    void allowPrivateBypass() {
        guard.allowPrivateNetworks = true;
        assertThat(guard.isAllowed("http://127.0.0.1/")).isTrue();
        assertThat(guard.isAllowed("http://10.0.0.5/")).isTrue();
        assertThat(guard.isAllowed("file:///etc/passwd")).isFalse();
    }

    @Test
    @DisplayName("disabled guard allows everything")
    void disabledAllowsAll() {
        guard.enabled = false;
        assertThat(guard.isAllowed("http://169.254.169.254/")).isTrue();
        assertThat(guard.isAllowed("file:///etc/passwd")).isTrue();
    }

    @Test
    @DisplayName("malformed / empty input is blocked, never crashes")
    void handlesBadInput() {
        assertThat(guard.isAllowed(null)).isFalse();
        assertThat(guard.isAllowed("")).isFalse();
        assertThat(guard.isAllowed("not a url")).isFalse();
    }
}
