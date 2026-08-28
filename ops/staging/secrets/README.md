# Staging secrets

Do not commit real files into this directory. The only tracked files are documentation/placeholders.

Create these files on the staging host before starting the stack:

- `postgres-password`
- `vault-token` — short-lived/revocable Vault token restricted to the Ganj server-secret prefix
- `auth-session-private.jwk` — private Ed25519 JWK JSON used by Auth Session signing
- `telegram-oidc-client-secret`
- `assignment-key` — at least 32 random bytes
- `runtime-config-ed25519.pem`
- `google-play-service-account.json`

The production-like staging default uses the Vault resolver. `control_servers.secret_ref` values therefore use a form such as:

`vault:kv/data/ganj/servers/de-premium-01`

The Vault policy for this token should grant read access only to the configured `CONTROL_API_VAULT_SECRET_PREFIX`. Do not use a root token.

`servers/*.json` exists only for the local file-resolver fallback (`file-server-secret.js`) and should not be used as acceptance evidence for Issue #30 when a real Vault/KMS environment is available.

## Permissions

The Control API secret bootstrap and Vault token loader reject files with group/world permission bits. For bind-mounted staging secrets, make the files readable by the numeric uid/gid used by the `controlapi` container user and remove all group/world access. Verify the image uid before deployment, then apply restrictive ownership/mode on the host.

Example concept (replace UID/GID with the built image values):

```bash
chown 100:101 vault-token auth-session-private.jwk telegram-oidc-client-secret assignment-key runtime-config-ed25519.pem google-play-service-account.json
chmod 0400 vault-token auth-session-private.jwk telegram-oidc-client-secret assignment-key runtime-config-ed25519.pem google-play-service-account.json
chmod 0600 postgres-password
```

Never loosen secrets to `0644` merely to make a container boot. Fix ownership instead.

## Vault server secret value

A Vault KV v2 secret contains backend-only connection material and is never sent as raw JSON to Android or Admin Console. The `data` value should have a shape such as:

```json
{
  "endpoint": "vpn.example.internal",
  "port": 443,
  "protocol": "vless",
  "credential": "00000000-0000-4000-8000-000000000000",
  "transport": { "type": "tcp" },
  "security": {
    "type": "tls",
    "server_name": "vpn.example.internal",
    "fingerprint": "chrome"
  },
  "flow": "xtls-rprx-vision"
}
```

Do not use the example credential in a real environment.
