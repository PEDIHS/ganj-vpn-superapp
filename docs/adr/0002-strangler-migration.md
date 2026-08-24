# ADR-0002: Strangler Migration for the Telegram Bot Backend

- Status: Accepted
- Date: 2026-08-24

## Decision

Do not rewrite or directly expose the existing PHP bot API. Introduce a versioned Control API and a Legacy Adapter, then migrate domains independently.

## Rationale

The existing system already contains valuable commerce/provider logic but has a large coupled schema and single-token sessions. Big-bang replacement risks sales, wallet balance and active services.

## Consequences

- Temporary dual systems and reconciliation jobs are required.
- Every dual-write needs idempotency and outbox delivery.
- Domain ownership must be explicit and feature-flagged.

