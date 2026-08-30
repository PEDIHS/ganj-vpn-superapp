import assert from 'node:assert/strict';
import test from 'node:test';
import { createWalletRouter } from '../src/wallet-routes.js';

const principal = {
  userId: '10000000-0000-4000-8000-000000000001',
  deviceId: '20000000-0000-4000-8000-000000000001',
};

function request(path) {
  return new Request(`https://api.example.invalid${path}`, {
    headers: { authorization: 'Bearer opaque-test-token' },
  });
}

function routerWithDatabase(database) {
  return createWalletRouter({
    auth: { async authenticate() { return principal; } },
    repository: { kind: 'postgres-v1', database() { return database; } },
    clock: () => new Date('2026-08-30T08:00:00Z'),
  });
}

test('GET /v1/wallet returns only authenticated user balance', async () => {
  const database = {
    async query(sql, params) {
      assert.match(sql, /FROM control_wallets/);
      assert.deepEqual(params, [principal.userId]);
      return { rowCount: 1, rows: [{ balance_amount_minor: 5_000_000, currency: 'IRR' }] };
    },
  };
  const route = routerWithDatabase(database);
  const response = await route({
    request: request('/v1/wallet'),
    url: new URL('https://api.example.invalid/v1/wallet'),
    requestId: '90000000-0000-4000-8000-000000000001',
  });

  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data, {
    balance: { amount_minor: 5_000_000, currency: 'IRR' },
  });
});

test('GET /v1/wallet fails closed when migration has not initialized wallet', async () => {
  const route = routerWithDatabase({ async query() { return { rowCount: 0, rows: [] }; } });
  await assert.rejects(
    route({
      request: request('/v1/wallet'),
      url: new URL('https://api.example.invalid/v1/wallet'),
      requestId: '90000000-0000-4000-8000-000000000002',
    }),
    (error) => error?.status === 503 && error?.code === 'wallet_not_initialized',
  );
});

test('wallet transactions are owner-scoped cursor paginated and privacy minimized', async () => {
  const rows = [
    {
      id: '70000000-0000-4000-8000-000000000003',
      entry_type: 'refund',
      direction: 'credit',
      amount_minor: 250_000,
      currency: 'IRR',
      balance_after_minor: 5_000_000,
      reference_type: 'order',
      reference_id: 'order-safe-ref',
      description: 'بازگشت وجه',
      created_at: new Date('2026-08-30T07:59:00Z'),
    },
    {
      id: '70000000-0000-4000-8000-000000000002',
      entry_type: 'purchase',
      direction: 'debit',
      amount_minor: 2_990_000,
      currency: 'IRR',
      balance_after_minor: 4_750_000,
      reference_type: 'order',
      reference_id: 'order-second-safe-ref',
      description: null,
      created_at: new Date('2026-08-30T07:58:00Z'),
    },
    {
      id: '70000000-0000-4000-8000-000000000001',
      entry_type: 'topup',
      direction: 'credit',
      amount_minor: 5_000_000,
      currency: 'IRR',
      balance_after_minor: 7_740_000,
      reference_type: null,
      reference_id: null,
      description: null,
      created_at: new Date('2026-08-30T07:57:00Z'),
    },
  ];
  const database = {
    async query(sql, params) {
      assert.match(sql, /FROM control_wallet_ledger/);
      assert.deepEqual(params, [principal.userId, 3]);
      return { rowCount: rows.length, rows };
    },
  };
  const route = routerWithDatabase(database);
  const response = await route({
    request: request('/v1/wallet/transactions?limit=2'),
    url: new URL('https://api.example.invalid/v1/wallet/transactions?limit=2'),
    requestId: '90000000-0000-4000-8000-000000000003',
  });

  assert.equal(response.status, 200);
  assert.equal(response.body.data.length, 2);
  assert.equal(response.body.meta.has_more, true);
  assert.equal(typeof response.body.meta.next_cursor, 'string');
  assert.deepEqual(response.body.data[0], {
    id: rows[0].id,
    type: 'refund',
    direction: 'credit',
    amount: { amount_minor: 250_000, currency: 'IRR' },
    balance_after: { amount_minor: 5_000_000, currency: 'IRR' },
    reference_type: 'order',
    reference_id: 'order-safe-ref',
    description: 'بازگشت وجه',
    created_at: '2026-08-30T07:59:00.000Z',
  });
});

test('wallet transaction cursor rejects malformed input before database access', async () => {
  let called = false;
  const route = routerWithDatabase({ async query() { called = true; return { rows: [] }; } });
  await assert.rejects(
    route({
      request: request('/v1/wallet/transactions?cursor=%%%'),
      url: new URL('https://api.example.invalid/v1/wallet/transactions?cursor=%%%'),
      requestId: '90000000-0000-4000-8000-000000000004',
    }),
    (error) => error?.status === 400 && error?.code === 'invalid_request',
  );
  assert.equal(called, false);
});
