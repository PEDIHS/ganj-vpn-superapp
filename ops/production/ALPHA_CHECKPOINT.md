# Alpha 0.1 — Server checkpoint

Updated: 2026-09-11 UTC. The running server is authoritative; GitHub records reviewed changes.

- Source baseline deployed: `7bf94d77f503c02f0b1b1cd9f61245802d91bec4`.
- Branch: `feat/server-runtime-ganj-bot-integration`; PR #52.
- Target: `/opt/ganj-vpn/current` on the existing bot host.
- Control API: native Node 22 systemd service `ganj-control-api`, user `ganj-api`, localhost:8080.
- HTTPS base: `https://bot.pedramhs.ir/control-api/` through the existing Apache TLS vhost.
- `/healthz` and `/readyz`: HTTP 200. Unauthenticated `/v1/services`: HTTP 401. Bot root remains HTTP 200.
- Database credentials and signing keys remain in private files outside the repository.
- All six existing production migrations verified after a private PostgreSQL backup.
- Local backend tests: 104 pass, 1 PostgreSQL integration test skipped. This is not Android/VPN evidence.

## Legacy sync boundary

A first bounded run successfully read and checkpointed two pages (200 events). All 200 are unresolved account-link conflicts; no user or paid entitlement was invented. 166 distinct `legacy_customer_unlinked` conflicts exist. No account or plan mappings currently exist in PostgreSQL. The timer is not enabled yet. The one-page diagnostic limit produced `legacy_reconciliation_page_limit_reached`; this is not a failed network connection.

## Exact next actions

1. Wire the existing Bot Approval contract into this server runtime and into the running PHP webhook after its trusted-update/user-block/rate gates.
2. Prove real guest authentication over HTTPS without exposing tokens.
3. Resolve conflict replay and account-triggered reconciliation so previously seen events become services after login; import only explicit bot plan mappings.
4. Wire actual PasarGuard service locators and live profile issuance.
5. Build/install Android, register the signing identity and expose the verified APK through the bot.
6. Prove real-device VPN traffic and record remaining device gates.

Do not replace the running PHP bot with a GitHub snapshot. Do not seed production with fixture plans/users/servers. Do not claim VPN Connected from API health.
