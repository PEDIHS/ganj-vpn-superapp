CREATE TABLE IF NOT EXISTS control_users (
  id uuid PRIMARY KEY,
  status text NOT NULL CHECK (status IN ('active', 'blocked', 'deleted')),
  display_name text,
  locale text NOT NULL DEFAULT 'fa-IR',
  telegram_subject text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_devices (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  status text NOT NULL CHECK (status IN ('active', 'revoked')),
  signing_public_jwk jsonb NOT NULL,
  encryption_public_jwk jsonb NOT NULL,
  key_version text NOT NULL,
  attestation_status text NOT NULL DEFAULT 'pending' CHECK (attestation_status IN ('pending', 'trusted', 'rejected')),
  last_seen_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, user_id)
);
CREATE INDEX IF NOT EXISTS control_devices_user_idx ON control_devices(user_id);

CREATE TABLE IF NOT EXISTS control_auth_sessions (
  jti_hash text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_auth_sessions_owner_idx ON control_auth_sessions(user_id, device_id, expires_at);

CREATE TABLE IF NOT EXISTS control_plans (
  id uuid PRIMARY KEY,
  code text NOT NULL UNIQUE,
  name text NOT NULL,
  tier text NOT NULL CHECK (tier IN ('free', 'premium', 'vip')),
  duration_days integer CHECK (duration_days IS NULL OR duration_days > 0),
  traffic_limit_bytes bigint CHECK (traffic_limit_bytes IS NULL OR traffic_limit_bytes >= 0),
  device_limit integer NOT NULL CHECK (device_limit > 0),
  features text[] NOT NULL DEFAULT '{}',
  price_amount_minor bigint NOT NULL CHECK (price_amount_minor >= 0),
  price_currency char(3) NOT NULL,
  channels text[] NOT NULL,
  play_product_id text UNIQUE,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_services (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE RESTRICT,
  plan_id uuid NOT NULL REFERENCES control_plans(id) ON DELETE RESTRICT,
  name text NOT NULL,
  status text NOT NULL CHECK (status IN ('pending', 'active', 'disabled', 'expired', 'revoked')),
  tier text NOT NULL CHECK (tier IN ('free', 'premium', 'vip')),
  country_code char(2),
  traffic_limit_bytes bigint CHECK (traffic_limit_bytes IS NULL OR traffic_limit_bytes >= 0),
  traffic_used_bytes bigint NOT NULL DEFAULT 0 CHECK (traffic_used_bytes >= 0),
  expires_at timestamptz,
  device_limit integer NOT NULL CHECK (device_limit > 0),
  allowed_protocols text[] NOT NULL,
  version bigint NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, user_id)
);
CREATE INDEX IF NOT EXISTS control_services_owner_idx ON control_services(user_id, status, expires_at);

CREATE TABLE IF NOT EXISTS control_service_devices (
  service_id uuid NOT NULL,
  device_id uuid NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  bound_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (service_id, device_id),
  FOREIGN KEY (service_id, user_id) REFERENCES control_services(id, user_id) ON DELETE CASCADE,
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_service_devices_owner_idx ON control_service_devices(user_id, service_id);

CREATE TABLE IF NOT EXISTS control_servers (
  id uuid PRIMARY KEY,
  code text NOT NULL UNIQUE,
  name text NOT NULL,
  country_code char(2) NOT NULL,
  city text,
  tier text NOT NULL CHECK (tier IN ('free', 'premium', 'vip')),
  status text NOT NULL CHECK (status IN ('active', 'busy', 'maintenance')),
  load_ratio double precision NOT NULL CHECK (load_ratio >= 0 AND load_ratio <= 1),
  latency_hint_ms integer CHECK (latency_hint_ms IS NULL OR latency_hint_ms >= 0),
  protocols text[] NOT NULL,
  secret_ref text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS control_servers_catalog_idx ON control_servers(status, tier, country_code);

CREATE TABLE IF NOT EXISTS control_orders (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE RESTRICT,
  plan_id uuid NOT NULL REFERENCES control_plans(id) ON DELETE RESTRICT,
  service_id uuid,
  channel text NOT NULL CHECK (channel IN ('play', 'direct', 'wallet')),
  status text NOT NULL CHECK (status IN ('pending', 'authorized', 'paid', 'fulfilled', 'cancelled', 'refunded', 'failed')),
  total_amount_minor bigint NOT NULL CHECK (total_amount_minor >= 0),
  total_currency char(3) NOT NULL,
  entitlement_service_id uuid,
  external_transaction_id text UNIQUE,
  purchase_token_digest text UNIQUE,
  fulfilled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (service_id, user_id) REFERENCES control_services(id, user_id) ON DELETE RESTRICT,
  FOREIGN KEY (entitlement_service_id, user_id) REFERENCES control_services(id, user_id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS control_orders_owner_idx ON control_orders(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS control_idempotency_keys (
  scope text NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  idempotency_key text NOT NULL,
  request_fingerprint text NOT NULL,
  resource_id uuid NOT NULL,
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '7 days'),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (scope, user_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS control_purchase_token_bindings (
  token_digest text PRIMARY KEY,
  order_id uuid NOT NULL UNIQUE REFERENCES control_orders(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_connection_profile_grants (
  profile_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  service_id uuid NOT NULL,
  device_id uuid NOT NULL,
  server_id uuid NOT NULL REFERENCES control_servers(id) ON DELETE RESTRICT,
  client_nonce_digest text NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (device_id, client_nonce_digest),
  CHECK (expires_at > created_at),
  FOREIGN KEY (service_id, user_id) REFERENCES control_services(id, user_id) ON DELETE CASCADE,
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_profile_grants_expiry_idx ON control_connection_profile_grants(expires_at) WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS control_webhook_events (
  provider text NOT NULL,
  event_id text NOT NULL,
  payload_digest text NOT NULL,
  event_type text,
  processing_status text NOT NULL DEFAULT 'received' CHECK (processing_status IN ('received', 'processed', 'ignored', 'failed')),
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz,
  PRIMARY KEY (provider, event_id)
);

CREATE TABLE IF NOT EXISTS control_telegram_login_states (
  state_digest text PRIMARY KEY,
  code_challenge text NOT NULL,
  redirect_uri text NOT NULL,
  device_id uuid NOT NULL REFERENCES control_devices(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
