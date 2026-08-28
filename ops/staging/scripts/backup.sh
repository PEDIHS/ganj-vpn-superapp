#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then echo 'docker is required' >&2; exit 1; fi
if ! command -v age >/dev/null 2>&1; then echo 'age is required for encrypted backups' >&2; exit 1; fi
if [ ! -f .env ]; then echo 'ops/staging/.env is required' >&2; exit 1; fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${BACKUP_AGE_RECIPIENT:?BACKUP_AGE_RECIPIENT is required}"

mkdir -p backups
umask 077
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
output="backups/ganj-staging-${timestamp}.dump.age"
tmp="${output}.partial"
trap 'rm -f "$tmp"' EXIT INT TERM

# The database password remains inside the postgres container through POSTGRES_PASSWORD_FILE.
# pg_dump writes a custom-format stream to stdout; age encrypts it before any persistent write.
docker compose -f compose.yml exec -T postgres \
  sh -ec 'export PGPASSWORD="$(cat /run/secrets/postgres_password)"; pg_dump -U ganj -d ganj --format=custom --no-owner --no-privileges' \
  | age -r "$BACKUP_AGE_RECIPIENT" -o "$tmp"

[ -s "$tmp" ] || { echo 'encrypted backup is empty' >&2; exit 1; }
mv "$tmp" "$output"
trap - EXIT INT TERM
sha256sum "$output" > "${output}.sha256"
chmod 0600 "$output" "${output}.sha256"
printf '%s\n' "$output"
