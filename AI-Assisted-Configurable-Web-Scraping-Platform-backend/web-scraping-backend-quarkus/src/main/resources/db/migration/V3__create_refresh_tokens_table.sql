CREATE TABLE refresh_tokens
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    token      VARCHAR(255) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens (token)   WHERE revoked = FALSE;
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);