#!/bin/sh
set -eu

: "${BASE_URL:?BASE_URL is required}"
case "$BASE_URL" in https://*) ;; *) printf '%s\n' 'BASE_URL must use HTTPS' >&2; exit 64;; esac
curl_args="--fail --silent --show-error --proto =https --tlsv1.2 --connect-timeout 5 --max-time 15"
health="$(curl $curl_args "$BASE_URL/healthz")"
ready="$(curl $curl_args "$BASE_URL/readyz")"
printf '%s' "$health" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"'
printf '%s' "$ready" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"ready"'
headers="$(mktemp)"
trap 'rm -f "$headers"' EXIT HUP INT TERM
curl $curl_args --dump-header "$headers" --output /dev/null "$BASE_URL/healthz"
grep -Eiq '^strict-transport-security:' "$headers"
grep -Eiq '^x-content-type-options:[[:space:]]*nosniff' "$headers"
grep -Eiq '^content-security-policy:' "$headers"
printf '%s\n' '{"event":"staging_smoke_passed"}'
