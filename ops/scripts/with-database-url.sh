#!/bin/sh
set -eu

: "${DATABASE_URL_FILE:?DATABASE_URL_FILE is required}"
test -r "$DATABASE_URL_FILE"
DATABASE_URL="$(cat "$DATABASE_URL_FILE")"
: "${DATABASE_URL:?database URL secret is empty}"
export DATABASE_URL
exec "$@"
