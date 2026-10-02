CREATE TABLE IF NOT EXISTS control_support_messages (
  id uuid PRIMARY KEY,
  ticket_id uuid NOT NULL,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
  sender_role text NOT NULL CHECK (sender_role IN ('user', 'support', 'system')),
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 8000),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(ticket_id, id),
  FOREIGN KEY(ticket_id, user_id) REFERENCES control_support_tickets(id, user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS control_support_messages_thread_idx
  ON control_support_messages(user_id, ticket_id, created_at, id);

-- Existing ticket bodies become the first real user message. Reusing ticket UUID is deterministic
-- and avoids requiring a PostgreSQL UUID extension during migration.
INSERT INTO control_support_messages (id, ticket_id, user_id, sender_role, body, created_at)
SELECT id, id, user_id, 'user', body, created_at
FROM control_support_tickets
ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION ganj_support_seed_initial_message()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO control_support_messages (id, ticket_id, user_id, sender_role, body, created_at)
  VALUES (NEW.id, NEW.id, NEW.user_id, 'user', NEW.body, NEW.created_at)
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS control_support_ticket_initial_message ON control_support_tickets;
CREATE TRIGGER control_support_ticket_initial_message
AFTER INSERT ON control_support_tickets
FOR EACH ROW
EXECUTE FUNCTION ganj_support_seed_initial_message();
