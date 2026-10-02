import test from 'node:test';
import assert from 'node:assert/strict';
import { LegacyPostgresRepository } from '../src/adapters/legacy-postgres.js';

test('legacy postgres forwards canonical audit fields without dropping resource identity', async () => {
  let observed = null;
  const base = {
    database() { return { query: async () => ({ rowCount: 0, rows: [] }) }; },
    transaction(work) { return work(); },
    appendAdminAudit(value) { observed = value; return value; },
  };
  const repository = new LegacyPostgresRepository(base);
  const value = {
    actorSubject: 'system:legacy-reconciler',
    action: 'legacy_service_created',
    resourceType: 'service',
    resourceId: '33333333-3333-4333-8333-333333333333',
    reason: 'legacy_sync:ganj-bot-primary',
    requestId: '44444444-4444-4444-8444-444444444444',
    beforeDigest: null,
    afterDigest: null,
    outcome: 'success',
    createdAt: '2026-10-02T18:00:00.000Z',
  };

  await repository.appendAdminAudit(value);

  assert.deepEqual(observed, value);
  assert.equal(observed.resourceType, 'service');
  assert.equal(observed.resourceId, value.resourceId);
});
