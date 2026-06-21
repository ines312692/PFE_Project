-- ═══════════════════════════════════════════════════════
-- V9: Create notifications table
-- ═══════════════════════════════════════════════════════

CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    job_id        UUID,
    type          VARCHAR(30)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    message       VARCHAR(500),
    read          BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_job  FOREIGN KEY (job_id)  REFERENCES jobs(id)  ON DELETE SET NULL
);

CREATE INDEX idx_notifications_user_id    ON notifications(user_id);
CREATE INDEX idx_notifications_user_read  ON notifications(user_id, read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
