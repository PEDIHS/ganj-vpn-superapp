#!/bin/sh
set -eu
umask 077

if [ -n "${DATABASE_URL_FILE:-}" ]; then
  test -r "$DATABASE_URL_FILE"
  DATABASE_URL="$(cat "$DATABASE_URL_FILE")"
fi
: "${DATABASE_URL:?DATABASE_URL or DATABASE_URL_FILE is required}"
backup_directory="${BACKUP_DIRECTORY:-/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-7}"
case "$retention_days" in ''|*[!0-9]*) printf '%s\n' 'BACKUP_RETENTION_DAYS must be numeric' >&2; exit 64;; esac
mkdir -p "$backup_directory"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$backup_directory/control-api-$timestamp.dump"
temporary="$archive.partial"
trap 'rm -f "$temporary"' EXIT HUP INT TERM
pg_dump --dbname="$DATABASE_URL" --format=custom --compress=9 --no-owner --no-privileges --file="$temporary"
pg_restore --list "$temporary" >/dev/null
mv "$temporary" "$archive"
sha256sum "$archive" > "$archive.sha256"
ln -sfn "$(basename "$archive")" "$backup_directory/latest.dump"
find "$backup_directory" -type f -name 'control-api-*.dump' -mtime "+$retention_days" -delete
find "$backup_directory" -type f -name 'control-api-*.dump.sha256' -mtime "+$retention_days" -delete
printf '{"event":"postgres_backup_completed","archive":"%s"}\n' "$(basename "$archive")"
