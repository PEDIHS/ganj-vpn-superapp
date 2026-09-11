# Ganj VPN — Production Server Deployment Checklist

> Target host: the existing Ganj production server that also hosts the Telegram bot.
> Goal: deploy Control API, PostgreSQL, Admin Console and the Bot integration without disrupting the existing PHP bot or its MariaDB data.

## Architecture lock

- Apache remains the public edge already serving `bot.pedramhs.ir`.
- Ganj Control API runs privately on localhost/container network; no public :8080 exposure.
- PostgreSQL is isolated from the Internet and is dedicated to the Super App backend.
- Telegram bot PHP/MariaDB remains a separate commercial source-of-truth boundary.
- Android never accesses MariaDB or PasarGuard directly.
- Bot ownership/subscription state is projected through the approved ownership-v1 bridge/reconciliation pipeline.
- PasarGuard remains runtime truth for live VPN state, usage, expiry, node inventory and connection material.
- No raw VPN URI, subscription URL, provider credential, bot token or DB password is exposed to Android/Admin responses.

## A. Server foundation

- [x] Confirm Node.js >= 22 on host.
- [x] Confirm Docker + Docker Compose are available.
- [x] Confirm Apache/HTTPS for the existing bot is healthy before app work.
- [x] Create isolated `/opt/ganj-vpn` runtime tree with root-owned secrets.
- [x] Deploy PostgreSQL 16 with persistent storage and localhost-only exposure.
- [x] Create production database/user with generated secret.
- [ ] Add encrypted PostgreSQL backup + restore validation path.
- [x] Reserve localhost ports for Control API/Admin without colliding with bot services.

## B. Control API

- [x] Add immutable production image/build-and-deploy workflow on the integration branch.
- [x] Add hardened production Compose runtime for Control API + Legacy Sync.
- [ ] Deploy the current `services/control-api` runtime from a pinned commit/image.
- [x] Run the first production database migration batch against PostgreSQL.
- [ ] Apply/verify every remaining migration in repository order before first API start.
- [ ] Configure production adapter mode.
- [ ] Configure `/healthz` and `/readyz` checks.
- [ ] Configure runtime signing keys and assignment key as files, never committed env literals.
- [ ] Configure Telegram auth/session secret boundary.
- [ ] Configure server secret resolver.
- [ ] Verify Catalog, Auth, Services, Profile Broker, Billing, Support and Admin routes.
- [x] Define restart policy, read-only root filesystem, dropped capabilities and bounded tmpfs in production Compose.

## C. Existing Ganj Telegram Bot integration

- [x] Add a protected Bot bridge endpoint for the canonical `ownership-v1` projection.
- [x] Add append-only ownership/change journal on the PHP bot side; do not use `time_sell` as an update cursor.
- [ ] Map Telegram numeric identity to Control API accounts after authenticated account link.
- [x] Create explicit legacy plan/product mapping table for Super App reconciliation.
- [ ] Project purchased/renewed/expired/revoked ownership idempotently through the live worker.
- [ ] Reject ownership collisions/orphans into reconciliation conflicts instead of guessing.
- [ ] Store only safe PasarGuard locator metadata; never project raw configs.
- [ ] Run legacy sync worker on a checkpointed schedule.
- [ ] Verify old Telegram purchasers appear automatically in Android `My Services` after account link.

## D. PasarGuard integration

- [ ] Configure backend-only PasarGuard credentials/secret references.
- [ ] Map commercial service -> PasarGuard upstream binding.
- [ ] Read live status, traffic, expiry and eligible nodes from PasarGuard.
- [ ] Validate only supported VLESS/VMess/Trojan/Shadowsocks profiles server-side.
- [ ] Issue short-lived, one-time, device-bound sealed profiles to Android.
- [ ] Prove Android never receives reusable subscription URLs/admin credentials.

## E. Admin Console

- [ ] Deploy `apps/admin-console` behind Apache on a dedicated protected hostname/path.
- [ ] Connect it to `/v1/admin/*` through the same Control API auth boundary.
- [ ] Enable server registry, emergency disable, reconciliation conflicts and support workspace.
- [ ] Add operational views for Bot sync checkpoint and PasarGuard health.
- [ ] Verify audit records for every mutation.
- [ ] Keep admin credentials/tokens out of LocalStorage/SessionStorage.

## F. Apache / TLS / exposure

- [x] Keep `bot.pedramhs.ir` PHP vhost online while backend work proceeds.
- [ ] Add separate API hostname/vhost that reverse-proxies only to localhost Control API.
- [ ] Add separate Admin hostname/vhost or protected route.
- [ ] Issue/renew Let's Encrypt certificates for API/Admin hostnames.
- [ ] Add security headers, request limits and proxy timeouts.
- [x] Keep PostgreSQL localhost-only; re-verify MariaDB and Control API exposure before launch.

## G. Android linkage and publication

- [ ] Point Android production Control API base URL to the production API hostname.
- [ ] Publish Android App Link `assetlinks.json` for Telegram account-link callback.
- [ ] Complete Telegram Bot Approval primary login flow + OIDC fallback.
- [ ] Validate Guest -> Free service path.
- [ ] Validate Telegram linked -> legacy paid services path.
- [ ] Validate Store -> purchase -> verified Entitlement -> connect path.
- [ ] Validate renew/expire/revoke changes propagate to Android.
- [x] Confirm signed AAB release workflow exists with SBOM, vulnerability gate, provenance and protected signing.
- [x] Confirm optional Google Play publication path exists for `internal` and `alpha` tracks.
- [x] Add Persian release-readiness runbook with required Environments, secrets, variables and Play Console checklist.
- [ ] Configure protected GitHub Environments/secrets for candidate/production signing and Play publication.
- [ ] Set `GANJ_CANDIDATE_CONTROL_API_BASE_URL` and `GANJ_PRODUCTION_CONTROL_API_BASE_URL` after API hostnames are live.

## H. Data protection and release gates

- [x] Preserve the production Bot/MariaDB boundary while adding journal/bridge schema.
- [ ] Snapshot/backup PostgreSQL before every future migration batch.
- [ ] Run Control API unit/integration/coverage gates on the exact deploy commit.
- [ ] Run Admin Console tests and secret-leak guards.
- [ ] Run Android unit/lint/build/instrumentation gates.
- [ ] Run real VPN E2E, DNS/IPv6 leak, network-change and device tests.
- [ ] Perform encrypted backup restore drill.
- [ ] Perform application rollback drill without database rollback.
- [ ] Capture redacted production evidence before claiming release-ready.

## Current implementation slice

1. Deploy exact Control API commit through the protected production workflow/runtime.
2. Bring `/readyz` green on localhost and then expose a dedicated HTTPS API hostname through Apache.
3. Start the checkpointed Legacy Sync worker against the existing ownership-v1 bridge.
4. Resolve plan/account mapping conflicts; never guess ownership.
5. Wire PasarGuard live-state bindings.
6. Deploy Admin Console and expose Bot/PasarGuard reconciliation health.
7. Set Android candidate/production API repository variables.
8. Execute signed Candidate AAB -> internal Play track -> real-device E2E -> production release.
