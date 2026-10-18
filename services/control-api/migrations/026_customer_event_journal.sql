-- Sequential, per-user replay cursor. Published from the SAME PG transaction
-- that records an authenticated bot notification.
CREATE TABLE IF NOT EXISTS control_customer_event_journal (
  event_seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  notification_id uuid NOT NULL UNIQUE REFERENCES control_notifications(id) ON DELETE CASCADE,
  event_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, event_key)
);
CREATE INDEX IF NOT EXISTS control_customer_event_owner_sequence_idx
  ON control_customer_event_journal(user_id, event_seq);
