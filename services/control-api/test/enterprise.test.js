import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import test from 'node:test';
import { createApplication } from '../src/application.js';
import { createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createTestPurchaseVerifier } from '../src/adapters/test-purchase-verifier.js';
import { createTestTelegramAuthAdapter } from '../src/adapters/test-telegram-auth.js';
import { EnterpriseSecurity, createTestEnterpriseSecurity } from '../src/enterprise.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-24T12:00:00.000Z');
const IDS = Object.freeze({
  batch: '70000000-0000-4000-8000-000000000001',
  event: '70000000-0000-4000-8000-000000000002',
  bug: '70000000-0000-4000-8000-000000000003',
  ticket: '70000000-0000-4000-8000-000000000004',
  diagnostic: '70000000-0000-4000-8000-000000000005',
});

function setup() {
  const repository = new InMemoryRepository(createSeed(NOW));
  const auth = createTestAuthAdapter({
    deviceSecrets: {
      [FIXTURES.devices.primary]: 'primary-secret',
      [FIXTURES.devices.secondary]: 'secondary-secret',
    },
  });
  const app = createApplication({
    repository,
    auth,
    purchaseVerifier: createTestPurchaseVerifier({ approvedTokens: {} }),
    telegramAuth: createTestTelegramAuthAdapter(),
    playNotifications: { kind: 'test-only', async verifyAndDecode() { throw new Error('unused'); } },
    enterpriseSecurity: createTestEnterpriseSecurity(),
    clock: () => new Date(NOW),
  });
  return { app, repository };
}

