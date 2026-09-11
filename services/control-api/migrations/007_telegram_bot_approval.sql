BEGIN;

CREATE TABLE IF NOT EXISTS control_telegram_bot_approvals (
  approval_digest char(64) PRIMARY KEY CHECK (approval_digest ~ '^[0-9a-f]{64}$'),
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'consuming', 'consumed', 'denied')),
  telegram_subject text,
  telegram_username text,
  telegram_display_name text,
  expires_at timestamptz NOT NULL,
  approved_at timestamptz,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (device_id, user_id) REFERENCES control_devices(id, user_id) ON DELETE CASCADE,
  CHECK (expires_at > created_at),
  CHECK (telegram_subject IS NULL OR telegram_subject ~ '^[1-9][0-9]{0,19}$')
);

CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_owner_idx
  ON control_telegram_bot_approvals(user_id, device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_expiry_idx
  ON control_telegram_bot_approvals(expires_at)
  WHERE status IN ('pending', 'approved', 'consuming');

COMMIT;
