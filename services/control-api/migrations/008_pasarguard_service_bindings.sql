BEGIN;

CREATE TABLE IF NOT EXISTS control_service_upstream_bindings (
  service_id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  provider_type text NOT NULL CHECK (provider_type IN ('pasarguard')),
  source_key text NOT NULL,
  external_service_id text NOT NULL,
  external_service_username text NOT NULL,
  connector_ref text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (service_id, user_id) REFERENCES control_services(id, user_id) ON DELETE CASCADE,
  CHECK (source_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
  CHECK (external_service_id ~ '^[A-Za-z0-9._:@-]{1,256}$'),
  CHECK (external_service_username ~ '^[A-Za-z0-9_.@-]{1,128}$'),
  CHECK (connector_ref ~ '^[A-Za-z0-9._:-]{1,128}$'),
  UNIQUE (source_key, external_service_id)
);

CREATE INDEX IF NOT EXISTS control_service_upstream_owner_idx
  ON control_service_upstream_bindings(user_id, provider_type, service_id);

COMMENT ON TABLE control_service_upstream_bindings IS
  'Ownership/runtime locator only. Raw VPN configuration, subscription URLs and provider credentials are forbidden.';

COMMIT;
