-- A member keeps their Telegram account and wallet; only service access is shared.
CREATE TABLE control_service_members (
 service_id uuid NOT NULL REFERENCES control_services(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES control_users(id) ON DELETE CASCADE,
 joined_at timestamptz NOT NULL DEFAULT now(),
 revoked_at timestamptz,
 PRIMARY KEY(service_id,user_id)
);
CREATE INDEX control_service_members_user_idx ON control_service_members(user_id,service_id) WHERE revoked_at IS NULL;
CREATE TABLE control_service_invites (
 id uuid PRIMARY KEY,
 service_id uuid NOT NULL REFERENCES control_services(id) ON DELETE CASCADE,
 token_hash char(64) NOT NULL UNIQUE,
 expires_at timestamptz NOT NULL,
 consumed_by uuid REFERENCES control_users(id),
 consumed_at timestamptz,
 revoked_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX control_service_invites_service_idx ON control_service_invites(service_id,expires_at);
-- The device/user composite FK still enforces the actual device owner.
-- Service access is separately checked transactionally against owner or active membership.
ALTER TABLE control_service_devices DROP CONSTRAINT control_service_devices_service_id_user_id_fkey;
ALTER TABLE control_service_devices ADD CONSTRAINT service_device_service_fk FOREIGN KEY(service_id) REFERENCES control_services(id) ON DELETE CASCADE;
ALTER TABLE control_connection_profile_grants DROP CONSTRAINT control_connection_profile_grants_service_id_user_id_fkey;
ALTER TABLE control_connection_profile_grants ADD CONSTRAINT profile_grant_service_fk FOREIGN KEY(service_id) REFERENCES control_services(id) ON DELETE CASCADE;
ALTER TABLE control_devices ADD COLUMN name text CHECK(name IS NULL OR length(name)<=120);
ALTER TABLE control_devices ADD COLUMN app_version text CHECK(app_version IS NULL OR length(app_version)<=64);
ALTER TABLE control_devices ADD COLUMN os_version text CHECK(os_version IS NULL OR length(os_version)<=64);
