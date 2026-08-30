BEGIN;

CREATE TABLE IF NOT EXISTS control_telegram_bot_approvals (
  request_id uuid PRIMARY KEY,
  initiating_user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  device_id uuid NOT NULL,
  state_digest char(64) NOT NULL CHECK (state_digest ~ '^[0-9a-f]{64}$'),
  approval_token_digest char(64) NOT NULL UNIQUE CHECK (approval_token_digest ~ '^[0-9a-f]{64}$'),
  code_challenge char(43) NOT NULL CHECK (code_challenge ~ '^[A-Za-z0-9_-]{43}$'),
  redirect_uri text NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'denied', 'consumed')),
  telegram_subject text,
  telegram_username text,
  telegram_display_name text,
  approval_event_id text UNIQUE,
  approved_at timestamptz,
  denied_at timestamptz,
  consumed_at timestamptz,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (device_id, initiating_user_id)
    REFERENCES control_devices(id, user_id) ON DELETE CASCADE,
  CHECK (expires_at > created_at),
  CHECK (
    (status = 'pending' AND telegram_subject IS NULL AND consumed_at IS NULL)
    OR (status = 'approved' AND telegram_subject IS NOT NULL AND approved_at IS NOT NULL AND consumed_at IS NULL)
    OR (status = 'denied' AND denied_at IS NOT NULL AND consumed_at IS NULL)
    OR (status = 'consumed' AND telegram_subject IS NOT NULL AND approved_at IS NOT NULL AND consumed_at IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_owner_idx
  ON control_telegram_bot_approvals(initiating_user_id, device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS control_telegram_bot_approvals_pending_expiry_idx
  ON control_telegram_bot_approvals(expires_at)
  WHERE status = 'pending';

COMMIT;
