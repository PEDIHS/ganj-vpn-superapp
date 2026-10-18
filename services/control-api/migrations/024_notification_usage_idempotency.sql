-- Durable, owner-scoped event deduplication; does not write customer balances.
ALTER TABLE control_notifications
  DROP CONSTRAINT IF EXISTS control_notifications_kind_check;
ALTER TABLE control_notifications
  ADD CONSTRAINT control_notifications_kind_check CHECK (kind IN (
    'subscription_usage', 'subscription_expiry',
    'purchase_success', 'payment_failure', 'maintenance',
    'security_update', 'support_reply', 'marketing'
  ));
ALTER TABLE control_notifications
  ADD COLUMN IF NOT EXISTS event_key text;
CREATE UNIQUE INDEX IF NOT EXISTS control_notifications_owner_event_key_unique
  ON control_notifications (user_id, event_key) WHERE event_key IS NOT NULL;
