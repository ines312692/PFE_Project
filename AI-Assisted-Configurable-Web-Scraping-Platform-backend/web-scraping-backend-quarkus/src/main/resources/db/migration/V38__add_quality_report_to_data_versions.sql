
ALTER TABLE data_versions
    ADD COLUMN IF NOT EXISTS quality_report JSONB;

-- Common access patterns: "show me the global validity rate of v5" and
-- "list versions with at least one invalid record".
CREATE INDEX IF NOT EXISTS idx_data_versions_quality_validity
    ON data_versions ((quality_report ->> 'validityRate'))
    WHERE quality_report IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_data_versions_quality_invalid
    ON data_versions (((quality_report ->> 'invalidRecords')::int))
    WHERE quality_report IS NOT NULL;
