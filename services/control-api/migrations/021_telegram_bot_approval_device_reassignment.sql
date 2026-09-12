BEGIN;

ALTER TABLE control_telegram_bot_approvals
  DROP CONSTRAINT IF EXISTS control_telegram_bot_approval_device_id_initiating_user_id_fkey;

ALTER TABLE control_telegram_bot_approvals
  ADD CONSTRAINT control_telegram_bot_approval_device_id_initiating_user_id_fkey
  FOREIGN KEY (device_id, initiating_user_id)
  REFERENCES control_devices(id, user_id)
  ON UPDATE CASCADE
  ON DELETE CASCADE;

COMMIT;
