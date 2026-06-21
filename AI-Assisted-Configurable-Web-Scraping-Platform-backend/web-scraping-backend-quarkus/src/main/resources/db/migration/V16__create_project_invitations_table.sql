CREATE TABLE project_invitations (
                                     id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     project_id    UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                     invited_by    UUID        NOT NULL REFERENCES users(id),
                                     invited_user  UUID        NOT NULL REFERENCES users(id),
                                     role          VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
                                     status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                                     created_at    TIMESTAMP   NOT NULL DEFAULT now(),
                                     responded_at  TIMESTAMP,
                                     expires_at    TIMESTAMP   NOT NULL DEFAULT now() + INTERVAL '7 days',

                                     CONSTRAINT uq_pending_invitation
                                         UNIQUE (project_id, invited_user, status)
);

CREATE INDEX idx_invitations_invited_user ON project_invitations(invited_user);
CREATE INDEX idx_invitations_project      ON project_invitations(project_id);