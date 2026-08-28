#!/bin/sh
set -eu

environment_file="${1:-ops/environments/production.env}"
test -r "$environment_file"
set -a
. "$environment_file"
set +a

for variable in CONTROL_API_IMAGE POSTGRES_IMAGE NGINX_IMAGE; do
  eval "value=\${$variable:-}"
  printf '%s' "$value" | grep -Eq '@sha256:[0-9a-f]{64}$' || {
    printf '%s\n' "$variable must be pinned to an immutable sha256 digest" >&2
    exit 64
  }
done
: "${GANJ_SECRETS_DIR:?GANJ_SECRETS_DIR is required}"
case "$GANJ_SECRETS_DIR" in /*) ;; *) printf '%s\n' 'GANJ_SECRETS_DIR must be absolute' >&2; exit 64;; esac
for name in postgres_password database_url assignment_secret runtime_config_private_key.pem \
  telegram_oidc_client_secret google_play_service_account.json server_secret_resolver_token \
  telegram_account_broker_token tls_certificate.pem tls_private_key.pem; do
  path="$GANJ_SECRETS_DIR/$name"
  test -s "$path" || { printf '%s\n' "missing secret file: $name" >&2; exit 78; }
  permissions="$(stat -c '%a' "$path")"
  case "$permissions" in 400|440|600|640) ;; *) printf '%s\n' "unsafe secret permissions: $name" >&2; exit 78;; esac
done
printf '%s\n' '{"event":"production_environment_validated"}'