function request(app, method, path, { body, userId = FIXTURES.users.primary, deviceId = FIXTURES.devices.primary, scopes = '', subject, headers = {} } = {}) {
  const requestHeaders = new Headers({
    'x-test-user-id': userId,
    'x-test-device-id': deviceId,
    'x-test-scopes': scopes,
    'x-app-version': '3.2.1',
    'x-platform': 'android',
    'accept-language': 'fa-IR',
    ...headers,
  });
  if (subject) requestHeaders.set('x-test-subject', subject);
  if (body !== undefined) requestHeaders.set('content-type', 'application/json');
  return app(new Request(`http://control.test${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}

test('enterprise security produces stable version-scoped cohorts and verifiable Ed25519 signatures', () => {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const security = new EnterpriseSecurity({ assignmentSecret: Buffer.alloc(32, 9), signingPrivateKey: privateKey });
  const first = security.assign({ subject: 'device:1', flagKey: 'smart.home:v3', rolloutBasisPoints: 5000 });
  const second = security.assign({ subject: 'device:1', flagKey: 'smart.home:v3', rolloutBasisPoints: 5000 });
  const nextVersion = security.assign({ subject: 'device:1', flagKey: 'smart.home:v4', rolloutBasisPoints: 5000 });
  assert.deepEqual(first, second);
  assert.notEqual(first.assignmentId, nextVersion.assignmentId);
  assert.equal(security.assign({ subject: 'device:1', flagKey: 'always.off:v1', rolloutBasisPoints: 0 }).selected, false);
  assert.equal(security.assign({ subject: 'device:1', flagKey: 'always.on:v1', rolloutBasisPoints: 10_000 }).selected, true);
  const payload = { values: { 'home.title': 'Ganj' }, version: 3 };
  assert.equal(verify(null, Buffer.from(JSON.stringify(payload)), publicKey, Buffer.from(security.sign(payload), 'base64')), true);
  assert.throws(() => new EnterpriseSecurity({ assignmentSecret: Buffer.alloc(8), signingPrivateKey: privateKey }), /incomplete/);
});

async function createAndPublishConfig(app) {
  const created = await request(app, 'POST', '/v1/admin/remote-config/releases', {
    scopes: 'admin:config:write', subject: 'admin:maker',
    body: {
      environment: 'production', version: 7, reason: 'Ship a targeted welcome campaign',
      entries: [
        { key: 'home.banner.title', value: 'Premium summer', sensitivity: 'public', target: { tiers: ['premium'], locales: ['fa-IR'], platforms: ['android'], min_app_version: '3.0.0' } },
        { key: 'operations.note', value: 'internal-only', sensitivity: 'internal', target: {} },
      ],
    },
  });
  assert.equal(created.status, 201);
  const published = await request(app, 'POST', `/v1/admin/remote-config/releases/${created.body.data.id}/publish`, {
    scopes: 'admin:config:publish', subject: 'admin:publisher', body: { reason: 'Reviewed by the release manager' },
  });
  assert.equal(published.status, 200);
  return created.body.data.id;
}

test('runtime config returns only applicable public values, signed deterministic assignments, and ETag revalidation', async () => {
  const { app } = setup();
  await createAndPublishConfig(app);
  const flag = await request(app, 'PUT', '/v1/admin/feature-flags/smart.home', {
    scopes: 'admin:flags:write', subject: 'admin:flags',
    body: {
      enabled: true, default_variant: 'liquid', rollout_percent: 100,
      audience: { tiers: ['premium'], locales: ['fa-IR'], platforms: ['android'], min_app_version: '3.0.0' },
      experiment_key: 'home-liquid-2026', reason: 'Gradual Android home experience rollout',
    },
  });
  assert.equal(flag.status, 200);

  const first = await request(app, 'GET', '/v1/app/runtime-config');
  assert.equal(first.status, 200);
  assert.deepEqual(first.body.data.values, { 'home.banner.title': 'Premium summer' });
  assert.equal(first.body.data.feature_assignments[0].enabled, true);
  assert.equal(first.body.data.feature_assignments[0].variant, 'liquid');
  assert.match(first.body.data.feature_assignments[0].assignment_id, /^[0-9a-f-]{36}$/);
  assert.match(first.body.data.signature, /^[A-Za-z0-9+/]+=*$/);
  assert.equal(JSON.stringify(first.body).includes('internal-only'), false);
  assert.equal(JSON.stringify(first.body).includes('config://'), false);

  const cached = await request(app, 'GET', '/v1/app/runtime-config', { headers: { 'if-none-match': first.headers.etag } });
  assert.equal(cached.status, 304);
  assert.equal(cached.body, null);

  const wrongAudience = await request(app, 'GET', '/v1/app/runtime-config', {
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary,
  });
  assert.deepEqual(wrongAudience.body.data.values, {});
  assert.equal(wrongAudience.body.data.feature_assignments[0].enabled, false);
});

test('admin mutations require scopes, prohibit VPN material, require production dual control, and append immutable audit records', async () => {
  const { app } = setup();
  const denied = await request(app, 'POST', '/v1/admin/remote-config/releases', { body: {} });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'insufficient_scope');

  const forbidden = await request(app, 'POST', '/v1/admin/remote-config/releases', {
    scopes: 'admin:config:write', subject: 'admin:maker',
    body: {
      environment: 'staging', version: 1, reason: 'Attempt a prohibited manual configuration key',
      entries: [{ key: 'vpn.manual_config', value: 'vless://private', sensitivity: 'public', target: {} }],
    },
  });
  assert.equal(forbidden.status, 400);
  assert.equal(forbidden.body.error.code, 'invalid_config_key');

  const id = await createAndPublishConfig(app);
  const sameActorDraft = await request(app, 'POST', '/v1/admin/remote-config/releases', {
    scopes: 'admin:config:write', subject: 'admin:one',
    body: { environment: 'production', version: 8, reason: 'Second production configuration release', entries: [{ key: 'home.copy', value: 'Hello', sensitivity: 'public', target: {} }] },
  });
  const sameActorPublish = await request(app, 'POST', `/v1/admin/remote-config/releases/${sameActorDraft.body.data.id}/publish`, {
    scopes: 'admin:config:publish', subject: 'admin:one', body: { reason: 'Self approval must not be allowed' },
  });
  assert.equal(sameActorPublish.status, 409);
  assert.equal(sameActorPublish.body.error.code, 'dual_control_required');

  const audit = await request(app, 'GET', '/v1/admin/audit-log', { scopes: 'admin:audit:read' });
  assert.equal(audit.status, 200);
  assert.equal(audit.body.data.some((item) => item.resource_id === id && item.action === 'remote_config.publish'), true);
  assert.equal(audit.body.data.every((item) => !('after_digest' in item)), true);
});

test('analytics ingestion requires owned active consent, enforces event/property allowlists, and is idempotent', async () => {
  const { app } = setup();
  const body = {
    batch_id: IDS.batch,
    consent_receipt_id: FIXTURES.consents.analytics,
    sent_at: NOW.toISOString(),
    events: [{
      event_id: IDS.event, name: 'vpn.connect_succeeded', occurred_at: NOW.toISOString(), schema_version: 1,
      properties: { tier: 'premium', network_type: 'wifi', selection_method: 'smart', latency_bucket: '50-99' },
    }],
  };
  const accepted = await request(app, 'POST', '/v1/telemetry/events:batch', { body });
  assert.equal(accepted.status, 202);
  assert.equal(accepted.body.data.accepted_count, 1);
  const replay = await request(app, 'POST', '/v1/telemetry/events:batch', { body });
  assert.equal(replay.body.data.replayed, true);
  assert.equal(replay.body.data.duplicate_count, 1);

  const conflict = await request(app, 'POST', '/v1/telemetry/events:batch', {
    body: { ...body, events: [{ ...body.events[0], properties: { tier: 'vip' } }] },
  });
  assert.equal(conflict.status, 409);
  assert.equal(conflict.body.error.code, 'analytics_batch_conflict');

  const leaked = await request(app, 'POST', '/v1/telemetry/events:batch', {
    body: { ...body, batch_id: '70000000-0000-4000-8000-000000000010', events: [{ ...body.events[0], event_id: '70000000-0000-4000-8000-000000000011', properties: { endpoint: 'example.test' } }] },
  });
  assert.equal(leaked.status, 400);
  assert.equal(leaked.body.error.code, 'unsupported_analytics_properties');

  const unconsented = await request(app, 'POST', '/v1/telemetry/events:batch', {
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, body,
  });
  assert.equal(unconsented.status, 403);
  assert.equal(unconsented.body.error.code, 'analytics_consent_required');
});

test('withdrawing analytics consent immediately blocks future batches', async () => {
  const { app } = setup();
  const revoked = await request(app, 'PUT', '/v1/me/consents', {
    body: { purpose: 'product_analytics', granted: false, policy_version: '2026-08' },
  });
  assert.equal(revoked.status, 204);
  const list = await request(app, 'GET', '/v1/me/consents');
  assert.equal(list.body.data.find((item) => item.purpose === 'product_analytics').granted, false);

  const response = await request(app, 'POST', '/v1/telemetry/events:batch', {
    body: {
      batch_id: IDS.batch, consent_receipt_id: FIXTURES.consents.analytics, sent_at: NOW.toISOString(),
      events: [{ event_id: IDS.event, name: 'app.opened', occurred_at: NOW.toISOString(), schema_version: 1, properties: { app_version: '3.2.1' } }],
    },
  });
  assert.equal(response.status, 403);
});

test('bug intake is device-owned, severity-derived, idempotent, and rejects pasted private configs', async () => {
  const { app } = setup();
  const body = {
    client_report_id: IDS.bug, title: 'Connection always fails', description: 'The connection fails after the loading state.',
    category: 'connection', device_id: FIXTURES.devices.primary, occurred_at: NOW.toISOString(), automatic_context_consent: true,
    context: { app_version: '3.2.1', os_version: '16', connection_state: 'connecting', network_type: 'wifi', error_code: 'TIMEOUT' },
  };
  const created = await request(app, 'POST', '/v1/reliability/bug-reports', { body });
  assert.equal(created.status, 201);
  assert.equal(created.body.data.severity, 'high');
  const replay = await request(app, 'POST', '/v1/reliability/bug-reports', { body });
  assert.equal(replay.status, 200);
  assert.equal(replay.body.data.id, created.body.data.id);
  const list = await request(app, 'GET', '/v1/reliability/bug-reports');
  assert.deepEqual(list.body.data.map((item) => item.id), [created.body.data.id]);

  const wrongDevice = await request(app, 'POST', '/v1/reliability/bug-reports', { body: { ...body, client_report_id: '70000000-0000-4000-8000-000000000020', device_id: FIXTURES.devices.secondary } });
  assert.equal(wrongDevice.status, 403);
  const privateMaterial = await request(app, 'POST', '/v1/reliability/bug-reports', { body: { ...body, client_report_id: '70000000-0000-4000-8000-000000000021', description: 'Please import vless://private-server-token to reproduce.' } });
  assert.equal(privateMaterial.status, 400);
  assert.equal(privateMaterial.body.error.code, 'sensitive_content_rejected');
});

test('support and diagnostic intake enforce ownership, consent, bounded fields, retention, and replay safety', async () => {
  const { app } = setup();
  const ticketBody = {
    client_ticket_id: IDS.ticket, category: 'connection', priority: 'normal',
    subject: 'Connection assistance', body: 'Please help diagnose repeated connection timeouts.',
  };
  const ticket = await request(app, 'POST', '/v1/support/tickets', { body: ticketBody });
  assert.equal(ticket.status, 201);
  const diagnosticBody = {
    client_report_id: IDS.diagnostic, device_id: FIXTURES.devices.primary,
    support_ticket_id: ticket.body.data.id, explicit_consent: true,
    started_at: new Date(NOW.getTime() - 10_000).toISOString(), completed_at: NOW.toISOString(),
    tests: [
      { kind: 'network', outcome: 'passed', latency_ms: 72, packet_loss_ratio: 0.01, result_code: 'OK' },
      { kind: 'dns_resolver', outcome: 'warning', result_code: 'DNS_CLASS_TIMEOUT' },
    ],
  };
  const diagnostic = await request(app, 'POST', '/v1/diagnostics/reports', { body: diagnosticBody });
  assert.equal(diagnostic.status, 201);
  assert.equal(Date.parse(diagnostic.body.data.expires_at) - NOW.getTime(), 30 * 86_400_000);
  assert.equal(diagnostic.body.data.redaction_version, 'diagnostics-v1');
  const replay = await request(app, 'POST', '/v1/diagnostics/reports', { body: diagnosticBody });
  assert.equal(replay.status, 200);
  assert.equal(replay.body.data.id, diagnostic.body.data.id);

  const rawDestination = await request(app, 'POST', '/v1/diagnostics/reports', {
    body: { ...diagnosticBody, client_report_id: '70000000-0000-4000-8000-000000000030', tests: [{ kind: 'server_ping', outcome: 'passed', hostname: 'vpn.example.test' }] },
  });
  assert.equal(rawDestination.status, 400);
  assert.equal(rawDestination.body.error.code, 'unsupported_fields');

  const unownedLink = await request(app, 'POST', '/v1/diagnostics/reports', {
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary,
    body: { ...diagnosticBody, client_report_id: '70000000-0000-4000-8000-000000000031', device_id: FIXTURES.devices.secondary },
  });
  assert.equal(unownedLink.status, 404);
  assert.equal(unownedLink.body.error.code, 'support_ticket_not_found');
});
