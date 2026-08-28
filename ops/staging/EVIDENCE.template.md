# Staging Release Evidence

> Copy this file outside the repository or into the ignored `ops/staging/evidence/` directory for each accepted run. Redact all secrets, JWTs, purchase tokens, provider responses, user identifiers and VPN connection material before attaching evidence to a release.

## Identity

- Date/time UTC:
- Operator:
- Git commit:
- Control API image digest (`@sha256:`):
- Staging hostname:
- Android candidate version/build (if applicable):

## TLS and network boundary

- DNS resolves to intended staging host: PASS / FAIL
- Valid certificate chain and hostname: PASS / FAIL
- HTTP redirects to HTTPS: PASS / FAIL
- HSTS present: PASS / FAIL
- PostgreSQL has no public host port: PASS / FAIL
- Control API has no public host port: PASS / FAIL
- Only edge publishes 80/443: PASS / FAIL
- Evidence reference:

## Production adapter and secret resolution

- `CONTROL_API_ADAPTER_MODE=production`: PASS / FAIL
- Test adapters rejected in production: PASS / FAIL
- Vault address is HTTPS: PASS / FAIL
- Vault policy restricted to configured server prefix: PASS / FAIL
- Vault health startup check: PASS / FAIL
- One server `secret_ref` resolved successfully without displaying value: PASS / FAIL
- No raw server credential stored in database/release evidence: PASS / FAIL
- Auth signing/Telegram/Play/enterprise secrets mounted via secret files: PASS / FAIL
- Evidence reference:

## Database and migrations

- PostgreSQL version:
- Migration command exit status:
- Migration checksum validation: PASS / FAIL
- `/readyz` after migration: PASS / FAIL
- Evidence reference:

## Public smoke

- `/healthz`: PASS / FAIL
- `/readyz`: PASS / FAIL
- `/v1/store/plans?channel=direct`: PASS / FAIL
- Response correlation/error envelope spot-check: PASS / FAIL
- Evidence reference:

## Monitoring and alerts

- API unavailable alert exercised: PASS / FAIL
- Readiness/database failure alert exercised: PASS / FAIL
- Error-rate alert exercised or synthetic equivalent: PASS / FAIL
- Recovery clears alert: PASS / FAIL
- Evidence reference:

## Encrypted backup

- Backup started UTC:
- Backup completed UTC:
- Duration seconds:
- Encrypted artifact path/reference:
- SHA-256 (safe to record):
- Plaintext dump persisted to disk: NO required
- Artifact can be decrypted with recovery identity: PASS / FAIL
- Evidence reference:

## Isolated restore

- Restore started UTC:
- `ganj_restore` restore: PASS / FAIL
- Current migrations applied to isolated DB before swap: PASS / FAIL
- Core-table integrity check: PASS / FAIL
- Live DB replaced only after isolated validation: PASS / FAIL
- Previous DB retained as `ganj_old`: PASS / FAIL
- Post-swap `/readyz` and smoke: PASS / FAIL
- Restore completed UTC:
- Duration seconds:
- Evidence reference:

## Application rollback

- Image A (previous digest):
- Image B (candidate digest):
- Deploy B smoke: PASS / FAIL
- Rollback confirmation used: PASS / FAIL
- Restored image A digest:
- Database rollback performed: NO required
- Manual entitlement/session mutation performed: NO required
- Post-rollback `/readyz` and public smoke: PASS / FAIL
- Evidence reference:

## RPO / RTO observation

- Backup cadence tested:
- Observed potential data-loss window (RPO evidence):
- Observed restore/service recovery duration (RTO evidence):
- Meets current release objective: PASS / FAIL

## Final operator statement

- [ ] No secret or private connection material is present in this evidence.
- [ ] No manual database intervention was needed to make application behavior appear successful, except the documented restore procedure itself.
- [ ] Any failure is linked to an issue before this run is accepted.
- [ ] This evidence corresponds exactly to the commit/image digest listed above.

Result: ACCEPTED / REJECTED
