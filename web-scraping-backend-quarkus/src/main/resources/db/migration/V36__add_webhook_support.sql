-- ============================================================================
-- V36 — Webhook trigger support (idempotent)
-- ============================================================================
-- Adds webhook credentials to jobs and creates an audit table for every
-- inbound webhook call. Uses IF NOT EXISTS everywhere so re-runs are safe.
-- ============================================================================

-- 1) Columns on jobs ----------------------------------------------------------
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS webhook_token   VARCHAR(64);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS webhook_secret  VARCHAR(128);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Unique partial index — only one job per non-null token
CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_webhook_token
    ON jobs (webhook_token)
    WHERE webhook_token IS NOT NULL;

-- 2) Webhook deliveries audit table ------------------------------------------
CREATE TABLE IF NOT EXISTS webhook_deliveries (
                                                  id              UUID PRIMARY KEY,
                                                  job_id          UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    received_at     TIMESTAMPTZ NOT NULL,
    status          VARCHAR(32) NOT NULL,
    http_status     INT NOT NULL,
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(512),
    signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    error_message   VARCHAR(512),
    payload         TEXT,
    duration_ms     INT
    );

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_job_received
    ON webhook_deliveries (job_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status
    ON webhook_deliveries (status);

-- 3) Backfill: existing WEBHOOK-triggered jobs get a token --------------------
-- Only fills jobs that don't yet have a token (idempotent on re-run)
UPDATE jobs
SET webhook_enabled = TRUE,
    webhook_token   = REPLACE(gen_random_uuid()::text, '-', '')
WHERE trigger_type = 'WEBHOOK'
  AND webhook_token IS NULL;