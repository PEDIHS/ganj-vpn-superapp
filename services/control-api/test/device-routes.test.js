import assert from 'node:assert/strict';
import test from 'node:test';
import { createDeviceRouter } from '../src/device-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  deviceId: '20000000-0000-4000-8000-000000000001',
};

function request(path, method = 'GET') {
  return new Request(`https://api.example.invalid${path}`, {
    method,
    headers: { authorization: 'Bearer opaque-test-token' },
  });
}

function routerWithDatabase(database, transaction = async (work) => work()) {
  return createDeviceRouter({
    auth: { async authenticate() { return principal; } },
    repository: {
      kind: 'postgres-v1',
      database() { return database; },
      transaction,
    },
    clock: () => new Date('2026-08-30T08:00:00Z'),
  });
}

test('GET /v1/me/devices is owner scoped and marks current device', async () => {
  const rows = [
    {
      id: principal.deviceId,
      status: 'active',
      last_seen_at: new Date('2026-08-30T07:59:00Z'),
      created_at: new Date('2026-08-29T08:00:00Z'),
    },
    {
      id: '20000000-0000-4000-8000-000000000002',
      status: 'revoked',
      last_seen_at: null,
      created_at: new Date('2026-08-28T08:00:00Z'),
    },
  ];
  const route = routerWithDatabase({
    async query(sql, params) {
      assert.match(sql, /FROM control_devices/);
      assert.deepEqual(params, [principal.userId, principal.deviceId]);
      return { rowCount: rows.length, rows };
    },
  });
  const response = await route({
    request: request('/v1/me/devices'),
    url: new URL('https://api.example.invalid/v1/me/devices'),
    requestId: '90000000-0000-4000-8000-000000000010',
  });

  assert.equal(response.status, 200);
  assert.equal(response.body.data.length, 2);
  assert.deepEqual(response.body.data[0], {
    id: principal.deviceId,
    platform: 'android',
    name: null,
    app_version: null,
    status: 'active',
    current: true,
    last_seen_at: '2026-08-30T07:59:00.000Z',
  });
  assert.equal(response.body.data[1].current, false);
});

test('DELETE rejects revoking the current device', async () => {
  let touched = false;
  const route = routerWithDatabase({ async query() { touched = true; return { rowCount: 0, rows: [] }; } });
  await assert.rejects(
    route({
      request: request(`/v1/me/devices/${principal.deviceId}`, 'DELETE'),
      url: new URL(`https://api.example.invalid/v1/me/devices/${principal.deviceId}`),
      requestId: '90000000-0000-4000-8000-000000000011',
    }),
    (error) => error?.status === 409 && error?.code === 'cannot_revoke_current_device',
  );
  assert.equal(touched, false);
});

test('DELETE revokes owned device sessions and service bindings atomically', async () => {
  const target = '20000000-0000-4000-8000-000000000002';
  const calls = [];
  let transactionCalls = 0;
  const database = {
    async query(sql, params) {
      calls.push({ sql, params });
      if (/SELECT id, status/.test(sql)) {
        return { rowCount: 1, rows: [{ id: target, status: 'active' }] };
      }
      return { rowCount: 1, rows: [] };
    },
  };
  const route = routerWithDatabase(database, async (work) => {
    transactionCalls += 1;
    return work();
  });
  const response = await route({
    request: request(`/v1/me/devices/${target}`, 'DELETE'),
    url: new URL(`https://api.example.invalid/v1/me/devices/${target}`),
    requestId: '90000000-0000-4000-8000-000000000012',
  });

  assert.equal(response.status, 204);
  assert.equal(transactionCalls, 1);
  assert.equal(calls.length, 4);
  assert.match(calls[1].sql, /UPDATE control_devices/);
  assert.match(calls[2].sql, /UPDATE control_auth_sessions/);
  assert.match(calls[3].sql, /DELETE FROM control_service_devices/);
  for (const call of calls) assert.deepEqual(call.params, [principal.userId, target]);
});
