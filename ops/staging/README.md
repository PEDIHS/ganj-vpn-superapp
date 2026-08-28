# Ganj VPN production-like staging

This stack exists to prove the release-critical production boundaries before public rollout. It is not a developer convenience stack and intentionally fails closed when real production adapters or secret resolution are unavailable.

## Architecture

`Internet -> Caddy TLS edge -> Control API -> PostgreSQL`

The Control API also has outbound-only access to Vault, Telegram and Google provider endpoints through the separate `egress` network. PostgreSQL and the API never publish host ports. Only Caddy publishes `80/443`.

The `migrate` and `control-api` services must use the exact same immutable `CONTROL_API_IMAGE=...@sha256:<digest>` value. Mutable tags and local builds are forbidden for accepted staging evidence.

## Secret model

No reusable VPN credential belongs in this directory, `.env`, GitHub Actions variables or database rows. `control_servers.secret_ref` points to Vault KV using `vault:<path>`. The Vault adapter enforces HTTPS, an allowlisted path prefix, response-size/time bounds and startup health.

The remaining bootstrap material is mounted as read-only files:

- Vault token;
- Auth Session Ed25519 private JWK;
- Telegram client secret;
- enterprise assignment key;
- runtime-config Ed25519 signing key;
- Google Play service-account JSON;
- PostgreSQL password.

`services/control-api/src/production-environment.js` imports permitted `*_FILE` values into process memory at startup and rejects unsafe file permissions. Do not copy any of those values into `.env`.

## First deployment

1. Copy `.env.example` to `.env` and replace every placeholder with staging-specific public metadata.
2. Set a real immutable `CONTROL_API_IMAGE` digest built from the commit being validated.
3. Provision a dedicated staging Vault policy limited to the configured `CONTROL_API_VAULT_SECRET_PREFIX`.
4. Create the files documented under `secrets/README.md` with restrictive ownership and mode.
5. Point `STAGING_HOST` DNS to the staging host and allow Caddy to obtain a certificate.
6. Run `docker compose --env-file .env -f compose.yml config` and inspect the rendered topology.
7. Run `./scripts/deploy.sh <immutable-image@sha256:digest>`.
8. Run `./scripts/smoke.sh` from a machine that reaches the public hostname.
9. Record only redacted results in the release evidence template. Never paste tokens, JWTs, Vault responses, purchase tokens or VPN connection material.

## Health semantics

`/healthz` is liveness only. It proves that the HTTP process is alive.

`/readyz` is readiness. It performs a repository-level query and must return non-2xx if the application cannot safely serve traffic. Container health uses readiness, not liveness.

## Encrypted backup

Set the public age recipient in `.env` as `BACKUP_AGE_RECIPIENT` and run:

```sh
./scripts/backup.sh
```

`pg_dump` streams directly into `age`; a plaintext dump is never persisted. The resulting `.dump.age` and checksum are deliberately ignored by Git.

## Restore proof

Use a staging-only age private identity kept outside the repository:

```sh
RESTORE_CONFIRM=staging AGE_IDENTITY_FILE=/secure/path/age-key.txt \
  ./scripts/restore.sh backups/<backup>.dump.age
```

The restore procedure:

1. verifies the encrypted artifact checksum when present;
2. restores into isolated `ganj_restore` while the live API continues serving `ganj`;
3. runs the current immutable image migrations against `ganj_restore`;
4. verifies migration/account/session/service/profile-grant tables without printing customer data;
5. stops the API only for the database-name swap;
6. keeps the previous database as `ganj_old`;
7. restarts the API and runs public TLS/readiness/catalog smoke checks.

A failed pre-swap restore never replaces the live database.

## Application rollback

Deployment records the previous immutable application image in `.deploy-state` with mode `0600`. To roll back the application without rolling the database backward:

```sh
ROLLBACK_CONFIRM=staging ./scripts/rollback-app.sh
```

The rollback script switches only the Control API image and immediately runs smoke checks. Database migrations are forward-only. Any schema change merged for public release must therefore remain compatible with the immediately previous application version for the documented rollback window.

## Acceptance evidence

Use `EVIDENCE.template.md`. The acceptance evidence must include:

- public hostname and certificate validation;
- deployed commit and immutable image digest;
- successful immutable migrations;
- Vault health and one redacted server-secret resolution check;
- `/healthz`, `/readyz` and public catalog smoke results;
- monitoring/alert verification;
- encrypted backup artifact checksum and timestamps;
- isolated restore + integrity + post-swap smoke result;
- measured backup/restore duration for RPO/RTO discussion;
- application rollback from image B to image A with post-rollback smoke result;
- verification that sessions/entitlements were not manually modified during rollback.

Repository CI validates the topology and scripts, but only a real host/domain/Vault/backup/restore/rollback run satisfies Issue #30 completely.
