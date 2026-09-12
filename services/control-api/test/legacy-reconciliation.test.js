import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../src/errors.js';
import { LegacySubscriptionReconciler, normalizeLegacySubscriptionEvent } from '../src/legacy-reconciliation.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const PLAN_ID = '30000000-0000-4000-8000-000000000002';
const SERVICE_ID = '40000000-0000-4000-8000-000000000099';
const NOW = new Date('2026-08-28T05:00:00.000Z');

function event(overrides = {}) {
  return {
    source_key: 'ganj-primary-bot',
    event_id: 'evt-001',
    external_customer_id: 'tg-customer-42',
    telegram_subject: 'telegram-subject-42',
    external_service_id: 'legacy-service-9001',
    plan_code: 'premium-30d',
    display_name: 'Legacy Premium',
    status: 'active',
    expires_at: '2026-09-27T05:00:00.000Z',
    traffic_limit_bytes: 107374182400,
    traffic_used_bytes: 3221225472,
    device_limit: 2,
    allowed_protocols: ['trojan', 'vless'],
    source_updated_at: '2026-08-28T04:59:00.000Z',
    ...overrides,
  };
}

class FakeLegacyRepository {
  constructor() {
    this.users = new Map([['telegram-subject-42', { id: USER_ID }]]);
    this.plans = new Map([['premium-30d', { id: PLAN_ID, tier: 'premium' }]]);
    this.services = new Map();
    this.events = new Map();
    this.customerMappings = new Map();
    this.projections = new Map();
    this.conflicts = new Map();
    this.audit = [];
    this.createCount = 0;
    this.updateCount = 0;
  }

  async transaction(work) { return work(); }

  eventKey(sourceKey, eventId) { return `${sourceKey}:${eventId}`; }
  mappingKey(sourceKey, externalCustomerId) { return `${sourceKey}:${externalCustomerId}`; }
  projectionKey(sourceKey, externalServiceId) { return `${sourceKey}:${externalServiceId}`; }

  async findLegacyReconciliationEvent(sourceKey, eventId) {
    return structuredClone(this.events.get(this.eventKey(sourceKey, eventId)) ?? null);
  }

  async recordLegacyReconciliationEvent(value) {
    this.events.set(this.eventKey(value.sourceKey, value.eventId), {
      ...structuredClone(value),
      processingStatus: 'received',
      controlServiceId: null,
      errorCode: null,
    });
  }

  async completeLegacyReconciliationEvent(value) {
    const key = this.eventKey(value.sourceKey, value.eventId);
    const current = this.events.get(key);
    this.events.set(key, { ...current, ...structuredClone(value) });
  }

  async findUserByTelegramSubject(subject) {
    return structuredClone(this.users.get(subject) ?? null);
  }

  async findLegacyCustomerMapping(sourceKey, externalCustomerId) {
    return structuredClone(this.customerMappings.get(this.mappingKey(sourceKey, externalCustomerId)) ?? null);
  }

  async saveLegacyCustomerMapping(value) {
    this.customerMappings.set(this.mappingKey(value.sourceKey, value.externalCustomerId), structuredClone(value));
  }

  async findPlanByCode(code) { return structuredClone(this.plans.get(code) ?? null); }

  async findLegacyServiceProjection(sourceKey, externalServiceId) {
    return structuredClone(this.projections.get(this.projectionKey(sourceKey, externalServiceId)) ?? null);
  }

  async touchLegacyServiceProjection({ sourceKey, externalServiceId, lastSeenAt }) {
    const key = this.projectionKey(sourceKey, externalServiceId);
    this.projections.set(key, { ...this.projections.get(key), lastSeenAt });
  }

  async saveLegacyServiceProjection(value) {
    this.projections.set(this.projectionKey(value.sourceKey, value.externalServiceId), structuredClone(value));
  }

  async findOwnedService(userId, id) {
    const value = this.services.get(id);
    return value?.userId === userId ? structuredClone(value) : null;
  }

  async createService(value) {
    this.createCount += 1;
    const stored = structuredClone(value);
    this.services.set(stored.id, stored);
    return structuredClone(stored);
  }

  async saveService(value) {
    this.updateCount += 1;
    this.services.set(value.id, structuredClone(value));
    return structuredClone(value);
  }

  async upsertLegacyConflict(value) {
    const key = `${value.sourceKey}:${value.entityType}:${value.externalEntityId}:${value.conflictCode}`;
    const prior = this.conflicts.get(key);
    this.conflicts.set(key, {
      ...structuredClone(value),
      id: prior?.id ?? value.id,
      firstSeenAt: prior?.firstSeenAt ?? value.lastSeenAt,
    });
  }

  async appendAdminAudit(value) { this.audit.push(structuredClone(value)); }
}

