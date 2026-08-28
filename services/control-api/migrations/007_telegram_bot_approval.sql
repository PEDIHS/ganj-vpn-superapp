BEGIN;

CREATE TABLE IF NOT EXISTS control_telegram_bot_approvals (
  state_digest char(64) PRIMARY KEY CHECK (state_digest ~ '^[0-9a-f]{64}$'),
  request_digest char(64) NOT NULL UNIQUE CHECK (request_digest ~ '^[0-9a-f]{64}$'),
  code_challenge varchar(43) NOT NULL CHECK (code_challenge ~ '^[A-Za-z0-9_-]{43}$'),
  redirect_uri text NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  telegram_subject text,
  telegram_username text,
  telegram_display_name text,
  expires_at timestamptz NOT NULL,
  approved_at timestamptz,
  cancelled_at timestamptz,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE,
  CHECK (expires_at > created_at),
  CHECK (telegram_subject IS NULL OR telegram_subject ~ '^[1-9][0-9]{0,19}$'),
  CHECK (telegram_username IS NULL OR telegram_username ~ '^[A-Za-z0-9_]{5,32}$'),
  CHECK (NOT (approved_at IS NOT NULL AND cancelled_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_owner_pending_idx
  ON control_telegram_bot_approvals(user_id, device_id, expires_at)
  WHERE consumed_at IS NULL AND cancelled_at IS NULL;

CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_expiry_idx
  ON control_telegram_bot_approvals(expires_at)
  WHERE consumed_at IS NULL;

COMMIT;
