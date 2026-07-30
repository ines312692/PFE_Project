-- ═══════════════════════════════════════════════════════
-- V37: Create audit_logs table (admin audit trail)
-- ═══════════════════════════════════════════════════════

CREATE TABLE audit_logs
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    occurred_at     TIMESTAMP   NOT NULL DEFAULT now(),

    user_id         UUID,
    username        VARCHAR(150),
    user_role       VARCHAR(30),

    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50) NOT NULL,
    resource_id     VARCHAR(100),

    http_method     VARCHAR(10),
    endpoint        VARCHAR(500),
    http_status     INTEGER,
    outcome         VARCHAR(20) NOT NULL,

    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500),
    correlation_id  VARCHAR(100),

    request_summary TEXT,
    error_message   VARCHAR(1000)
);

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_logs_outcome ON audit_logs (outcome, occurred_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action, occurred_at DESC);

COMMENT
ON TABLE  audit_logs IS 'Admin audit trail: all write operations (POST/PUT/PATCH/DELETE) + auth events';
COMMENT
ON COLUMN audit_logs.request_summary IS 'Truncated and masked request body (4KB max, sensitive fields replaced with ***)';
COMMENT
ON COLUMN audit_logs.correlation_id  IS 'Correlates with HttpLogInterceptor MDC for log tracing';
