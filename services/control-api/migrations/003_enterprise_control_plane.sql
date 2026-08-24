CREATE TABLE IF NOT EXISTS control_consent_receipts (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  purpose text NOT NULL CHECK (purpose IN ('essential', 'product_analytics', 'crash_diagnostics', 'personalized_marketing', 'support_diagnostics')),
  status text NOT NULL CHECK (status IN ('granted', 'revoked')),
  policy_version text NOT NULL CHECK (char_length(policy_version) BETWEEN 1 AND 64),
  granted_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(id, user_id, purpose),
  CHECK ((status = 'granted' AND revoked_at IS NULL) OR (status = 'revoked' AND revoked_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS control_consent_receipts_owner_idx
  ON control_consent_receipts(user_id, purpose, created_at DESC);

CREATE TABLE IF NOT EXISTS control_remote_config_releases (
  id uuid PRIMARY KEY,
  environment text NOT NULL CHECK (environment IN ('development', 'testing', 'staging', 'production')),
  version bigint NOT NULL CHECK (version > 0),
  status text NOT NULL CHECK (status IN ('draft', 'published', 'retired')) DEFAULT 'draft',
  reason text NOT NULL CHECK (char_length(reason) BETWEEN 8 AND 500),
  created_by text NOT NULL,
  published_by text,
  content_digest char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz,
  UNIQUE(environment, version)
);
CREATE UNIQUE INDEX IF NOT EXISTS control_remote_config_one_published_idx
  ON control_remote_config_releases(environment) WHERE status = 'published';

CREATE TABLE IF NOT EXISTS control_remote_config_entries (
  release_id uuid NOT NULL REFERENCES control_remote_config_releases(id) ON DELETE CASCADE,
  config_key text NOT NULL CHECK (config_key ~ '^[a-z][a-z0-9_.-]{2,127}$'),
  config_value jsonb NOT NULL,
  sensitivity text NOT NULL CHECK (sensitivity IN ('public', 'internal')),
  target jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(target) = 'object'),
  PRIMARY KEY(release_id, config_key)
);

CREATE TABLE IF NOT EXISTS control_feature_flag_versions (
  id uuid PRIMARY KEY,
  flag_key text NOT NULL CHECK (flag_key ~ '^[a-z][a-z0-9_.-]{2,127}$'),
  version bigint NOT NULL CHECK (version > 0),
  enabled boolean NOT NULL,
  default_variant text NOT NULL CHECK (char_length(default_variant) BETWEEN 1 AND 64),
  rollout_basis_points integer NOT NULL CHECK (rollout_basis_points BETWEEN 0 AND 10000),
  audience jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(audience) = 'object'),
  experiment_key text CHECK (experiment_key IS NULL OR char_length(experiment_key) BETWEEN 1 AND 128),
  reason text NOT NULL CHECK (char_length(reason) BETWEEN 8 AND 500),
  created_by text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(flag_key, version)
);
CREATE INDEX IF NOT EXISTS control_feature_flag_latest_idx
  ON control_feature_flag_versions(flag_key, version DESC);

CREATE TABLE IF NOT EXISTS control_analytics_batches (
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  batch_id uuid NOT NULL,
  consent_receipt_id uuid NOT NULL REFERENCES control_consent_receipts(id),
  consent_purpose text NOT NULL DEFAULT 'product_analytics' CHECK (consent_purpose = 'product_analytics'),
  payload_digest char(64) NOT NULL,
  event_count integer NOT NULL CHECK (event_count BETWEEN 1 AND 100),
  received_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, batch_id),
  FOREIGN KEY(consent_receipt_id, user_id, consent_purpose)
    REFERENCES control_consent_receipts(id, user_id, purpose)
);

