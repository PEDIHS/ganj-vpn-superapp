import assert from 'node:assert/strict';
import test from 'node:test';
import { createAccountRouter } from '../src/account-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  deviceId: '20000000-0000-4000-8000-000000000001',
};

function request() {
  return new Request('https://api.example.invalid/v1/me', {
    headers: { authorization: 'Bearer opaque-test-token' },
  });
}

function routerForRow(row) {
  return createAccountRouter({
    auth: { async authenticate() { return principal; } },
    repository: {
      kind: 'postgres-v1',
      database() {
        return {
          async query(sql, params) {
            assert.match(sql, /FROM control_users/);
            assert.deepEqual(params, [principal.userId]);
            return row ? { rowCount: 1, rows: [row] } : { rowCount: 0, rows: [] };
          },
        };
      },
    },
    authSession: null,
    clock: () => new Date('2026-08-30T07:00:00Z'),
  });
}

test('GET /v1/me exposes only privacy-safe account identity fields', async () => {
  const route = routerForRow({
    id: principal.userId,
    status: 'active',
    display_name: 'Pedram',
    telegram_subject: 'telegram-provider-subject-must-not-leak',
  });

  const response = await route({
    request: request(),
    url: new URL('https://api.example.invalid/v1/me'),
    requestId: '90000000-0000-4000-8000-000000000001',
  });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data, {
    id: principal.userId,
    status: 'active',
    display_name: 'Pedram',
    locale: 'fa-IR',
    telegram_linked: true,
    telegram_username: null,
  });
  assert.doesNotMatch(JSON.stringify(response.body), /telegram-provider-subject-must-not-leak/);
});

test('GET /v1/me fails closed for inactive account', async () => {
  const route = routerForRow({
    id: principal.userId,
    status: 'blocked',
    display_name: null,
    telegram_subject: null,
  });

  await assert.rejects(
    route({
      request: request(),
      url: new URL('https://api.example.invalid/v1/me'),
      requestId: '90000000-0000-4000-8000-000000000002',
    }),
    (error) => error?.status === 403 && error?.code === 'account_inactive',
  );
});

test('GET /v1/me fails closed when account row is missing', async () => {
  const route = routerForRow(null);

  await assert.rejects(
    route({
      request: request(),
      url: new URL('https://api.example.invalid/v1/me'),
      requestId: '90000000-0000-4000-8000-000000000003',
    }),
    (error) => error?.status === 404 && error?.code === 'account_not_found',
  );
});

test('authenticated Android wallet bridge returns only its own linked numeric Telegram ID', async () => {
  const route=routerForRow({
    id: principal.userId, status: 'active', display_name: null, telegram_subject: '123456789',
  });
  const url=new URL('https://api.example.invalid/v1/me/store-identity');
  const reply=await route({
    request:new Request(url,{headers:{authorization:'Bearer opaque-test-token'}}),
    url, requestId:'90000000-0000-4000-8000-000000000004',
  });
  assert.equal(reply.status, 200);
  assert.deepEqual(reply.body.data,{telegram_subject:'123456789'});
});

test('Android wallet bridge denies guest, missing and non-numeric identities', async () => {
  const url=new URL('https://api.example.invalid/v1/me/store-identity');
  for(const subject of [null,'not-a-number','123']) {
    const route=routerForRow({id:principal.userId,status:'active',telegram_subject:subject});
    await assert.rejects(route({
      request:new Request(url),url,requestId:'90000000-0000-4000-8000-000000000005',
    }),err=>err.status===403&&err.code==='telegram_link_required');
  }
});
