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

# Stop API traffic before replacing database state. PostgreSQL remains available only on the
# internal Docker network and the edge will have no healthy upstream while control-api is stopped.
docker compose -f compose.yml stop control-api
trap 'docker compose -f compose.yml start control-api >/dev/null 2>&1 || true' EXIT INT TERM

age -d -i "$identity" "$backup" \
  | docker compose -f compose.yml exec -T postgres sh -ec '
      export PGPASSWORD="$(cat /run/secrets/postgres_password)"
      dropdb --if-exists -U ganj ganj_restore
      createdb -U ganj ganj_restore
      pg_restore -U ganj -d ganj_restore --no-owner --no-privileges --exit-on-error
      psql -U ganj -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '\''ganj'\'' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS ganj_old;
ALTER DATABASE ganj RENAME TO ganj_old;
ALTER DATABASE ganj_restore RENAME TO ganj;
SQL
    '

# Re-run idempotent migrations against the restored database before reopening API traffic.
docker compose -f compose.yml run --rm migrate
docker compose -f compose.yml start control-api
trap - EXIT INT TERM

echo 'Restore completed. The previous database remains as ganj_old for explicit post-restore rollback.'
