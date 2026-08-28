import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PostgresTelegramBotApprovalStore,
  TelegramBotApprovalAdapter,
  createTelegramBotApprovalAdapter,
} from '../src/adapters/telegram-bot-approval.js';

const NOW = new Date('2026-08-28T10:00:00.000Z');
const LATER = new Date('2026-08-28T10:10:00.000Z');
const USER_ID = '10000000-0000-4000-8000-000000000001';
const TARGET_ID = '10000000-0000-4000-8000-000000000002';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';

function dbResult(rows = []) {
  return { rowCount: rows.length, rows };
}

function makePool({ direct = async () => dbResult(), client = async () => dbResult() } = {}) {
  const directQueries = [];
  const clientQueries = [];
  let released = 0;
  let ended = 0;
  const dbClient = {
    async query(sql, params) {
      clientQueries.push({ sql, params });
      return client(sql, params, clientQueries.length);
    },
    release() { released += 1; },
  };
  return {
    pool: {
      async query(sql, params) {
        directQueries.push({ sql, params });
        return direct(sql, params, directQueries.length);
      },
      async connect() { return dbClient; },
      async end() { ended += 1; },
    },
    directQueries,
    clientQueries,
    released: () => released,
    ended: () => ended,
  };
}

function approvalRow(overrides = {}) {
  return {
    state_digest: 'state-digest',
    code_challenge: 'challenge',
    user_id: USER_ID,
    device_id: DEVICE_ID,
    telegram_subject: null,
    telegram_username: null,
    telegram_display_name: null,
    expires_at: LATER,
    approved_at: null,
    cancelled_at: null,
    consumed_at: null,
    ...overrides,
  };
}

test('Postgres Telegram approval store validates its pool and covers direct persistence state mapping', async () => {
  assert.throws(() => new PostgresTelegramBotApprovalStore({}), /PostgreSQL pool is required/);

  let statusRead = 0;
  const fake = makePool({
    direct: async (sql) => {
      if (sql.includes('INSERT INTO control_telegram_bot_approvals')) return dbResult();
      if (sql.includes('SET cancelled_at')) return dbResult([{ request_digest: 'r' }]);
      if (sql.includes('SET consumed_at')) return dbResult([{ state_digest: 's' }]);
      if (sql.includes('FROM control_telegram_bot_approvals')) {
        statusRead += 1;
        if (statusRead === 1) return dbResult([approvalRow()]);
        if (statusRead === 2) return dbResult([approvalRow({ approved_at: NOW, telegram_subject: '123456789' })]);
        if (statusRead === 3) return dbResult([approvalRow({ cancelled_at: NOW })]);
        if (statusRead === 4) return dbResult([approvalRow({ consumed_at: NOW })]);
        if (statusRead === 5) return dbResult([approvalRow({ expires_at: new Date(NOW.getTime() - 1) })]);
        return dbResult();
      }
      throw new Error(`Unexpected direct SQL: ${sql}`);
    },
  });
  const store = new PostgresTelegramBotApprovalStore(fake.pool);
  await store.create({
    stateDigest: 'sd', requestDigest: 'rd', codeChallenge: 'cc', redirectUri: 'https://app.test/cb',
    userId: USER_ID, deviceId: DEVICE_ID, expiresAt: LATER, now: NOW,
  });
  assert.equal(await store.cancel({ requestDigest: 'rd', now: NOW }), true);
  assert.equal(await store.markConsumed({ stateDigest: 'sd', userId: USER_ID, deviceId: DEVICE_ID, now: NOW }), true);
  assert.equal(await store.restoreApproved({
    stateDigest: 'sd', userId: USER_ID, deviceId: DEVICE_ID, consumedAt: NOW,
  }), true);

  const pending = await store.findStatus({ stateDigest: 's1', userId: USER_ID, deviceId: DEVICE_ID, now: NOW });
  assert.equal(pending.status, 'pending');
  const approved = await store.loadForExchange({ stateDigest: 's2', userId: USER_ID, deviceId: DEVICE_ID, now: NOW });
  assert.equal(approved.status, 'approved');
  assert.equal(approved.telegramSubject, '123456789');
  assert.equal((await store.findStatus({ stateDigest: 's3', userId: USER_ID, deviceId: DEVICE_ID, now: NOW })).status, 'cancelled');
  assert.equal((await store.findStatus({ stateDigest: 's4', userId: USER_ID, deviceId: DEVICE_ID, now: NOW })).status, 'consumed');
  assert.equal((await store.findStatus({ stateDigest: 's5', userId: USER_ID, deviceId: DEVICE_ID, now: NOW })).status, 'expired');
  assert.equal(await store.findStatus({ stateDigest: 'missing', userId: USER_ID, deviceId: DEVICE_ID, now: NOW }), null);
  await store.close();
  assert.equal(fake.ended(), 1);
});

