CREATE TABLE IF NOT EXISTS control_phone_push_devices (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL REFERENCES control_devices(id) ON DELETE CASCADE,
  token_sha256 text NOT NULL UNIQUE CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
  token_encrypted bytea NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS control_phone_push_devices_user_device
 ON control_phone_push_devices(user_id, device_id);
