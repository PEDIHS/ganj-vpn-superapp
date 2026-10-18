# GANJ customer state: durable event delivery, no polling daemon

Status: proposed architecture. Not enabled in production. No DB migration or service
replacement is authorized by this document.

## Confirmed constraints (October 18, 2026)

- The canonical source for bot wallet, invoice, receipt and customer status is
  the existing GANJ Bot MariaDB database.
- log_bin=OFF and binlog_format=MIXED on the inspected production MariaDB.
  Binlog-based CDC is not available without changing database operations.
- The Android app has multiple separately encrypted Telegram account slots.
  A signal must be scoped to the authenticated user ID (not installation ID,
  IP address, username, Telegram handle or untrusted request parameter).
- Finance actions are server-authoritative; UI never grants entitlements.
- No daemon should periodically recalculate every user's statistics.
- Android can be suspended indefinitely; replay is required on reconnection.

## Proposed path (next phase, not currently live)

1. Create a narrow ganj_customer_event_outbox InnoDB table with sequential event
   ID, canonical bot user ID, event kind (wallet, invoice, receipt,
   entitlement, profile), creation timestamp. NEVER include balances, tokens,
   payment card details or raw VPN configurations. Bound replay and retention.
2. Each canonical bot mutation writes an outbox row INSIDE the SAME DB
   transaction as the original modification, and publishes only AFTER COMMIT.
   Audit every write path (Telegram bot handlers, administrator approvals,
   refunds, renewals, manual credit/debit, site invoices). Site API alone is
   not enough. Durable replay protects against missing publisher signals.
3. Reuse the existing authenticated Control API process to send per-principal
   Server-Sent Events (SSE). No additional account-scanning process. The
   post-commit PHP publisher calls a localhost-only HMAC authenticated route,
   checking timestamp, nonce and event identity. Push failure must NEVER
   rollback or block a completed payment.
4. On connect/reconnect, the app provides a PER-ACCOUNT last-seen revision.
   Server reads only the bounded outbox delta for that authenticated user,
   then joins live events. If cursor is older than retained history, send a
   resync-required event and fetch one authoritative snapshot. Monotonically
   increasing IDs and duplicate suppression protect against repeats.
5. Android opens at most one subscription for the foreground, verified account.
   Background/Doze disconnection is expected. A received event only invalidates
   the affected local view, which re-reads the canonical Bot DB through existing
   authenticated APIs. NEVER apply a pushed balance directly.
6. Horizontal scaling: in-memory SSE does not fan out across replicas. Before
   adding Control API replicas, use shared pub/sub for fanout; MariaDB outbox
   still owns durability. Do not assume Redis is available: inspected bot host
   had redis/redis-server inactive.
7. Operate with per-user/global connection caps, heartbeat, per-client
   backpressure limits, no bearer token in URL, Cache-Control: no-store,
   read-only GET, redacted logs and key rotation.

## Fail-closed end-to-end acceptance cases

- Duplicate event/post-commit attempt never causes duplicate credit or payment.
- Bot writes while SSE/Android offline: reconnect and replay updates that user.
- One principal never receives another customer's event.
- Account switch closes old stream; drops old queued events and cookies.
- Manual credit, refund, receipt approval and renewals each update correct user.
- Expired event cursor forces a single safe resync and no local value invention.
- PHP process crashes after COMMIT: outbox survives and supports replay.
- Stress test >10k events plus disconnected clients with bounded memory.

## Implemented so far in the Android draft

- Financial snapshot/transaction reads bypass time-based cached results.
- On a genuine money/receipt mutation, a local generation event invalidates
  active wallet, profile and transaction views.
- On foreground resume the screens read once; no periodic background poller.
- Catalog-only TTL cache remains bounded; single-flight locks do not leak.
- This is NOT production bot-origin live SSE. Implement and test the protocol
  before asserting full real-time consistency.
