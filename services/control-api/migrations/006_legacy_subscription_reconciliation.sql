CREATE TABLE IF NOT EXISTS control_legacy_sources (
  source_key text PRIMARY KEY,
  source_kind text NOT NULL CHECK (source_kind IN ('telegram_bot')),
  enabled boolean NOT NULL DEFAULT true,
  read_owner text NOT NULL DEFAULT 'legacy' CHECK (read_owner IN ('legacy', 'control_api')),
  checkpoint_cursor text,
  last_started_at timestamptz,
  last_completed_at timestamptz,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_legacy_customer_mappings (
  source_key text NOT NULL REFERENCES control_legacy_sources(source_key) ON DELETE CASCADE,
  external_customer_id text NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE RESTRICT,
  mapping_basis text NOT NULL CHECK (mapping_basis IN ('telegram_subject', 'admin_verified')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_key, external_customer_id),
  UNIQUE (source_key, user_id)
);
CREATE INDEX IF NOT EXISTS control_legacy_customer_user_idx
  ON control_legacy_customer_mappings(user_id, source_key);

CREATE TABLE IF NOT EXISTS control_legacy_service_projections (
  source_key text NOT NULL REFERENCES control_legacy_sources(source_key) ON DELETE CASCADE,
  external_service_id text NOT NULL,
  external_customer_id text NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE RESTRICT,
  control_service_id uuid NOT NULL,
  source_fingerprint text NOT NULL,
  source_updated_at timestamptz,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  last_applied_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_key, external_service_id),
  UNIQUE (control_service_id),
  FOREIGN KEY (source_key, external_customer_id)
    REFERENCES control_legacy_customer_mappings(source_key, external_customer_id) ON DELETE RESTRICT,
  FOREIGN KEY (control_service_id, user_id)
    REFERENCES control_services(id, user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_legacy_service_owner_idx
  ON control_legacy_service_projections(user_id, source_key, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS control_legacy_reconciliation_conflicts (
  id uuid PRIMARY KEY,
  source_key text NOT NULL REFERENCES control_legacy_sources(source_key) ON DELETE CASCADE,
  entity_type text NOT NULL CHECK (entity_type IN ('customer', 'service', 'plan')),
  external_entity_id text NOT NULL,
  conflict_code text NOT NULL,
  detail jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved', 'ignored')),
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz,
  resolved_by text,
  resolution_note text,
  UNIQUE (source_key, entity_type, external_entity_id, conflict_code, status)
);
CREATE INDEX IF NOT EXISTS control_legacy_conflicts_open_idx
  ON control_legacy_reconciliation_conflicts(source_key, last_seen_at DESC)
  WHERE status = 'open';

CREATE TABLE IF NOT EXISTS control_legacy_reconciliation_events (
  source_key text NOT NULL REFERENCES control_legacy_sources(source_key) ON DELETE CASCADE,
  event_id text NOT NULL,
  payload_fingerprint text NOT NULL,
  processing_status text NOT NULL CHECK (processing_status IN ('received', 'applied', 'unchanged', 'conflict', 'failed')),
  external_service_id text,
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz,
  error_code text,
  PRIMARY KEY (source_key, event_id)
);
CREATE INDEX IF NOT EXISTS control_legacy_events_status_idx
  ON control_legacy_reconciliation_events(source_key, processing_status, received_at DESC);

-- Legacy reconciliation stores normalized entitlement metadata only. Raw VPN URI/JSON,
-- credentials, subscription URLs and secrets are intentionally not represented in this schema.
