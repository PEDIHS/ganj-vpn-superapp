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


test('legacy postgres keeps compatibility with the legacy audit shape', async () => {
  let observed = null;
  const base = {
    database() { return { query: async () => ({ rowCount: 0, rows: [] }) }; },
    transaction(work) { return work(); },
    appendAdminAudit(value) { observed = value; return value; },
  };
  const repository = new LegacyPostgresRepository(base);

  await repository.appendAdminAudit({
    actorType: 'system',
    actorId: 'legacy-reconciler',
    action: 'legacy_service_created',
    targetType: 'service',
    targetId: '33333333-3333-4333-8333-333333333333',
    metadata: { external_service_digest: 'a'.repeat(64) },
    occurredAt: '2026-10-02T18:00:00.000Z',
  });

  assert.equal(observed.actorSubject, 'system:legacy-reconciler');
  assert.equal(observed.resourceType, 'service');
  assert.equal(observed.resourceId, '33333333-3333-4333-8333-333333333333');
  assert.equal(observed.afterDigest, 'a'.repeat(64));
  assert.equal(observed.outcome, 'success');
  assert.match(observed.requestId, /^[0-9a-f-]{36}$/);
});

test('legacy postgres repository covers ownership plan and source-run persistence ports', async () => {
  const queries = [];
  const locks = [];
  const base = {
    database() {
      return {
        query: async (sql, args = []) => {
          queries.push({ sql, args });
          if (sql.includes('FROM control_users')) {
            return { rowCount: 1, rows: [{ id: '11111111-1111-4111-8111-111111111111' }] };
          }
          if (sql.includes('FROM control_plans')) {
            return { rowCount: 1, rows: [{ id: '22222222-2222-4222-8222-222222222222', tier: 'vip' }] };
          }
          if (sql.includes('INSERT INTO control_legacy_sources')) {
            return { rowCount: 1, rows: [{ checkpoint_cursor: 'cursor-1', enabled: true, read_owner: 'control-api' }] };
          }
          return { rowCount: 1, rows: [] };
        },
      };
    },
    transaction(work) { return work(); },
    advisoryLock: async (...args) => { locks.push(args); },
    findOwnedService: async (userId, id) => ({ userId, id }),
    saveService: async (value) => ({ ...value, saved: true }),
    createService: async (value) => ({ ...value, created: true }),
    appendAdminAudit(value) { return value; },
  };
  const repository = new LegacyPostgresRepository(base);

  assert.equal(await repository.transaction(async () => 'tx-ok'), 'tx-ok');
  assert.deepEqual(await repository.findOwnedService('u', 's'), { userId: 'u', id: 's' });
  assert.equal((await repository.saveService({ id: 's' })).saved, true);
  assert.equal((await repository.createService({ id: 's' })).created, true);
  assert.deepEqual(
    await repository.findUserByTelegramSubject('1234'),
    { id: '11111111-1111-4111-8111-111111111111' },
  );
  assert.deepEqual(
    await repository.findPlanByCode('6d0d'),
    { id: '22222222-2222-4222-8222-222222222222', tier: 'vip' },
  );
  assert.deepEqual(
    await repository.beginLegacySourceRun({ sourceKey: 'ganj-bot-primary', startedAt: '2026-10-02T18:00:00.000Z' }),
    { checkpointCursor: 'cursor-1', readOwner: 'control-api' },
  );
  await repository.completeLegacySourceRun({
    sourceKey: 'ganj-bot-primary', checkpointCursor: 'cursor-2', completedAt: '2026-10-02T18:01:00.000Z', clearError: true,
  });
  await repository.failLegacySourceRun({
    sourceKey: 'ganj-bot-primary', errorCode: 'source_failure', completedAt: '2026-10-02T18:02:00.000Z',
  });
  await repository.recordLegacyWorkerFailure({
    sourceKey: 'ganj-bot-primary', eventId: 'event-1', errorCode: 'item_failure', failedAt: '2026-10-02T18:03:00.000Z',
  });
  await repository.recordLegacyWorkerFailure({
    sourceKey: 'ganj-bot-primary', eventId: null, errorCode: 'ignored', failedAt: '2026-10-02T18:03:00.000Z',
  });

  assert.deepEqual(locks[0], ['legacy-source-run', 'ganj-bot-primary']);
  assert.ok(queries.length >= 6);
});
