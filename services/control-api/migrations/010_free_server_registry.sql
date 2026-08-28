ALTER TABLE control_servers
  DROP CONSTRAINT IF EXISTS control_servers_status_check;
ALTER TABLE control_servers
  ADD CONSTRAINT control_servers_status_check
  CHECK (status IN ('active', 'busy', 'maintenance', 'disabled'));

ALTER TABLE control_servers
  ADD COLUMN IF NOT EXISTS priority integer NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 100000),
  ADD COLUMN IF NOT EXISTS provider_ref text,
  ADD COLUMN IF NOT EXISTS upstream_ref text,
  ADD COLUMN IF NOT EXISTS max_load_ratio double precision NOT NULL DEFAULT 1 CHECK (max_load_ratio >= 0 AND max_load_ratio <= 1),
  ADD COLUMN IF NOT EXISTS max_active_profile_grants integer CHECK (max_active_profile_grants IS NULL OR max_active_profile_grants BETWEEN 1 AND 100000),
  ADD COLUMN IF NOT EXISTS emergency_disabled boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS rollout_basis_points integer NOT NULL DEFAULT 10000 CHECK (rollout_basis_points BETWEEN 0 AND 10000),
  ADD COLUMN IF NOT EXISTS min_app_version text;

CREATE INDEX IF NOT EXISTS control_servers_free_priority_idx
  ON control_servers (priority, country_code, code)
  WHERE tier = 'free';
