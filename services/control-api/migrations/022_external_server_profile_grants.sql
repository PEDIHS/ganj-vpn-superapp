BEGIN;

-- Live server candidates may come from a trusted external connection source
-- (for example the Ganj bot/PasarGuard resolver) and therefore do not
-- necessarily have a row in control_servers. The connection-profile route
-- validates and resolves the candidate twice before a grant is persisted.
ALTER TABLE control_connection_profile_grants
  DROP CONSTRAINT IF EXISTS control_connection_profile_grants_server_id_fkey;

COMMENT ON COLUMN control_connection_profile_grants.server_id IS
  'Opaque UUID of the server candidate authorized at issuance time; may originate from control_servers or a trusted external resolver.';

COMMIT;
