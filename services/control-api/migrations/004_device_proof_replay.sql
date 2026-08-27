BEGIN;

CREATE TABLE IF NOT EXISTS control_device_proof_nonces (
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  key_version text NOT NULL CHECK (key_version ~ '^[A-Za-z0-9_-]{1,64}$'),
  nonce_digest char(64) NOT NULL CHECK (nonce_digest ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (device_id, key_version, nonce_digest),
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE,
  CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS control_device_proof_nonces_expiry_idx
  ON control_device_proof_nonces(expires_at);

COMMIT;
