import assert from 'node:assert/strict';
import test from 'node:test';
import { createAdminAwareApplication } from '../src/admin-aware-application.js';
import { createRuntime, FIXTURES } from '../src/runtime.js';

const NOW = new Date('2026-08-28T11:00:00.000Z');

async function setup() {
  const runtime = await createRuntime({
    CONTROL_API_ADAPTER_MODE: 'test',
    NODE_ENV: 'test',
    CONTROL_API_TEST_DEVICE_SECRET: 'admin-test-device-secret',
  });
  return {
    runtime,
    app: createAdminAwareApplication(runtime, { clock: () => new Date(NOW) }),
  };
}

function request(app, method, path, { body, scope = 'admin:control-plane', authenticate = true } = {}) {
  const headers = new Headers();
  if (authenticate) {
    headers.set('x-test-user-id', FIXTURES.users.primary);
    headers.set('x-test-device-id', FIXTURES.devices.primary);
    if (scope) headers.set('x-test-scopes', scope);
  }
  if (body !== undefined) headers.set('content-type', 'application/json');
  return app(new Request(`http://control.test${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}

test('admin server catalog requires authenticated dedicated scope', async () => {
  const { app } = await setup();
  const anonymous = await request(app, 'GET', '/v1/admin/control-plane/servers', { authenticate: false });
  assert.equal(anonymous.status, 401);
  assert.equal(anonymous.body.error.code, 'unauthorized');

  const userOnly = await request(app, 'GET', '/v1/admin/control-plane/servers', { scope: '' });
  assert.equal(userOnly.status, 403);
  assert.equal(userOnly.body.error.code, 'admin_scope_required');
});

test('admin server catalog never exposes connection material or secret references', async () => {
  const { app } = await setup();
  const response = await request(app, 'GET', '/v1/admin/control-plane/servers');
  assert.equal(response.status, 200);
  assert.ok(response.body.data.length >= 4);
  const serialized = JSON.stringify(response.body);
  for (const forbidden of ['connection', 'credential', 'secret_ref', 'secretRef', 'vless://', 'vmess://', 'trojan://']) {
    assert.equal(serialized.includes(forbidden), false, `${forbidden} leaked from admin response`);
  }
  assert.equal(typeof response.body.data[0].secret_configured, 'boolean');
});

test('admin can create a maintenance server using only an opaque secret-store reference', async () => {
  const { app, runtime } = await setup();
  const created = await request(app, 'POST', '/v1/admin/control-plane/servers', {
    body: {
      code: 'tr-free-02',
      name: 'Türkiye Free 02',
      country_code: 'TR',
      city: 'Istanbul',
      tier: 'free',
      status: 'maintenance',
      load_ratio: 0,
      latency_hint_ms: null,
      protocols: ['vless'],
      secret_reference: 'vault:kv/data/ganj/servers/tr-free-02',
    },
  });
  assert.equal(created.status, 201);
  assert.equal(created.body.data.code, 'tr-free-02');
  assert.equal(created.body.data.secret_configured, true);
  assert.equal(JSON.stringify(created.body).includes('vault:kv/data/ganj/servers/tr-free-02'), false);

  const stored = await runtime.repository.findServer(created.body.data.id);
  assert.equal(stored.code, 'tr-free-02');
  const audit = await runtime.repository.listAdminAudit({ limit: 10 });
  assert.equal(audit[0].action, 'server.create');
});

test('admin can emergency-disable an existing server without changing its secret binding', async () => {
  const { app, runtime } = await setup();
  const before = await runtime.repository.findServer(FIXTURES.servers.free);
  const patched = await request(app, 'PATCH', `/v1/admin/control-plane/servers/${FIXTURES.servers.free}`, {
    body: { status: 'disabled', load_ratio: 0 },
  });
  assert.equal(patched.status, 200);
  assert.equal(patched.body.data.status, 'disabled');

  const after = await runtime.repository.findServer(FIXTURES.servers.free);
  assert.equal(after.status, 'disabled');
  assert.deepEqual(after.connection, before.connection);
  assert.equal(JSON.stringify(patched.body).includes('credential'), false);
});

test('server create and patch reject unsafe or unsupported operator input', async () => {
  const { app } = await setup();
  const raw = await request(app, 'POST', '/v1/admin/control-plane/servers', {
    body: {
      code: 'bad-secret-01', name: 'Bad Secret', country_code: 'NL', tier: 'free',
      protocols: ['vless'], secret_reference: 'vless://60000000-0000-4000-8000-000000000001@example.test:443',
    },
  });
  assert.equal(raw.status, 400);

  const externalUrl = await request(app, 'POST', '/v1/admin/control-plane/servers', {
    body: {
      code: 'bad-locator-02', name: 'Bad Locator', country_code: 'NL', tier: 'free',
      protocols: ['vless'], secret_reference: 'https://secrets.example.test/server',
    },
  });
  assert.equal(externalUrl.status, 400);
  assert.equal(externalUrl.body.error.code, 'invalid_secret_reference');

  const unknown = await request(app, 'PATCH', `/v1/admin/control-plane/servers/${FIXTURES.servers.free}`, {
    body: { endpoint: 'attacker.example', credential: 'do-not-accept' },
  });
  assert.equal(unknown.status, 400);
  assert.equal(unknown.body.error.code, 'unsupported_fields');

  const invalidProtocols = await request(app, 'PATCH', `/v1/admin/control-plane/servers/${FIXTURES.servers.free}`, {
    body: { protocols: ['vless', 'vless'] },
  });
  assert.equal(invalidProtocols.status, 400);
  assert.equal(invalidProtocols.body.error.code, 'invalid_protocols');
});

test('unknown admin resources use a safe 404 envelope', async () => {
  const { app } = await setup();
  const response = await request(app, 'GET', '/v1/admin/control-plane/not-a-resource');
  assert.equal(response.status, 404);
  assert.equal(response.body.error.code, 'route_not_found');
});
