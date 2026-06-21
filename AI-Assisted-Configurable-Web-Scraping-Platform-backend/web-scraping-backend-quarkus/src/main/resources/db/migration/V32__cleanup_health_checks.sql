ALTER TABLE proxy_health_checks DROP COLUMN IF EXISTS cloudflare_detected;
ALTER TABLE proxy_health_checks DROP COLUMN IF EXISTS cloudflare_blocked;
ALTER TABLE proxy_health_checks DROP COLUMN IF EXISTS check_type;