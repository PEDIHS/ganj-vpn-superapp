#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

image=${1:-}
[ -n "$image" ] || { echo 'usage: deploy.sh <immutable-control-api-image-ref>' >&2; exit 1; }
case "$image" in
  *@sha256:*) ;;
  *) echo 'Deployment requires an immutable @sha256 image reference.' >&2; exit 1 ;;
esac

[ -f .env ] || { echo 'ops/staging/.env is required' >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo 'docker is required' >&2; exit 1; }

set -a
# shellcheck disable=SC1091
. ./.env
set +a

previous=${CONTROL_API_IMAGE:-}
[ -n "$previous" ] || { echo 'CONTROL_API_IMAGE must already identify the currently deployed image.' >&2; exit 1; }

umask 077
cat > .deploy-state <<EOF
PREVIOUS_CONTROL_API_IMAGE=$previous
CURRENT_CONTROL_API_IMAGE=$image
DEPLOYED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

export CONTROL_API_IMAGE="$image"
docker compose -f compose.yml pull control-api migrate
docker compose -f compose.yml run --rm migrate
docker compose -f compose.yml up -d --no-deps control-api

STAGING_HOST=${STAGING_HOST:-staging.ganj.local} ./scripts/smoke.sh
printf '%s\n' "$image"
