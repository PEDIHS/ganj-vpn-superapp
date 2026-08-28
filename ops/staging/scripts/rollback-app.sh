#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

[ "${ROLLBACK_CONFIRM:-}" = 'staging-app' ] || { echo 'Set ROLLBACK_CONFIRM=staging-app to authorize application rollback.' >&2; exit 1; }
[ -f .deploy-state ] || { echo '.deploy-state is missing; no recorded previous image.' >&2; exit 1; }
[ -f .env ] || { echo 'ops/staging/.env is required' >&2; exit 1; }

set -a
# shellcheck disable=SC1091
. ./.env
# shellcheck disable=SC1091
. ./.deploy-state
set +a

previous=${PREVIOUS_CONTROL_API_IMAGE:-}
current=${CURRENT_CONTROL_API_IMAGE:-}
case "$previous" in *@sha256:*) ;; *) echo 'Recorded previous image is not immutable.' >&2; exit 1;; esac

export CONTROL_API_IMAGE="$previous"
docker compose -f compose.yml pull control-api
docker compose -f compose.yml up -d --no-deps control-api
STAGING_HOST=${STAGING_HOST:-staging.ganj.local} ./scripts/smoke.sh

umask 077
cat > .rollback-evidence <<EOF
ROLLED_BACK_FROM=$current
ROLLED_BACK_TO=$previous
ROLLED_BACK_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
DATABASE_ACTION=none
EOF

printf 'Application rollback completed: %s\n' "$previous"
