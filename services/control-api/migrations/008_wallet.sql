CREATE TABLE IF NOT EXISTS control_wallets (
  user_id uuid PRIMARY KEY REFERENCES control_users(id) ON DELETE RESTRICT,
  currency char(3) NOT NULL DEFAULT 'IRR' CHECK (currency ~ '^[A-Z]{3}$'),
  balance_amount_minor bigint NOT NULL DEFAULT 0 CHECK (balance_amount_minor >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS control_wallet_ledger (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE RESTRICT,
  entry_type text NOT NULL CHECK (entry_type IN ('topup', 'purchase', 'refund', 'adjustment', 'reversal')),
  direction text NOT NULL CHECK (direction IN ('credit', 'debit')),
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  balance_after_minor bigint NOT NULL CHECK (balance_after_minor >= 0),
  reference_type text,
  reference_id text,
  description text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (reference_type IS NULL OR char_length(reference_type) BETWEEN 1 AND 64),
  CHECK (reference_id IS NULL OR char_length(reference_id) BETWEEN 1 AND 200),
  CHECK (description IS NULL OR char_length(description) <= 500)
);

CREATE INDEX IF NOT EXISTS control_wallet_ledger_owner_idx
  ON control_wallet_ledger(user_id, created_at DESC, id DESC);

INSERT INTO control_wallets(user_id)
SELECT id FROM control_users
ON CONFLICT (user_id) DO NOTHING;

CREATE OR REPLACE FUNCTION control_ensure_wallet_for_user()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO control_wallets(user_id) VALUES (NEW.id)
  ON CONFLICT (user_id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS control_users_wallet_trigger ON control_users;
CREATE TRIGGER control_users_wallet_trigger
AFTER INSERT ON control_users
FOR EACH ROW EXECUTE FUNCTION control_ensure_wallet_for_user();
