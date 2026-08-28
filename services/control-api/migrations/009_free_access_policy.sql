CREATE TABLE IF NOT EXISTS control_free_access_policy (
  policy_key text PRIMARY KEY CHECK (policy_key = 'default'),
  enabled boolean NOT NULL DEFAULT true,
  maintenance boolean NOT NULL DEFAULT false,
  emergency_disabled boolean NOT NULL DEFAULT false,
  issue_window_seconds integer NOT NULL DEFAULT 3600 CHECK (issue_window_seconds BETWEEN 60 AND 86400),
  max_issues_per_user integer NOT NULL DEFAULT 60 CHECK (max_issues_per_user BETWEEN 1 AND 10000),
  max_issues_per_device integer NOT NULL DEFAULT 30 CHECK (max_issues_per_device BETWEEN 1 AND 10000),
  max_active_grants_per_user integer NOT NULL DEFAULT 4 CHECK (max_active_grants_per_user BETWEEN 1 AND 1000),
  max_active_grants_per_device integer NOT NULL DEFAULT 2 CHECK (max_active_grants_per_device BETWEEN 1 AND 1000),
  max_active_grants_per_server integer NOT NULL DEFAULT 500 CHECK (max_active_grants_per_server BETWEEN 1 AND 100000),
  updated_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO control_free_access_policy (policy_key)
VALUES ('default')
ON CONFLICT (policy_key) DO NOTHING;

CREATE INDEX IF NOT EXISTS control_profile_grants_user_admission_idx
  ON control_connection_profile_grants (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS control_profile_grants_device_admission_idx
  ON control_connection_profile_grants (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS control_profile_grants_server_active_idx
  ON control_connection_profile_grants (server_id, expires_at)
  WHERE consumed_at IS NULL;
