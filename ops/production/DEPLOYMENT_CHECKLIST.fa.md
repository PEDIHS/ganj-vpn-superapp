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
- [ ] Create isolated `/opt/ganj-vpn` runtime tree with root-owned secrets.
- [ ] Deploy PostgreSQL 16 with persistent volume and no public host port.
- [ ] Create production database/user with generated secret.
- [ ] Add encrypted PostgreSQL backup + restore validation path.
- [ ] Reserve localhost ports for Control API/Admin without colliding with bot services.

## B. Control API

- [ ] Deploy the current `services/control-api` runtime from a pinned commit/image.
- [ ] Run database migrations against PostgreSQL.
- [ ] Configure production adapter mode.
- [ ] Configure `/healthz` and `/readyz` checks.
- [ ] Configure runtime signing keys and assignment key as files, never committed env literals.
- [ ] Configure Telegram auth/session secret boundary.
- [ ] Configure server secret resolver.
- [ ] Verify Catalog, Auth, Services, Profile Broker, Billing, Support and Admin routes.
- [ ] Add systemd/Compose restart policy and bounded logs.

## C. Existing Ganj Telegram Bot integration

- [ ] Add a read-only Bot bridge endpoint for the canonical `ownership-v1` projection.
- [ ] Add append-only ownership/change journal on the PHP bot side; do not use `time_sell` as an update cursor.
- [ ] Map Telegram numeric identity to Control API accounts.
- [ ] Map legacy plans/products to Super App plan IDs explicitly.
- [ ] Project purchased/renewed/expired/revoked ownership idempotently.
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

- [ ] Keep `bot.pedramhs.ir` PHP vhost unchanged except the intentional Bot bridge route.
- [ ] Add separate API hostname/vhost that reverse-proxies only to localhost Control API.
- [ ] Add separate Admin hostname/vhost or protected route.
- [ ] Issue/renew Let's Encrypt certificates.
- [ ] Add security headers, request limits and proxy timeouts.
- [ ] Ensure PostgreSQL, MariaDB and Control API internal ports are not Internet-exposed.

## G. Android linkage

- [ ] Point Android production Control API base URL to the production API hostname.
- [ ] Publish Android App Link `assetlinks.json` for Telegram account-link callback.
- [ ] Complete Telegram Bot Approval primary login flow + OIDC fallback.
- [ ] Validate Guest -> Free service path.
- [ ] Validate Telegram linked -> legacy paid services path.
- [ ] Validate Store -> purchase -> verified Entitlement -> connect path.
- [ ] Validate renew/expire/revoke changes propagate to Android.

## H. Data protection and release gates

- [ ] Snapshot Bot MariaDB before adding bridge/journal schema.
- [ ] Snapshot/backup PostgreSQL before every migration batch.
- [ ] Run Control API unit/integration/coverage gates.
- [ ] Run Admin Console tests and secret-leak guards.
- [ ] Run Android unit/lint/build/instrumentation gates.
- [ ] Run real VPN E2E, DNS/IPv6 leak, network-change and device tests.
- [ ] Perform encrypted backup restore drill.
- [ ] Perform application rollback drill without database rollback.
- [ ] Capture redacted production evidence before claiming release-ready.

## First implementation slice

1. Preserve the running Telegram bot as-is and make a fresh DB snapshot.
2. Bring up isolated PostgreSQL + Control API on localhost only.
3. Reverse-proxy a dedicated API hostname through Apache.
4. Implement the PHP `ownership-v1` bridge and append-only journal.
5. Run legacy reconciliation against real Bot data.
6. Bring up the Admin Console and expose Bot/PasarGuard reconciliation health.
7. Only after backend E2E succeeds, point the Android app at the new production API.
