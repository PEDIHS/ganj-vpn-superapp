-- Admin Control Plane server-registry hardening.
-- Keep this migration intentionally narrow so it can coexist with later provider/PasarGuard schema work.

ALTER TABLE control_servers
  DROP CONSTRAINT IF EXISTS control_servers_status_check;

ALTER TABLE control_servers
  ADD CONSTRAINT control_servers_status_check
  CHECK (status IN ('active', 'busy', 'maintenance', 'disabled'));

CREATE INDEX IF NOT EXISTS control_servers_admin_status_idx
  ON control_servers(status, tier, country_code, updated_at DESC);
