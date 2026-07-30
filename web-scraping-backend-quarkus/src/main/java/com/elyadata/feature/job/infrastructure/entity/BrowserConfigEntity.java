package com.elyadata.feature.job.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "browser_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "js_rendering_enabled")
    private Boolean javascriptEnabled;

    @Column(name = "session_enabled")
    private Boolean stealthMode;

    @Column(name = "request_delay_ms")
    private Integer requestDelayMs;

    @Column(name = "request_timeout_ms")
    private Integer timeoutMs;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_login_enabled")
    private Boolean sessionLoginEnabled;

    @Column(name = "session_login_mode", length = 20)
    private String sessionLoginMode;

    @Column(name = "session_login_url", length = 2048)
    private String sessionLoginUrl;

    @Column(name = "session_username_sel", length = 500)
    private String sessionUsernameSel;

    @Column(name = "session_password_sel", length = 500)
    private String sessionPasswordSel;

    @Column(name = "session_submit_sel", length = 500)
    private String sessionSubmitSel;

    @Column(name = "session_success_sel", length = 500)
    private String sessionSuccessSel;

    @Column(name = "session_username_enc", columnDefinition = "TEXT")
    private String sessionUsernameEnc;

    @Column(name = "session_password_enc", columnDefinition = "TEXT")
    private String sessionPasswordEnc;

    @Column(name = "session_imported_state", columnDefinition = "TEXT")
    private String sessionImportedState;
}