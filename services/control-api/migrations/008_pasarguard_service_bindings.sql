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

-- Static Free/control-plane servers continue using server_id. Paid PasarGuard subscription nodes
-- are intentionally virtual and never materialized into control_servers. They use an opaque node
-- digest plus a deterministic UUID exposed to Android. Both paths share the same one-time grant
-- table so nonce replay and consume semantics remain identical.
ALTER TABLE control_connection_profile_grants
  ALTER COLUMN server_id DROP NOT NULL,
  ADD COLUMN IF NOT EXISTS upstream_node_id char(64),
  ADD COLUMN IF NOT EXISTS upstream_virtual_server_id uuid;

ALTER TABLE control_connection_profile_grants
  DROP CONSTRAINT IF EXISTS control_connection_profile_grants_source_check;

ALTER TABLE control_connection_profile_grants
  ADD CONSTRAINT control_connection_profile_grants_source_check CHECK (
    (server_id IS NOT NULL AND upstream_node_id IS NULL AND upstream_virtual_server_id IS NULL)
    OR
    (server_id IS NULL AND upstream_node_id ~ '^[0-9a-f]{64}$' AND upstream_virtual_server_id IS NOT NULL)
  );

CREATE INDEX IF NOT EXISTS control_profile_grants_upstream_idx
  ON control_connection_profile_grants(service_id, upstream_virtual_server_id)
  WHERE upstream_node_id IS NOT NULL;

COMMIT;
