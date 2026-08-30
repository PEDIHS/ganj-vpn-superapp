import assert from 'node:assert/strict';
import test from 'node:test';
import { createAdminSupportRouter } from '../src/admin-support-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  subject: 'admin:test',
  scopes: ['admin:control-plane'],
};
const ticketId = '70000000-0000-4000-8000-000000000001';
const userId = '10000000-0000-4000-8000-000000000099';
const messageId = '71000000-0000-4000-8000-000000000001';
const requestId = '90000000-0000-4000-8000-000000000001';

function request(path, { method = 'GET', body } = {}) {
  return new Request(`https://api.example.invalid${path}`, {
    method,
    headers: body ? { 'content-type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
}

function router(database, audits = []) {
  const repository = {
    kind: 'postgres-v1',
    database() { return database; },
    async transaction(work) { return work(); },
    async appendAdminAudit(entry) { audits.push(entry); },
  };
  return createAdminSupportRouter({
    repository,
    parseBody: (incoming) => incoming.json(),
    clock: () => new Date('2026-08-30T10:00:00Z'),
  });
}

const ticket = {
  id: ticketId,
  user_id: userId,
  public_code: 'SUP-1001',
  category: 'account',
  priority: 'high',
  subject: 'مشکل حساب',
  status: 'waiting_support',
  created_at: new Date('2026-08-30T09:00:00Z'),
  updated_at: new Date('2026-08-30T09:30:00Z'),
};

test('admin support list requires control plane scope', async () => {
  const route = router({ async query() { throw new Error('must not query'); } });
  const incoming = request('/v1/admin/support/tickets');
  await assert.rejects(
    route({ request: incoming, url: new URL(incoming.url), pathname: new URL(incoming.url).pathname, principal: { ...principal, scopes: [] }, requestId }),
    (error) => error?.status === 403 && error?.code === 'admin_scope_required',
  );
});

test('admin support reply writes support role moves status and audits', async () => {
  const audits = [];
  const database = {
    async query(sql) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      if (/INSERT INTO control_support_messages/.test(sql)) {
        return { rowCount: 1, rows: [{ id: messageId, sender_role: 'support', body: 'پاسخ پشتیبانی', created_at: new Date('2026-08-30T09:40:00Z') }] };
      }
      if (/UPDATE control_support_tickets/.test(sql)) {
        return { rowCount: 1, rows: [{ ...ticket, status: 'waiting_user', updated_at: new Date('2026-08-30T09:40:00Z') }] };
      }
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database, audits);
  const incoming = request(`/v1/admin/support/tickets/${ticketId}/messages`, {
    method: 'POST',
    body: { client_message_id: messageId, body: 'پاسخ پشتیبانی' },
  });
  const url = new URL(incoming.url);
  const response = await route({ request: incoming, url, pathname: url.pathname, principal, requestId });

  assert.equal(response.status, 201);
  assert.equal(response.body.data.sender_role, 'support');
  assert.equal(audits.length, 1);
  assert.equal(audits[0].action, 'support.reply');
  assert.equal(audits[0].resourceId, ticketId);
});

test('admin can resolve ticket with audited status transition', async () => {
  const audits = [];
  const database = {
    async query(sql, params) {
      if (/FROM control_support_tickets/.test(sql)) return { rowCount: 1, rows: [ticket] };
      if (/UPDATE control_support_tickets/.test(sql)) {
        assert.equal(params[1], 'resolved');
        return { rowCount: 1, rows: [{ ...ticket, status: 'resolved', updated_at: new Date('2026-08-30T09:50:00Z') }] };
      }
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
  const route = router(database, audits);
  const incoming = request(`/v1/admin/support/tickets/${ticketId}`, { method: 'PATCH', body: { status: 'resolved' } });
  const url = new URL(incoming.url);
  const response = await route({ request: incoming, url, pathname: url.pathname, principal, requestId });

  assert.equal(response.status, 200);
  assert.equal(response.body.data.status, 'resolved');
  assert.equal(audits.length, 1);
  assert.equal(audits[0].action, 'support.status');
});
