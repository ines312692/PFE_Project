-- ============================================================
-- V39__add_waiting_for_human_status.sql
-- Add WAITING_FOR_HUMAN status for captcha intervention flow
-- ============================================================

ALTER TABLE jobs DROP CONSTRAINT chk_jobs_status;

ALTER TABLE jobs ADD CONSTRAINT chk_jobs_status
    CHECK (status IN (
                      'CREATED',
                      'QUEUED',
                      'RUNNING',
                      'PAUSED',
                      'WAITING_FOR_HUMAN',
                      'COMPLETED',
                      'FAILED',
                      'CANCELLED'
        ));