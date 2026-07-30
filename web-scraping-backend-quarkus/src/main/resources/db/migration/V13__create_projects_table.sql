CREATE TABLE projects (
                          id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          name        VARCHAR(120)  NOT NULL,
                          description TEXT,
                          color       CHAR(7),
                          status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
                          owner_id    UUID          NOT NULL REFERENCES users(id),
                          created_by  UUID          NOT NULL REFERENCES users(id),
                          updated_by  UUID          REFERENCES users(id),
                          created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                          deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_projects_owner_id  ON projects(owner_id)  WHERE deleted = FALSE;
CREATE INDEX idx_projects_status    ON projects(status)     WHERE deleted = FALSE;
