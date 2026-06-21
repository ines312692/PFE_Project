-- ============================================================
-- V35__create_data_versions_table.sql
-- Immutable, versioned snapshots of cleaned records produced by the ETL pipeline.
--
-- scrape_results.records stays the UNTOUCHED raw source — it is version 0
-- (virtual, never stored here). Every apply-and-save appends one row below;
-- nothing is ever overwritten or deleted, which gives full history + rollback.
-- ============================================================

CREATE TABLE data_versions
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    scrape_result_id  UUID        NOT NULL REFERENCES scrape_results (id) ON DELETE CASCADE,
    version_number    INT         NOT NULL,
    parent_version_id UUID        REFERENCES data_versions (id) ON DELETE SET NULL,
    records           JSONB       NOT NULL DEFAULT '[]',
    applied_steps     JSONB       NOT NULL DEFAULT '[]',
    record_count      INT         NOT NULL DEFAULT 0,
    label             VARCHAR(255),
    created_by        UUID REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_data_versions_number
        UNIQUE (scrape_result_id, version_number),
    CONSTRAINT chk_data_versions_number
        CHECK (version_number >= 1)
);

CREATE INDEX idx_data_versions_scrape_result ON data_versions (scrape_result_id);
CREATE INDEX idx_data_versions_created_at ON data_versions (created_at DESC);
CREATE INDEX idx_data_versions_parent ON data_versions(parent_version_id);
COMMENT
ON TABLE data_versions IS
    'Versioned snapshots of cleaned records. Each apply-and-save creates one row; '
    'the raw scrape result (scrape_results.records) is never mutated.';