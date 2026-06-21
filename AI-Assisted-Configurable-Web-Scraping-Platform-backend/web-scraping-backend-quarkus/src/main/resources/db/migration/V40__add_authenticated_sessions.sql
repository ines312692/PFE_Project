-- F-3.5 — Authenticated sessions & cookies
-- 1. Persisted browser sessions (storageState), one live row per domain, encrypted at rest.
CREATE TABLE browser_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain              VARCHAR(255) NOT NULL UNIQUE,
    storage_state_enc   TEXT         NOT NULL,
    captured_at         TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_browser_sessions_domain ON browser_sessions (domain);

-- 2. Per-job session/login configuration, attached to browser_configs.
--    Credentials are AES-GCM ciphertext (never plaintext).
ALTER TABLE browser_configs
    ADD COLUMN session_login_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN session_login_mode     VARCHAR(20),
    ADD COLUMN session_login_url      VARCHAR(2048),
    ADD COLUMN session_username_sel   VARCHAR(500),
    ADD COLUMN session_password_sel   VARCHAR(500),
    ADD COLUMN session_submit_sel     VARCHAR(500),
    ADD COLUMN session_success_sel    VARCHAR(500),
    ADD COLUMN session_username_enc   TEXT,
    ADD COLUMN session_password_enc   TEXT,
    ADD COLUMN session_imported_state TEXT;
