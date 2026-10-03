import assert from 'node:assert/strict';
import test from 'node:test';
import { createSupportThreadRouter } from '../src/support-thread-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  deviceId: '20000000-0000-4000-8000-000000000001',
};
const ticketId = '70000000-0000-4000-8000-000000000001';
const messageId = '71000000-0000-4000-8000-000000000001';

function request(path, { method = 'GET', body } = {}) {
  return new Request(`https://api.example.invalid${path}`, {
    method,
    headers: { authorization: 'Bearer test', ...(body ? { 'content-type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
}

function repository(database) {
  return {
    kind: 'postgres-v1',
    database() { return database; },
    async transaction(work) { return work(); },
  };
}

function router(database) {
  return createSupportThreadRouter({
    auth: { async authenticate() { return principal; } },
    repository: repository(database),
    parseBody: (incoming) => incoming.json(),
    clock: () => new Date('2026-08-30T09:30:00Z'),
  });
}

const ticket = {
  id: ticketId,
  public_code: 'SUP-1001',
  category: 'account',
  priority: 'normal',
  subject: 'مشکل ورود حساب',
  status: 'open',
  created_at: new Date('2026-08-30T09:00:00Z'),
  updated_at: new Date('2026-08-30T09:00:00Z'),
};

test('ticket detail and messages are owner scoped', async () => {
  const database = {
    async query(sql, params) {
      assert.equal(params[0], principal.userId);
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      return {
        rowCount: 1,
        rows: [{ id: ticketId, sender_role: 'user', body: 'متن اولیه درخواست پشتیبانی', created_at: ticket.created_at }],
      };
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}`);
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000001' });
  assert.equal(response.status, 200);
  assert.equal(response.body.data.id, ticketId);
  assert.equal(response.body.data.messages.length, 1);
});

test('new user reply moves ticket to waiting_support', async () => {
  const calls = [];
  const database = {
    async query(sql) {
      calls.push(sql);
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      if (/INSERT INTO control_support_messages/.test(sql)) {
        return { rowCount: 1, rows: [{ id: messageId, sender_role: 'user', body: 'لطفاً این مشکل را بررسی کنید.', created_at: new Date('2026-08-30T09:20:00Z') }] };
      }
      if (/UPDATE control_support_tickets/.test(sql)) return { rowCount: 1, rows: [] };
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}/messages`, {
    method: 'POST',
    body: { client_message_id: messageId, body: 'لطفاً این مشکل را بررسی کنید.' },
  });
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000002' });
  assert.equal(response.status, 201);
  assert.ok(calls.some((sql) => /status = 'waiting_support'/.test(sql)));
});

test('same client message id and same body replays idempotently', async () => {
  const stored = {
    id: messageId,
    sender_role: 'user',
    body: 'لطفاً این مشکل را بررسی کنید.',
    created_at: new Date('2026-08-30T09:20:00Z'),
  };
  const database = {
    async query(sql) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      if (/INSERT INTO control_support_messages/.test(sql)) return { rowCount: 0, rows: [] };
      if (/SELECT id, sender_role, body, created_at/.test(sql)) return { rowCount: 1, rows: [stored] };
      if (/UPDATE control_support_tickets/.test(sql)) return { rowCount: 1, rows: [] };
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}/messages`, {
    method: 'POST',
    body: { client_message_id: messageId, body: stored.body },
  });
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000005' });
  assert.equal(response.status, 200);
  assert.equal(response.body.data.id, messageId);
  assert.equal(response.body.data.body, stored.body);
});

test('same client message id with different body fails closed', async () => {
  const stored = {
    id: messageId,
    sender_role: 'user',
    body: 'متن قبلی درخواست',
    created_at: new Date('2026-08-30T09:20:00Z'),
  };
  const database = {
    async query(sql) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      if (/INSERT INTO control_support_messages/.test(sql)) return { rowCount: 0, rows: [] };
      if (/SELECT id, sender_role, body, created_at/.test(sql)) return { rowCount: 1, rows: [stored] };
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}/messages`, {
    method: 'POST',
    body: { client_message_id: messageId, body: 'متن متفاوت برای همان شناسه' },
  });
  await assert.rejects(
    route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000006' }),
    (error) => error?.status === 409 && error?.code === 'idempotency_conflict',
  );
});

test('closed ticket rejects a reply until reopen', async () => {
  const database = {
    async query(sql) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [{ ...ticket, status: 'closed' }] };
      throw new Error('write must not occur');
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}/messages`, {
    method: 'POST',
    body: { client_message_id: messageId, body: 'لطفاً دوباره بررسی شود.' },
  });
  const responsePromise = route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000003' });
  await assert.rejects(responsePromise, (error) => error?.status === 409 && error?.code === 'support_ticket_closed');
});

test('reopen moves a closed ticket to waiting_support', async () => {
  const database = {
    async query(sql) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [{ ...ticket, status: 'closed' }] };
      if (/UPDATE control_support_tickets/.test(sql)) {
        return { rowCount: 1, rows: [{ ...ticket, status: 'waiting_support', updated_at: new Date('2026-08-30T09:30:00Z') }] };
      }
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database);
  const incoming = request(`/v1/support/tickets/${ticketId}/reopen`, { method: 'POST' });
  const response = await route({ request: incoming, url: new URL(incoming.url), requestId: '90000000-0000-4000-8000-000000000004' });
  assert.equal(response.status, 200);
  assert.equal(response.body.data.status, 'waiting_support');
});
