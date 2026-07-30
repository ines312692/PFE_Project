-- V4__create_extraction_schemas_table.sql

CREATE TABLE extraction_schemas (
                                    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                    job_id            UUID        NOT NULL UNIQUE,
                                    fields            JSONB,
                                    llm_prompt        TEXT,
                                    llm_model         VARCHAR(50),
                                    llm_output_format TEXT,
                                    regex_pattern     TEXT,
                                    regex_group       VARCHAR(100),
                                    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                    CONSTRAINT fk_extraction_schema_job
                                        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_extraction_schemas_job_id ON extraction_schemas(job_id);