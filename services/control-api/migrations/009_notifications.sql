CREATE TABLE IF NOT EXISTS control_notifications (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  kind text NOT NULL CHECK (kind IN (
    'subscription_expiry',
    'purchase_success',
    'payment_failure',
    'maintenance',
    'security_update',
    'support_reply',
    'marketing'
  )),
  title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160),
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
  action jsonb,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (action IS NULL OR jsonb_typeof(action) = 'object')
);
CREATE INDEX IF NOT EXISTS control_notifications_owner_created_idx
  ON control_notifications(user_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS control_notifications_owner_unread_idx
  ON control_notifications(user_id, created_at DESC)
  WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS control_notification_preferences (
  user_id uuid PRIMARY KEY REFERENCES control_users(id) ON DELETE CASCADE,
  subscription_expiry boolean NOT NULL DEFAULT true,
  purchase_success boolean NOT NULL DEFAULT true,
  payment_failure boolean NOT NULL DEFAULT true,
  maintenance boolean NOT NULL DEFAULT true,
  security_update boolean NOT NULL DEFAULT true,
  support_reply boolean NOT NULL DEFAULT true,
  marketing boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO control_notification_preferences (user_id)
SELECT id FROM control_users
ON CONFLICT (user_id) DO NOTHING;

CREATE OR REPLACE FUNCTION ganj_create_notification_preferences_for_user()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO control_notification_preferences (user_id)
  VALUES (NEW.id)
  ON CONFLICT (user_id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS control_users_notification_preferences_insert ON control_users;
CREATE TRIGGER control_users_notification_preferences_insert
AFTER INSERT ON control_users
FOR EACH ROW
EXECUTE FUNCTION ganj_create_notification_preferences_for_user();