test('approval transaction supports first approval, replay and identity conflict while rejecting unavailable requests', async () => {
  async function approveWith(row) {
    const fake = makePool({
      client: async (sql) => {
        if (sql === 'BEGIN' || sql === 'COMMIT' || sql === 'ROLLBACK') return dbResult();
        if (sql.includes('SELECT telegram_subject')) return row === null ? dbResult() : dbResult([row]);
        if (sql.includes('UPDATE control_telegram_bot_approvals')) return dbResult();
        throw new Error(`Unexpected SQL: ${sql}`);
      },
    });
    const store = new PostgresTelegramBotApprovalStore(fake.pool);
    const value = await store.approve({
      requestDigest: 'r', telegramSubject: '123456789', username: 'ganj_user', displayName: 'Ganj User', now: NOW,
    });
    assert.equal(fake.released(), 1);
    return { value, fake };
  }

  assert.equal((await approveWith({ telegram_subject: null, approved_at: null, cancelled_at: null, consumed_at: null, expires_at: LATER })).value, true);
  assert.equal((await approveWith({ telegram_subject: '123456789', approved_at: NOW, cancelled_at: null, consumed_at: null, expires_at: LATER })).value, true);
  assert.equal((await approveWith({ telegram_subject: '987654321', approved_at: NOW, cancelled_at: null, consumed_at: null, expires_at: LATER })).value, 'conflict');
  assert.equal((await approveWith(null)).value, false);
  assert.equal((await approveWith({ telegram_subject: null, approved_at: null, cancelled_at: NOW, consumed_at: null, expires_at: LATER })).value, false);
  assert.equal((await approveWith({ telegram_subject: null, approved_at: null, cancelled_at: null, consumed_at: NOW, expires_at: LATER })).value, false);
  assert.equal((await approveWith({ telegram_subject: null, approved_at: null, cancelled_at: null, consumed_at: null, expires_at: new Date(NOW.getTime() - 1) })).value, false);
});

test('approval transaction rolls back and releases client on database failure', async () => {
  const fake = makePool({
    client: async (sql) => {
      if (sql === 'BEGIN' || sql === 'ROLLBACK') return dbResult();
      if (sql.includes('SELECT telegram_subject')) throw new Error('database unavailable');
      return dbResult();
    },
  });
  const store = new PostgresTelegramBotApprovalStore(fake.pool);
  await assert.rejects(
    store.approve({ requestDigest: 'r', telegramSubject: '123456789', username: null, displayName: null, now: NOW }),
    /database unavailable/,
  );
  assert.ok(fake.clientQueries.some(({ sql }) => sql === 'ROLLBACK'));
  assert.equal(fake.released(), 1);
});

test('account-link preparation allows free-only guest merge but rejects missing device, mismatched Telegram and paid holdings', async () => {
  async function prepare({ deviceRows, targetRows = [], paid = false }) {
    const fake = makePool({
      client: async (sql) => {
        if (sql === 'BEGIN' || sql === 'COMMIT' || sql === 'ROLLBACK') return dbResult();
        if (sql.includes('FROM control_devices')) return dbResult(deviceRows);
        if (sql.includes('FROM control_users WHERE telegram_subject')) return dbResult(targetRows);
        if (sql.includes('AS has_paid_holdings')) return dbResult([{ has_paid_holdings: paid }]);
        throw new Error(`Unexpected SQL: ${sql}`);
      },
    });
    const store = new PostgresTelegramBotApprovalStore(fake.pool);
    const value = await store.prepareAccountLink({ deviceId: DEVICE_ID, telegramSubject: '123456789', now: NOW });
    assert.equal(fake.released(), 1);
    return { value, fake };
  }

  assert.equal((await prepare({ deviceRows: [] })).value, 'merge_required');
  assert.equal((await prepare({ deviceRows: [{ user_id: USER_ID, telegram_subject: '999999999' }] })).value, 'merge_required');
  assert.equal((await prepare({ deviceRows: [{ user_id: USER_ID, telegram_subject: null }], targetRows: [] })).value, 'ready');
  assert.equal((await prepare({ deviceRows: [{ user_id: USER_ID, telegram_subject: null }], targetRows: [{ id: USER_ID }] })).value, 'ready');
  assert.equal((await prepare({ deviceRows: [{ user_id: USER_ID, telegram_subject: null }], targetRows: [{ id: TARGET_ID }], paid: true })).value, 'merge_required');
  const freeOnly = await prepare({
    deviceRows: [{ user_id: USER_ID, telegram_subject: null }],
    targetRows: [{ id: TARGET_ID }],
    paid: false,
  });
  assert.equal(freeOnly.value, 'ready');
  assert.equal(freeOnly.fake.clientQueries.some(({ sql }) => sql.includes('DELETE FROM control_services')), false);
});

test('account-link preparation rolls back on unexpected database failure', async () => {
  const fake = makePool({
    client: async (sql) => {
      if (sql === 'BEGIN' || sql === 'ROLLBACK') return dbResult();
      if (sql.includes('FROM control_devices')) throw new Error('link failure');
      return dbResult();
    },
  });
  const store = new PostgresTelegramBotApprovalStore(fake.pool);
  await assert.rejects(
    store.prepareAccountLink({ deviceId: DEVICE_ID, telegramSubject: '123456789', now: NOW }),
    /link failure/,
  );
  assert.ok(fake.clientQueries.some(({ sql }) => sql === 'ROLLBACK'));
  assert.equal(fake.released(), 1);
});

test('adapter constructor and runtime factory reject unsafe deployment configuration', async () => {
  const completeStore = {
    create() {}, approve() {}, cancel() {}, findStatus() {}, loadForExchange() {}, prepareAccountLink() {}, markConsumed() {}, restoreApproved() {}, close() {},
  };
  const broker = { linkAndIssueSession() {}, close() {} };
  const base = {
    store: completeStore,
    accountBroker: broker,
    botUsername: 'ganj_vpn_bot',
    redirectUris: ['https://app.ganj.test/telegram'],
    internalToken: 'i'.repeat(32),
    codeKey: 'k'.repeat(32),
  };
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, store: {} }), /store is incomplete/);
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, accountBroker: {} }), /broker is incomplete/);
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, botUsername: 'bad-name' }), /BOT_USERNAME is invalid/);
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, redirectUris: ['http://unsafe.test'] }), /allow-list is invalid/);
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, stateSeconds: 30 }), /TTL is invalid/);
  assert.throws(() => new TelegramBotApprovalAdapter({ ...base, internalToken: 'short' }), /32 to 4096/);

  await assert.rejects(createTelegramBotApprovalAdapter({ environment: {} }), /DATABASE_URL is required/);
});
