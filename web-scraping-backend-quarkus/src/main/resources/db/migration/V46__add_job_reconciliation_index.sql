

CREATE INDEX IF NOT EXISTS idx_jobs_reconciliation
    ON jobs (status, updated_at)
    WHERE deleted = false;
