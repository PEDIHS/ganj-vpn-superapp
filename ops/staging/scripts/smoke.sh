#!/usr/bin/env sh
set -eu

host=${STAGING_HOST:-staging.ganj.local}
base="https://${host}"

command -v curl >/dev/null 2>&1 || { echo 'curl is required' >&2; exit 1; }

curl_args="--fail --silent --show-error --connect-timeout 5 --max-time 15"
if [ "${STAGING_TLS_INTERNAL:-0}" = '1' ]; then
  curl_args="$curl_args --insecure"
fi

health=$(curl $curl_args "$base/healthz")
ready=$(curl $curl_args "$base/readyz")

printf '%s' "$health" | grep -q '"status":"ok"' || { echo 'liveness response is invalid' >&2; exit 1; }
printf '%s' "$ready" | grep -q '"status":"ready"' || { echo 'readiness response is invalid' >&2; exit 1; }

headers=$(mktemp)
trap 'rm -f "$headers"' EXIT INT TERM
curl $curl_args -D "$headers" -o /dev/null "$base/healthz"
grep -qi '^strict-transport-security:' "$headers" || { echo 'HSTS header missing' >&2; exit 1; }
grep -qi '^x-content-type-options:[[:space:]]*nosniff' "$headers" || { echo 'nosniff header missing' >&2; exit 1; }
grep -qi '^referrer-policy:[[:space:]]*no-referrer' "$headers" || { echo 'no-referrer header missing' >&2; exit 1; }

# Public catalog is a safe unauthenticated contract and proves edge -> API routing beyond probes.
plans=$(curl $curl_args "$base/v1/store/plans?channel=direct")
printf '%s' "$plans" | grep -q '"error":null' || { echo 'public catalog smoke check failed' >&2; exit 1; }

printf 'staging smoke passed for %s\n' "$host"
