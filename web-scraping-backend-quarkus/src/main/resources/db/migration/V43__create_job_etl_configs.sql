CREATE TABLE job_etl_configs
(
    job_id     UUID PRIMARY KEY REFERENCES jobs (id) ON DELETE CASCADE,
    steps_json TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);