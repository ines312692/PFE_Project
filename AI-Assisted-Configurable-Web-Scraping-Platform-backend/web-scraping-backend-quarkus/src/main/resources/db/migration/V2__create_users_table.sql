CREATE TABLE users
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'READER',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'OPERATOR', 'READER'))
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email    ON users (email);
CREATE INDEX idx_users_role     ON users (role);

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_updated_by
        FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;