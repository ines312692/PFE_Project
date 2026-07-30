-- ═══════════════════════════════════════════════════════
-- V8: Extract browser_configs and retry_policies tables
-- ═══════════════════════════════════════════════════════

-- 1. Create browser_configs table
CREATE TABLE browser_configs (
                                 id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 job_id          UUID NOT NULL UNIQUE,
                                 js_rendering_enabled   BOOLEAN     DEFAULT true,
                                 session_enabled        BOOLEAN     DEFAULT false,
                                 request_delay_ms       INTEGER     DEFAULT 1500,
                                 request_timeout_ms     INTEGER     DEFAULT 30000,
                                 user_agent             VARCHAR(500),

                                 CONSTRAINT fk_browser_configs_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- 2. Migrate existing embedded data into browser_configs
INSERT INTO browser_configs (job_id, js_rendering_enabled, session_enabled, request_delay_ms, request_timeout_ms, user_agent)
SELECT id,
       COALESCE(js_rendering_enabled, true),
       COALESCE(session_enabled, false),
       COALESCE(request_delay_ms, 1500),
       COALESCE(request_timeout_ms, 30000),
       user_agent
FROM jobs;

-- 3. Create retry_policies table
CREATE TABLE retry_policies (
                                id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                job_id                         UUID NOT NULL UNIQUE,
                                max_retries                    INTEGER  DEFAULT 3,
                                retry_initial_delay_ms         BIGINT   DEFAULT 1000,
                                retry_backoff_multiplier       DOUBLE PRECISION DEFAULT 2.0,
                                retry_max_delay_ms             BIGINT   DEFAULT 30000,
                                circuit_breaker_enabled        BOOLEAN  DEFAULT false,
                                circuit_breaker_threshold      DOUBLE PRECISION DEFAULT 0.5,
                                circuit_breaker_reset_seconds  INTEGER  DEFAULT 60,

                                CONSTRAINT fk_retry_policies_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- 4. Migrate existing embedded data into retry_policies
INSERT INTO retry_policies (job_id, max_retries, retry_initial_delay_ms, retry_backoff_multiplier,
                            retry_max_delay_ms, circuit_breaker_enabled, circuit_breaker_threshold,
                            circuit_breaker_reset_seconds)
SELECT id,
       COALESCE(max_retries, 3),
       COALESCE(retry_initial_delay_ms, 1000),
       COALESCE(retry_backoff_multiplier, 2.0),
       COALESCE(retry_max_delay_ms, 30000),
       COALESCE(circuit_breaker_enabled, false),
       COALESCE(circuit_breaker_threshold, 0.5),
       COALESCE(circuit_breaker_reset_seconds, 60)
FROM jobs;

-- 5. Drop old embedded columns from jobs
ALTER TABLE jobs DROP COLUMN IF EXISTS js_rendering_enabled;
ALTER TABLE jobs DROP COLUMN IF EXISTS session_enabled;
ALTER TABLE jobs DROP COLUMN IF EXISTS request_delay_ms;
ALTER TABLE jobs DROP COLUMN IF EXISTS request_timeout_ms;
ALTER TABLE jobs DROP COLUMN IF EXISTS user_agent;
ALTER TABLE jobs DROP COLUMN IF EXISTS max_retries;
ALTER TABLE jobs DROP COLUMN IF EXISTS retry_initial_delay_ms;
ALTER TABLE jobs DROP COLUMN IF EXISTS retry_backoff_multiplier;
ALTER TABLE jobs DROP COLUMN IF EXISTS retry_max_delay_ms;
ALTER TABLE jobs DROP COLUMN IF EXISTS circuit_breaker_enabled;
ALTER TABLE jobs DROP COLUMN IF EXISTS circuit_breaker_threshold;
ALTER TABLE jobs DROP COLUMN IF EXISTS circuit_breaker_reset_seconds;

-- 6. Indexes
CREATE INDEX idx_browser_configs_job_id ON browser_configs(job_id);
CREATE INDEX idx_retry_policies_job_id ON retry_policies(job_id);