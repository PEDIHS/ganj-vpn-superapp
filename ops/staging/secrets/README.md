# Staging secrets

Do not commit real files into this directory. The only tracked file should be this README.

Create these files on the staging host before starting the stack:

- `postgres-password`
- `auth-session-private.jwk` — private Ed25519 JWK JSON used by Auth Session signing
- `telegram-oidc-client-secret`
- `assignment-key` — at least 32 random bytes
- `runtime-config-ed25519.pem`
- `google-play-service-account.json`
- `servers/*.json` — one resolved connection payload per `control_servers.secret_ref`

Server registry references use the form:

`file:de-premium-01.json`

and resolve relative to `/run/secrets/ganj/servers` inside the Control API container.

## Permissions

The Control API secret bootstrap/resolver rejects files with group/world permission bits. For a bind-mounted local staging host, make the files readable by the numeric uid/gid used by the `controlapi` container user and remove all group/world access. Verify the image uid before deployment, then apply restrictive ownership/mode on the host.

Example concept (replace UID/GID with the built image values):

```bash
chown 100:101 auth-session-private.jwk telegram-oidc-client-secret assignment-key runtime-config-ed25519.pem google-play-service-account.json servers/*.json
chmod 0400 auth-session-private.jwk telegram-oidc-client-secret assignment-key runtime-config-ed25519.pem google-play-service-account.json servers/*.json
chmod 0600 postgres-password
```

Never loosen secrets to `0644` merely to make a container boot. Fix ownership instead.

## Server secret JSON

A server secret file is backend-only connection material and is never sent as raw JSON to Android or Admin Console. Example shape:

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
