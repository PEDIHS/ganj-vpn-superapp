import assert from 'node:assert/strict';
import test from 'node:test';
import { createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createFreeAdminApplication } from '../src/free-admin-routes.js';
import { withFreeAccess } from '../src/free-access.js';
import { withFreePolicy } from '../src/free-policy.js';
import { withFreeServerRegistry } from '../src/free-server-registry.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T11:15:00.000Z');
const SECRET = 'test-device-secret-change-me';

function setup() {
  const repository = withFreeServerRegistry(withFreePolicy(withFreeAccess(new InMemoryRepository(createSeed(NOW)), { clock: () => new Date(NOW) }), { clock: () => new Date(NOW) }), { clock: () => new Date(NOW) });
  const auth = createTestAuthAdapter({ deviceSecrets: { [FIXTURES.devices.primary]: SECRET } });
  const app = createFreeAdminApplication({
    baseApplication: async () => ({ status: 404, body: { delegated: true } }),
    repository,
    auth,
    clock: () => new Date(NOW),
  });
  return { repository, app };
}

function request(path, { method = 'GET', scope = '', body } = {}) {
  const headers = {
    'x-test-user-id': FIXTURES.users.primary,
    'x-test-device-id': FIXTURES.devices.primary,
    'x-test-subject': 'admin:test',
    'x-test-scopes': scope,
  };
  if (body !== undefined) headers['content-type'] = 'application/json';
  return new Request(`https://control.ganj.test${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

test('Free admin endpoints require dedicated read/write scopes', async () => {
  const { app } = setup();
  const deniedRead = await app(request('/v1/admin/free/policy'));
  assert.equal(deniedRead.status, 403);
  assert.equal(deniedRead.body.error.code, 'insufficient_scope');
  const deniedWrite = await app(request('/v1/admin/free/policy', {
    method: 'PATCH', scope: 'admin:free:read', body: { maintenance: true, reason: 'scheduled maintenance' },
  }));
  assert.equal(deniedWrite.status, 403);
});

test('Free policy can be managed through scoped Control API and is audit logged', async () => {
  const { repository, app } = setup();
  const updated = await app(request('/v1/admin/free/policy', {
    method: 'PATCH', scope: 'admin:free:write',
    body: { maintenance: true, max_issues_per_device: 7, reason: 'scheduled maintenance window' },
  }));
  assert.equal(updated.status, 200);
  assert.equal(updated.body.data.maintenance, true);
  assert.equal(updated.body.data.max_issues_per_device, 7);
  const read = await app(request('/v1/admin/free/policy', { scope: 'admin:free:read' }));
  assert.equal(read.status, 200);
  assert.equal(read.body.data.maintenance, true);
  const audit = repository.listAdminAudit({ limit: 10 });
  assert.equal(audit[0].action, 'free_policy.update');
  assert.equal(audit[0].resourceType, 'free_access_policy');
  assert.match(audit[0].afterDigest, /^[a-f0-9]{64}$/);
});

test('Free Server Registry creates and lists safe metadata without returning secret reference', async () => {
  const { app } = setup();
  const created = await app(request('/v1/admin/free/servers', {
    method: 'POST', scope: 'admin:free:write', body: {
      code: 'tr-free-01', name: 'Turkey Free 01', country_code: 'TR', city: 'Istanbul',
      protocols: ['vless'], secret_ref: 'vault://ganj/free/tr-01', provider_ref: 'core-free',
      upstream_ref: 'istanbul-a', priority: 10, max_load_ratio: 0.8, max_active_profile_grants: 25,
      rollout_percent: 50, min_app_version: '1.2.0', reason: 'add Turkey free capacity',
    },
  }));
  assert.equal(created.status, 201);
  assert.equal(created.body.data.country_code, 'TR');
  assert.equal(created.body.data.secret_ref_configured, true);
  assert.equal(Object.hasOwn(created.body.data, 'secret_ref'), false);
  assert.equal(JSON.stringify(created.body).includes('vault://ganj/free/tr-01'), false);

  const listed = await app(request('/v1/admin/free/servers', { scope: 'admin:free:read' }));
  const server = listed.body.data.find((item) => item.id === created.body.data.id);
  assert.ok(server);
  assert.equal(server.provider_ref, 'core-free');
  assert.equal(Object.hasOwn(server, 'secret_ref'), false);
});

test('Free Server Registry updates immediately enforce status, emergency and load admission controls', async () => {
  const { repository, app } = setup();
  for (const patch of [
    { status: 'disabled' },
    { status: 'active', emergency_disabled: true },
    { emergency_disabled: false, load_ratio: 0.9, max_load_ratio: 0.5 },
  ]) {
    const result = await app(request(`/v1/admin/free/servers/${FIXTURES.servers.free}`, {
      method: 'PATCH', scope: 'admin:free:write', body: { ...patch, reason: 'exercise runtime server control' },
    }));
    assert.equal(result.status, 200);
    const visible = await repository.listServers();
    assert.equal(visible.some((item) => item.id === FIXTURES.servers.free), false);
  }
  const paid = await repository.findServer(FIXTURES.servers.premium);
  assert.equal(paid.id, FIXTURES.servers.premium);
});

test('Admin payload validation rejects raw connection fields and invalid Free protocol sets', async () => {
  const { app } = setup();
  const raw = await app(request('/v1/admin/free/servers', {
    method: 'POST', scope: 'admin:free:write', body: {
      code: 'bad-free', name: 'Bad Free', country_code: 'NL', protocols: ['vless'],
      secret_ref: 'vault://ganj/free/bad', credential: 'raw-secret', reason: 'must reject raw secret material',
    },
  }));
  assert.equal(raw.status, 400);
  assert.equal(raw.body.error.code, 'unsupported_fields');
  const protocol = await app(request('/v1/admin/free/servers', {
    method: 'POST', scope: 'admin:free:write', body: {
      code: 'bad-free-2', name: 'Bad Free 2', country_code: 'NL', protocols: ['trojan'],
      secret_ref: 'vault://ganj/free/bad2', reason: 'must keep current free protocol compatible',
    },
  }));
  assert.equal(protocol.status, 400);
});
