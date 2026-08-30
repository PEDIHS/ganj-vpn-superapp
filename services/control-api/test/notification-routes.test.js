import assert from 'node:assert/strict';
import test from 'node:test';
import { createNotificationRouter } from '../src/notification-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  deviceId: '20000000-0000-4000-8000-000000000001',
};
const requestId = '90000000-0000-4000-8000-000000000001';

function request(path, { method = 'GET', body } = {}) {
  return new Request(`https://api.example.invalid${path}`, {
    method,
    headers: {
      authorization: 'Bearer opaque-test-token',
      ...(body ? { 'content-type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
}

function routerWithDatabase(database) {
  return createNotificationRouter({
    auth: { async authenticate() { return principal; } },
    repository: { kind: 'postgres-v1', database() { return database; } },
    parseBody: async (incoming) => incoming.json(),
    clock: () => new Date('2026-08-30T09:00:00Z'),
  });
}

test('GET /v1/notifications is owner scoped and returns unread count', async () => {
  const calls = [];
  const database = {
    async query(sql, params) {
      calls.push({ sql, params });
      if (/count\(\*\)/.test(sql)) return { rowCount: 1, rows: [{ count: 1 }] };
      return {
        rowCount: 2,
        rows: [
          {
            id: '70000000-0000-4000-8000-000000000002',
            kind: 'subscription_expiry',
            title: 'تمدید سرویس',
            body: 'سرویس شما به پایان دوره نزدیک شده است.',
            action: { type: 'open_subscription', id: '80000000-0000-4000-8000-000000000001' },
            read_at: null,
            created_at: new Date('2026-08-30T08:59:00Z'),
          },
          {
            id: '70000000-0000-4000-8000-000000000001',
            kind: 'maintenance',
            title: 'نگهداری',
            body: 'نگهداری برنامه‌ریزی‌شده انجام شد.',
            action: null,
            read_at: new Date('2026-08-30T08:58:30Z'),
            created_at: new Date('2026-08-30T08:58:00Z'),
          },
        ],
      };
    },
  };
  const route = routerWithDatabase(database);
  const incoming = request('/v1/notifications?limit=10');
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId });

  assert.equal(response.status, 200);
  assert.equal(response.body.data.length, 2);
  assert.equal(response.body.data[0].read, false);
  assert.equal(response.body.meta.unread_count, 1);
  assert.deepEqual(calls[0].params, [principal.userId, 11]);
  assert.deepEqual(calls[1].params, [principal.userId]);
});

test('marking a notification read cannot update another user notification', async () => {
  const database = {
    async query(sql, params) {
      assert.match(sql, /WHERE user_id = \$1 AND id = \$2/);
      assert.equal(params[0], principal.userId);
      return { rowCount: 0, rows: [] };
    },
  };
  const route = routerWithDatabase(database);
  const incoming = request('/v1/notifications/70000000-0000-4000-8000-000000000009/read', { method: 'POST' });
  await assert.rejects(
    route({ request: incoming, url: new URL(incoming.url), requestId }),
    (error) => error?.status === 404 && error?.code === 'notification_not_found',
  );
});

test('notification preferences require the complete boolean preference set', async () => {
  const route = routerWithDatabase({ async query() { throw new Error('database must not be called'); } });
  const incoming = request('/v1/notifications/preferences', {
    method: 'PUT',
    body: { marketing: true },
  });
  await assert.rejects(
    route({ request: incoming, url: new URL(incoming.url), requestId }),
    (error) => error?.status === 400 && error?.code === 'invalid_request',
  );
});

test('marketing notification preference defaults can be explicitly persisted without changing security categories', async () => {
  const expected = {
    subscription_expiry: true,
    purchase_success: true,
    payment_failure: true,
    maintenance: true,
    security_update: true,
    support_reply: true,
    marketing: false,
  };
  const database = {
    async query(sql, params) {
      assert.match(sql, /UPDATE control_notification_preferences/);
      assert.deepEqual(params, [principal.userId, ...Object.values(expected)]);
      return { rowCount: 1, rows: [expected] };
    },
  };
  const route = routerWithDatabase(database);
  const incoming = request('/v1/notifications/preferences', { method: 'PUT', body: expected });
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId });
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data, expected);
});