function reconciler(repository, ids = [SERVICE_ID, '70000000-0000-4000-8000-000000000001']) {
  let index = 0;
  return new LegacySubscriptionReconciler({
    repository,
    now: () => new Date(NOW),
    ids: () => ids[index++] ?? `70000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
  });
}

test('normalization expires stale active services and ignores unknown raw config fields', () => {
  const normalized = normalizeLegacySubscriptionEvent(event({
    expires_at: '2026-08-27T05:00:00.000Z',
    raw_vpn_uri: 'vless://must-not-survive',
    credential: 'must-not-survive',
  }), NOW);

  assert.equal(normalized.status, 'expired');
  assert.equal('raw_vpn_uri' in normalized, false);
  assert.equal('credential' in normalized, false);
  assert.deepEqual(normalized.allowedProtocols, ['trojan', 'vless']);
});

test('active legacy service creates exactly one projected entitlement', async () => {
  const repository = new FakeLegacyRepository();
  const result = await reconciler(repository).reconcile(event());

  assert.deepEqual(result, { outcome: 'applied', replay: false, serviceId: SERVICE_ID });
  assert.equal(repository.createCount, 1);
  assert.equal(repository.updateCount, 0);
  assert.equal(repository.services.size, 1);
  const service = repository.services.get(SERVICE_ID);
  assert.equal(service.userId, USER_ID);
  assert.equal(service.planId, PLAN_ID);
  assert.equal(service.status, 'active');
  assert.deepEqual(service.allowed_protocols, ['trojan', 'vless']);
  assert.equal(repository.projections.size, 1);
  assert.equal(repository.audit.length, 1);
  assert.equal(JSON.stringify(repository.audit).includes('legacy-service-9001'), false);
});

test('same event replay is idempotent and never duplicates service', async () => {
  const repository = new FakeLegacyRepository();
  const service = reconciler(repository);

  const first = await service.reconcile(event());
  const replay = await service.reconcile(event());

  assert.equal(first.outcome, 'applied');
  assert.deepEqual(replay, { outcome: 'applied', replay: true, serviceId: SERVICE_ID });
  assert.equal(repository.createCount, 1);
  assert.equal(repository.services.size, 1);
});

test('new event with unchanged source fingerprint touches projection without service write', async () => {
  const repository = new FakeLegacyRepository();
  const service = reconciler(repository);
  await service.reconcile(event());

  const unchanged = await service.reconcile(event({ event_id: 'evt-002' }));

  assert.deepEqual(unchanged, { outcome: 'unchanged', replay: false, serviceId: SERVICE_ID });
  assert.equal(repository.createCount, 1);
  assert.equal(repository.updateCount, 0);
  assert.equal(repository.services.size, 1);
});

test('renewal updates the same entitlement instead of creating a second one', async () => {
  const repository = new FakeLegacyRepository();
  const service = reconciler(repository);
  await service.reconcile(event());

  const renewal = await service.reconcile(event({
    event_id: 'evt-renewal',
    expires_at: '2026-10-27T05:00:00.000Z',
    traffic_used_bytes: 0,
    source_updated_at: '2026-08-28T05:01:00.000Z',
  }));

  assert.equal(renewal.outcome, 'applied');
  assert.equal(renewal.serviceId, SERVICE_ID);
  assert.equal(repository.createCount, 1);
  assert.equal(repository.updateCount, 1);
  assert.equal(repository.services.size, 1);
  assert.equal(repository.services.get(SERVICE_ID).expires_at, '2026-10-27T05:00:00.000Z');
});

test('unlinked Telegram customer becomes an operator conflict without entitlement mutation', async () => {
  const repository = new FakeLegacyRepository();
  repository.users.clear();

  const result = await reconciler(repository).reconcile(event());

  assert.equal(result.outcome, 'conflict');
  assert.equal(result.conflictCode, 'legacy_customer_unlinked');
  assert.equal(repository.services.size, 0);
  assert.equal(repository.conflicts.size, 1);
  const detail = [...repository.conflicts.values()][0].detail;
  assert.equal('telegram_subject' in detail, false);
  assert.equal(typeof detail.telegram_subject_digest, 'string');
});

test('an unlinked-customer conflict is retried after Telegram linking without a new event id', async () => {
  const repository = new FakeLegacyRepository();
  repository.users.clear();
  const service = reconciler(repository);

  const first = await service.reconcile(event());
  assert.equal(first.conflictCode, 'legacy_customer_unlinked');
  repository.users.set('telegram-subject-42', { id: USER_ID });

  const retried = await service.reconcile(event());
  assert.equal(retried.outcome, 'applied');
  assert.equal(retried.replay, false);
  assert.equal(repository.services.size, 1);
});

test('a missing-plan conflict is retried after an operator adds the plan mapping', async () => {
  const repository = new FakeLegacyRepository();
  repository.plans.clear();
  const service = reconciler(repository);

  const first = await service.reconcile(event());
  assert.equal(first.conflictCode, 'legacy_plan_mapping_missing');
  repository.plans.set('premium-30d', { id: PLAN_ID, tier: 'premium' });

  const retried = await service.reconcile(event());
  assert.equal(retried.outcome, 'applied');
  assert.equal(retried.replay, false);
  assert.equal(repository.services.size, 1);
});

test('existing customer mapping to another user is not silently overwritten', async () => {
  const repository = new FakeLegacyRepository();
  repository.customerMappings.set('ganj-primary-bot:tg-customer-42', {
    sourceKey: 'ganj-primary-bot',
    externalCustomerId: 'tg-customer-42',
    userId: '10000000-0000-4000-8000-000000000099',
  });

  const result = await reconciler(repository).reconcile(event());

  assert.equal(result.outcome, 'conflict');
  assert.equal(result.conflictCode, 'legacy_customer_owner_conflict');
  assert.equal(repository.services.size, 0);
});

test('missing plan mapping is persisted as conflict instead of guessed', async () => {
  const repository = new FakeLegacyRepository();
  repository.plans.clear();

  const result = await reconciler(repository).reconcile(event());

  assert.equal(result.outcome, 'conflict');
  assert.equal(result.conflictCode, 'legacy_plan_mapping_missing');
  assert.equal(repository.services.size, 0);
});

test('event id reuse with changed content is rejected', async () => {
  const repository = new FakeLegacyRepository();
  const service = reconciler(repository);
  await service.reconcile(event());

  await assert.rejects(
    () => service.reconcile(event({ traffic_used_bytes: 1234 })),
    (error) => error instanceof ApiError && error.status === 409 && error.code === 'legacy_event_replay_conflict',
  );
  assert.equal(repository.services.size, 1);
  assert.equal(repository.createCount, 1);
});
