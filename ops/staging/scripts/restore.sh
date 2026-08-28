#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

backup=${1:-}
identity=${AGE_IDENTITY_FILE:-}

[ -n "$backup" ] || { echo 'usage: RESTORE_CONFIRM=staging AGE_IDENTITY_FILE=/path/key restore.sh backups/file.dump.age' >&2; exit 1; }
[ "${RESTORE_CONFIRM:-}" = 'staging' ] || { echo 'Set RESTORE_CONFIRM=staging to authorize destructive restore.' >&2; exit 1; }
[ -f "$backup" ] || { echo 'backup file not found' >&2; exit 1; }
[ -n "$identity" ] && [ -f "$identity" ] || { echo 'AGE_IDENTITY_FILE must point to the private age identity' >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo 'docker is required' >&2; exit 1; }
command -v age >/dev/null 2>&1 || { echo 'age is required' >&2; exit 1; }

checksum="${backup}.sha256"
if [ -f "$checksum" ]; then sha256sum -c "$checksum"; fi

# Restore into an isolated database while the live API continues to serve ganj.
age -d -i "$identity" "$backup" \
  | docker compose -f compose.yml exec -T postgres sh -ec '
      export PGPASSWORD="$(cat /run/secrets/postgres_password)"
      dropdb --if-exists -U ganj ganj_restore
      createdb -U ganj ganj_restore
      pg_restore -U ganj -d ganj_restore --no-owner --no-privileges --exit-on-error
    '

# Prove the restored database can accept the exact current immutable migration set before any swap.
MIGRATION_DATABASE_NAME=ganj_restore docker compose -f compose.yml run --rm migrate

# Fail closed unless the restored database contains the minimum state required for accounts,
# sessions, entitlements and one-time profile issuance. No customer rows are printed.
docker compose -f compose.yml exec -T postgres sh -ec '
  export PGPASSWORD="$(cat /run/secrets/postgres_password)"
  psql -U ganj -d ganj_restore -v ON_ERROR_STOP=1 -Atc "
    SELECT CASE WHEN
      to_regclass('"'"'public.control_api_migrations'"'"') IS NOT NULL AND
      to_regclass('"'"'public.control_users'"'"') IS NOT NULL AND
      to_regclass('"'"'public.control_devices'"'"') IS NOT NULL AND
      to_regclass('"'"'public.control_auth_sessions'"'"') IS NOT NULL AND
      to_regclass('"'"'public.control_services'"'"') IS NOT NULL AND
      to_regclass('"'"'public.control_connection_profile_grants'"'"') IS NOT NULL
    THEN '"'"'restore_integrity_ok'"'"' ELSE '"'"'restore_integrity_failed'"'"' END;
  " | grep -qx restore_integrity_ok
'

# Minimize downtime: only now stop API traffic and atomically-ish swap database names.
docker compose -f compose.yml stop control-api
restart_live() { docker compose -f compose.yml start control-api >/dev/null 2>&1 || true; }
trap restart_live EXIT INT TERM

if ! docker compose -f compose.yml exec -T postgres sh -ec '
  export PGPASSWORD="$(cat /run/secrets/postgres_password)"
  psql -U ganj -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '\''ganj'\'' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS ganj_old;
ALTER DATABASE ganj RENAME TO ganj_old;
ALTER DATABASE ganj_restore RENAME TO ganj;
SQL
'; then
  # Best-effort recovery if the second rename failed after ganj was already renamed.
  docker compose -f compose.yml exec -T postgres sh -ec '
    export PGPASSWORD="$(cat /run/secrets/postgres_password)"
    psql -U ganj -d postgres -v ON_ERROR_STOP=1 -c "ALTER DATABASE ganj_old RENAME TO ganj" >/dev/null 2>&1 || true
  ' || true
  exit 1
fi

docker compose -f compose.yml start control-api
trap - EXIT INT TERM
STAGING_HOST=${STAGING_HOST:-staging.ganj.local} ./scripts/smoke.sh

echo 'Restore completed and smoke-tested. The previous database remains as ganj_old for explicit recovery.'
