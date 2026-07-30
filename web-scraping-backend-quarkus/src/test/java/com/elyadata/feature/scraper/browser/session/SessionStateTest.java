package com.elyadata.feature.scraper.browser.session;

import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SessionState")
class SessionStateTest {

    @Test
    @DisplayName("isEmpty() → true when storageState is null")
    void isEmpty_nullStorage() {
        SessionState s = SessionState.builder().domain("a.com").storageState(null).build();
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() → true when storageState is blank")
    void isEmpty_blankStorage() {
        SessionState s = SessionState.builder().domain("a.com").storageState("   ").build();
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() → true when storageState is empty string")
    void isEmpty_emptyStorage() {
        SessionState s = SessionState.builder().domain("a.com").storageState("").build();
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() → false when storageState has content")
    void isEmpty_withContent() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}").build();
        assertThat(s.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("isExpired() → false when expiresAt is null (never expires)")
    void isExpired_nullExpiresAt_neverExpires() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}").expiresAt(null).build();
        assertThat(s.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired() → false when expiresAt is in the future")
    void isExpired_futureExpiresAt() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        assertThat(s.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired() → true when expiresAt is in the past")
    void isExpired_pastExpiresAt() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}")
                .expiresAt(Instant.now().minusSeconds(1)).build();
        assertThat(s.isExpired()).isTrue();
    }

    @Test
    @DisplayName("isUsable() → false when storage is empty")
    void isUsable_emptyStorage_false() {
        SessionState s = SessionState.builder().domain("a.com").storageState(null).build();
        assertThat(s.isUsable()).isFalse();
    }

    @Test
    @DisplayName("isUsable() → false when session is expired")
    void isUsable_expired_false() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}")
                .expiresAt(Instant.now().minusSeconds(10)).build();
        assertThat(s.isUsable()).isFalse();
    }

    @Test
    @DisplayName("isUsable() → true when storage has content and not expired")
    void isUsable_validSession_true() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        assertThat(s.isUsable()).isTrue();
    }

    @Test
    @DisplayName("isUsable() → true when expiresAt is null (no expiry) and storage is set")
    void isUsable_noExpiry_true() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState("{\"cookies\":[]}").expiresAt(null).build();
        assertThat(s.isUsable()).isTrue();
    }

    @Test
    @DisplayName("isUsable() → false when both empty and expired")
    void isUsable_emptyAndExpired_false() {
        SessionState s = SessionState.builder()
                .domain("a.com").storageState(null)
                .expiresAt(Instant.now().minusSeconds(5)).build();
        assertThat(s.isUsable()).isFalse();
    }
}