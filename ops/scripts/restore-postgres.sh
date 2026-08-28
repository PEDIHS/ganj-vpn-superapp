#!/bin/sh
set -eu

: "${RESTORE_CONFIRMATION:?RESTORE_CONFIRMATION is required}"
[ "$RESTORE_CONFIRMATION" = "${RESTORE_DATABASE_NAME:-ganj_restore_verify}" ] || {
  printf '%s\n' 'restore confirmation does not match target database' >&2
  exit 64
}
if [ -n "${RESTORE_DATABASE_URL_FILE:-}" ]; then
  test -r "$RESTORE_DATABASE_URL_FILE"
  RESTORE_DATABASE_URL="$(cat "$RESTORE_DATABASE_URL_FILE")"
fi
: "${RESTORE_DATABASE_URL:?RESTORE_DATABASE_URL or RESTORE_DATABASE_URL_FILE is required}"
archive="${RESTORE_ARCHIVE:-/backups/latest.dump}"
test -r "$archive"
pg_restore --list "$archive" >/dev/null
pg_restore --dbname="$RESTORE_DATABASE_URL" --clean --if-exists --no-owner --no-privileges --exit-on-error "$archive"
psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -Atc \
  "SELECT CASE WHEN to_regclass('public.control_api_migrations') IS NOT NULL THEN 'restore-ok' ELSE 'restore-invalid' END" |
  grep -qx restore-ok
printf '%s\n' '{"event":"postgres_restore_verified"}'
