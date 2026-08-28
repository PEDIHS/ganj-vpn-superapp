#!/bin/sh
set -eu

die() { printf '%s\n' "$1" >&2; exit 78; }
read_secret() {
  variable="$1"
  file_variable="${variable}_FILE"
  eval "file_path=\${$file_variable:-}"
  [ -n "$file_path" ] || die "$file_variable is required"
  [ -r "$file_path" ] || die "$file_variable is not readable"
  value="$(cat "$file_path")"
  [ -n "$value" ] || die "$file_variable is empty"
  export "$variable=$value"
  unset value
}

[ "${NODE_ENV:-}" = production ] || die "NODE_ENV must be production"
[ "${CONTROL_API_ADAPTER_MODE:-}" = production ] || die "CONTROL_API_ADAPTER_MODE must be production"
for variable in CONTROL_API_DATA_ADAPTER_MODULE CONTROL_API_AUTH_ADAPTER_MODULE \
  CONTROL_API_PURCHASE_ADAPTER_MODULE CONTROL_API_TELEGRAM_AUTH_ADAPTER_MODULE \
  CONTROL_API_PLAY_NOTIFICATIONS_ADAPTER_MODULE CONTROL_API_ENTERPRISE_SECURITY_ADAPTER_MODULE \
  CONTROL_API_SERVER_SECRET_ADAPTER_MODULE CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE \
  AUTH_JWKS_URI AUTH_ISSUER AUTH_AUDIENCE TELEGRAM_OIDC_TOKEN_ENDPOINT \
  TELEGRAM_OIDC_JWKS_URI TELEGRAM_OIDC_ISSUER TELEGRAM_OIDC_CLIENT_ID \
  PLAY_RTDN_AUDIENCE PLAY_RTDN_SERVICE_ACCOUNT PLAY_RTDN_SUBSCRIPTION; do
  eval "value=\${$variable:-}"
  [ -n "$value" ] || die "$variable is required"
done
read_secret DATABASE_URL
read_secret TELEGRAM_OIDC_CLIENT_SECRET
exec "$@"
