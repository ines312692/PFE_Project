CREATE TABLE llm_calls
(
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    provider          VARCHAR(40)   NOT NULL,
    model             VARCHAR(120)  NOT NULL,
    feature           VARCHAR(60)   NOT NULL,
    outcome           VARCHAR(20)   NOT NULL,

    job_id            UUID,
    project_id        UUID,
    user_id           UUID,

    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    total_tokens      INTEGER,
    prompt_chars      INTEGER       NOT NULL DEFAULT 0,
    response_chars    INTEGER       NOT NULL DEFAULT 0,

    latency_ms        INTEGER       NOT NULL DEFAULT 0,
    estimated_cost    NUMERIC(12, 6) NOT NULL DEFAULT 0,

    error_message     TEXT
);

CREATE INDEX idx_llm_calls_created_at ON llm_calls (created_at);
CREATE INDEX idx_llm_calls_project    ON llm_calls (project_id, created_at);
CREATE INDEX idx_llm_calls_job        ON llm_calls (job_id);
CREATE INDEX idx_llm_calls_feature    ON llm_calls (feature, created_at);
CREATE INDEX idx_llm_calls_model      ON llm_calls (model, created_at);
