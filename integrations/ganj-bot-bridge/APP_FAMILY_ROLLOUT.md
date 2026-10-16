# App catalog, bot picker and family rollout — 2026-10-16

Product: Telegram-only identity. Never restore the cancelled phone/password or synthetic wallet work.

- Native app catalog: 18 additive `ga_` products on the existing VIP panel. Economy/Standard/Professional are 30/50/100 GB for 30 days. Periodic Premium allocates 100 GB per 30 days for 30/60/90 days. No claim of literally unlimited traffic.
- Each variant uses authoritative existing VIP 1/2/3-device pricing/templates. Periodic 60/90-day prices use 1.8/2.55 monthly factors, matching the existing period discount pattern. Prices are stored in the bot product table; the Android client never multiplies prices.
- Original products/prices/invoices are untouched. Website legacy catalog hides `ga_` products; native user agent or native SSO session reads the exact app catalog. Public catalog selection never grants entitlement.
- Retail bot purchase opens VIP → tier → period → devices → exact existing product/payment handler. Representatives keep their existing flow. Preserve active-button/required-contact gates.
- Native SSO sets a server session origin marker. Only that marker selects a separate backend-only `ganj_app` admin token during PasarGuard createUser. No APK or browser receives panel credentials. Production admin ID 5, owner role, profile title «گنج اپ», unrestricted access to all seven groups at installation.
- Support uses existing authenticated `support_departments`, `support_tickets`, `support_ticket_create`, `support_ticket_reply`; admin receives the existing `Responsesupport_<tracking>` callback. No real support message was sent during mock delivery/picker tests.
- `ownership-v1` must parse actual device count from product display name instead of emitting constant 1. Additive app product names include that count. Ambiguous historical product names must not have an invented capacity.
- Backend migration 023 adds memberships and hashed, expiring single-use invites. Exact server migration 022 was imported unchanged (SHA256 `4b8183bd3792f977157c7af762d6b2e75d2b23ecfe73aea4d5591e3a57034a28`). Do not replace deployed migration 022 with the initial, never-deployed family draft.
- Backend retains owner-only finance and independent Telegram identities. Foreground VPN checks `/services/{id}/family/access` using a shared session manager; authoritative revocation disconnects, transient outage has bounded grace.
- Invitation token is a URL fragment on `/app/join`, not a query. Android App Link must remain covered by the existing verified domain/certificate. Landing page is installation guidance only.

Bot overlay snapshot: `/root/ganj-family-20261016/original`; staged files under `candidate`. Compare original hashes before overwriting production. Restore original overlay files to roll back bot behavior; new products may be hidden but never delete purchased invoices. Panel admin is additive. Do not change old service owners.

Backend deployment: copy current release to an isolated directory, overlay only reviewed changed source and new migrations, retain private PostgreSQL dump, run migrations with injected secrets (never printed), switch `current` atomically, restart only `ganj-control-api`, and verify health/auth denial before rollout. Retain the previous symlink target for rollback; expanded schema remains backward compatible with old code.

APK publication remains gated on current source CI, native/emulator/UI checks, same certificate, checksum and in-place update. Physical-phone/family multi-account UX and real payment/support reply evidence must be reported honestly.

Verified production bot overlay: native catalog HTTP 200, 18 app variants; ordinary web catalog still 48 original entries. Six bot-picker states and exact 243,000 Toman / two-device handoff tested with Telegram send functions replaced by local capture (zero messages sent). An ephemeral 1 MiB/5-minute panel service was created through `ManagePanel::createUser(app_origin=true)`; raw authenticated panel API confirmed admin `ganj_app`, and deletion returned HTTP 204. The token-selection conditional must be inside `adduser`, not an earlier unrelated `token_panel` call. No customer invoice or wallet was modified by that provisioning test.

## Verified backend activation — 2026-10-16

- Family control-api staged separately at `/opt/ganj-vpn/releases/family-sharing-candidate-20261016/services/control-api`. Earlier release was not edited in place.
- Existing production migration checksums matched the branch; only `023_service_family.sql` was newly added.
- PostgreSQL backup saved as `/opt/ganj-vpn/backups/family-pre-023-20261016.dump` (823059 bytes, mode 0600), verified with `pg_restore --list`; the private archive remains on the server.
- Production migration 023 applied and recorded. `control_service_members` and `control_service_invites` confirmed.
- Staging tests: 172 passed, 0 failed, 2 skipped. Fixed production test-seed prohibition before opening any connection, and included OpenAPI fixture in staging.
- Real production adapters booted on isolated loopback port 18082: protected routes returned unauthenticated HTTP 401.
- Atomically switched `/opt/ganj-vpn/current` to candidate and restarted only `ganj-control-api`. Service active; `/v1/me` HTTP 401, bot website HTTP 200.
- Previous release preserved at `/opt/ganj-vpn/releases/reality-stable-selection-20261003T0228/services/control-api` for immediate process rollback. Schema migration remains installed if the symlink is rolled back.
- **Pending evidence:** two-user live Telegram invitation/approval, capacity and removal, real VPN traffic after join/revoke, signed in-place Android upgrade, and real payment/receipt tests. No APK publication or physical-device QA was claimed.
