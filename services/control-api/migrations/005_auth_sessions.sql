BEGIN;

ALTER TABLE control_auth_sessions
  ADD COLUMN IF NOT EXISTS session_id uuid,
  ADD COLUMN IF NOT EXISTS refresh_family_id uuid,
  ADD COLUMN IF NOT EXISTS auth_method text;

CREATE UNIQUE INDEX IF NOT EXISTS control_auth_sessions_session_idx
  ON control_auth_sessions(session_id)
  WHERE session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS control_auth_sessions_family_idx
  ON control_auth_sessions(refresh_family_id)
  WHERE refresh_family_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS control_refresh_tokens (
  token_hash char(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  session_id uuid NOT NULL,
  family_id uuid NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  parent_token_hash char(64),
  replacement_token_hash char(64),
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE,
  CHECK (expires_at > created_at),
  CHECK (parent_token_hash IS NULL OR parent_token_hash <> token_hash),
  CHECK (replacement_token_hash IS NULL OR replacement_token_hash <> token_hash)
);

CREATE INDEX IF NOT EXISTS control_refresh_tokens_session_idx
  ON control_refresh_tokens(session_id);
CREATE INDEX IF NOT EXISTS control_refresh_tokens_family_idx
  ON control_refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS control_refresh_tokens_expiry_idx
  ON control_refresh_tokens(expires_at)
  WHERE consumed_at IS NULL AND revoked_at IS NULL;

ALTER TABLE control_telegram_login_states
  ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES control_users(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS oidc_nonce_digest char(64);

-- Login states are deliberately short-lived. States created by a pre-nonce build
-- cannot be safely upgraded, so invalidate them during the auth migration.
DELETE FROM control_telegram_login_states
 WHERE user_id IS NULL OR oidc_nonce_digest IS NULL;

ALTER TABLE control_telegram_login_states
  ALTER COLUMN user_id SET NOT NULL,
  ALTER COLUMN oidc_nonce_digest SET NOT NULL;

CREATE INDEX IF NOT EXISTS control_telegram_login_states_owner_pending_idx
  ON control_telegram_login_states(user_id, device_id, expires_at)
  WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS control_auth_proof_nonces (
  nonce_digest char(64) PRIMARY KEY CHECK (nonce_digest ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS control_auth_proof_nonces_expiry_idx
  ON control_auth_proof_nonces(expires_at);

COMMIT;
