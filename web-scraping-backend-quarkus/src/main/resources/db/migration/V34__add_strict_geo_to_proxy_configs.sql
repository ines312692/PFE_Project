ALTER TABLE proxy_configs
    ADD COLUMN strict_geo BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN proxy_configs.strict_geo
    IS 'If FALSE, ProxyPoolService falls back to any country when countries filter returns empty.';