CREATE TABLE IF NOT EXISTS control_analytics_events (
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  event_id uuid NOT NULL,
  batch_id uuid NOT NULL,
  event_name text NOT NULL,
  occurred_at timestamptz NOT NULL,
  schema_version integer NOT NULL CHECK (schema_version > 0),
  session_id uuid,
  properties jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(properties) = 'object'),
  received_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, event_id),
  FOREIGN KEY(user_id, batch_id) REFERENCES control_analytics_batches(user_id, batch_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_analytics_events_time_idx
  ON control_analytics_events(event_name, occurred_at DESC);

CREATE TABLE IF NOT EXISTS control_bug_reports (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  client_report_id uuid NOT NULL,
  payload_digest char(64) NOT NULL,
  public_code text NOT NULL UNIQUE,
  title text NOT NULL CHECK (char_length(title) BETWEEN 3 AND 160),
  description text NOT NULL CHECK (char_length(description) BETWEEN 10 AND 5000),
  category text NOT NULL CHECK (category IN ('connection', 'purchase', 'account', 'ui', 'performance', 'security', 'other')),
  severity text NOT NULL CHECK (severity IN ('critical', 'high', 'medium', 'low')),
  status text NOT NULL CHECK (status IN ('new', 'investigating', 'assigned', 'fixing', 'testing', 'released', 'closed')) DEFAULT 'new',
  device_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  context jsonb NOT NULL CHECK (jsonb_typeof(context) = 'object'),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_report_id),
  UNIQUE(id, user_id),
  FOREIGN KEY(device_id, user_id) REFERENCES control_devices(id, user_id)
);
CREATE INDEX IF NOT EXISTS control_bug_reports_owner_idx ON control_bug_reports(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS control_support_tickets (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  client_ticket_id uuid NOT NULL,
  payload_digest char(64) NOT NULL,
  category text NOT NULL CHECK (category IN ('connection', 'billing', 'account', 'security', 'feedback', 'other')),
  public_code text NOT NULL UNIQUE,
  priority text NOT NULL CHECK (priority IN ('urgent', 'high', 'normal')),
  subject text NOT NULL CHECK (char_length(subject) BETWEEN 3 AND 160),
  body text NOT NULL CHECK (char_length(body) BETWEEN 10 AND 5000),
  status text NOT NULL CHECK (status IN ('open', 'waiting_user', 'waiting_support', 'resolved', 'closed')) DEFAULT 'open',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_ticket_id),
  UNIQUE(id, user_id)
);
CREATE INDEX IF NOT EXISTS control_support_tickets_owner_idx ON control_support_tickets(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS control_diagnostic_reports (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  client_report_id uuid NOT NULL,
  payload_digest char(64) NOT NULL,
  device_id uuid NOT NULL,
  bug_report_id uuid,
  support_ticket_id uuid,
  started_at timestamptz NOT NULL,
  finished_at timestamptz NOT NULL,
  tests jsonb NOT NULL CHECK (jsonb_typeof(tests) = 'array' AND jsonb_array_length(tests) BETWEEN 1 AND 50),
  redaction_version text NOT NULL CHECK (char_length(redaction_version) BETWEEN 1 AND 64),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_report_id),
  FOREIGN KEY(device_id, user_id) REFERENCES control_devices(id, user_id),
  FOREIGN KEY(bug_report_id, user_id) REFERENCES control_bug_reports(id, user_id),
  FOREIGN KEY(support_ticket_id, user_id) REFERENCES control_support_tickets(id, user_id),
  CHECK (finished_at >= started_at),
  CHECK (expires_at > created_at)
);
CREATE INDEX IF NOT EXISTS control_diagnostics_expiry_idx ON control_diagnostic_reports(expires_at);

CREATE TABLE IF NOT EXISTS control_admin_audit_log (
  id uuid PRIMARY KEY,
  actor_subject text NOT NULL CHECK (char_length(actor_subject) BETWEEN 1 AND 255),
  action text NOT NULL CHECK (char_length(action) BETWEEN 3 AND 128),
  resource_type text NOT NULL CHECK (char_length(resource_type) BETWEEN 3 AND 128),
  resource_id text NOT NULL CHECK (char_length(resource_id) BETWEEN 1 AND 255),
  reason text NOT NULL CHECK (char_length(reason) BETWEEN 8 AND 500),
  request_id uuid NOT NULL,
  before_digest char(64),
  after_digest char(64),
  outcome text NOT NULL CHECK (outcome IN ('success', 'denied', 'failed')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS control_admin_audit_time_idx ON control_admin_audit_log(created_at DESC);

CREATE OR REPLACE FUNCTION control_reject_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'control_admin_audit_log is append-only';
END;
$$;
DROP TRIGGER IF EXISTS control_admin_audit_immutable ON control_admin_audit_log;
CREATE TRIGGER control_admin_audit_immutable
  BEFORE UPDATE OR DELETE ON control_admin_audit_log
  FOR EACH ROW EXECUTE FUNCTION control_reject_audit_mutation();
