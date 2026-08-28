import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';
import { TelegramBotApprovalAdapter } from '../src/adapters/telegram-bot-approval.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const OTHER_DEVICE_ID = '20000000-0000-4000-8000-000000000002';
const INTERNAL_TOKEN = 'internal-bot-token-'.padEnd(64, 'x');
const CODE_KEY = 'bot-approval-code-key-'.padEnd(64, 'y');
const REDIRECT_URI = 'https://app.ganjvpn.example/auth/telegram';
const VERIFIER = 'A'.repeat(64);
const CHALLENGE = createHash('sha256').update(VERIFIER).digest('base64url');

function memoryStore(clock) {
  const rows = new Map();
  let mergePreparation = 'ready';
  return {
    rows,
    setMergePreparation(value) { mergePreparation = value; },
    async create(value) {
      rows.set(value.stateDigest, {
        ...value,
        telegramSubject: null,
        username: null,
        displayName: null,
        approvedAt: null,
        cancelledAt: null,
        consumedAt: null,
      });
    },
    async approve({ requestDigest, telegramSubject, username, displayName, now }) {
      const row = [...rows.values()].find((item) => item.requestDigest === requestDigest);
      if (!row || row.cancelledAt || row.consumedAt || row.expiresAt <= now) return false;
      if (row.approvedAt) return row.telegramSubject === telegramSubject ? true : 'conflict';
      Object.assign(row, { telegramSubject, username, displayName, approvedAt: now });
      return true;
    },
    async cancel({ requestDigest, now }) {
      const row = [...rows.values()].find((item) => item.requestDigest === requestDigest);
      if (!row || row.approvedAt || row.cancelledAt || row.consumedAt || row.expiresAt <= now) return false;
      row.cancelledAt = now;
      return true;
    },
    async findStatus({ stateDigest, userId, deviceId, now }) {
      const row = rows.get(stateDigest);
      if (!row || row.userId !== userId || row.deviceId !== deviceId) return null;
      return mapped(row, now);
    },
    async loadForExchange({ stateDigest, userId, deviceId, now }) {
      const row = rows.get(stateDigest);
      if (!row || row.userId !== userId || row.deviceId !== deviceId) return null;
      return mapped(row, now);
    },
    async prepareAccountLink() { return mergePreparation; },
    async markConsumed({ stateDigest, userId, deviceId, now }) {
      const row = rows.get(stateDigest);
      if (!row || row.userId !== userId || row.deviceId !== deviceId || !row.approvedAt
        || row.cancelledAt || row.consumedAt || row.expiresAt <= now) return false;
      row.consumedAt = now;
      return true;
    },
    async close() {},
  };
}

function mapped(row, now) {
  const status = row.consumedAt ? 'consumed'
    : row.cancelledAt ? 'cancelled'
      : row.expiresAt <= now ? 'expired'
        : row.approvedAt ? 'approved' : 'pending';
  return {
    status,
    stateDigest: row.stateDigest,
    codeChallenge: row.codeChallenge,
    userId: row.userId,
    deviceId: row.deviceId,
    telegramSubject: row.telegramSubject,
    username: row.username,
    displayName: row.displayName,
    expiresAt: row.expiresAt,
  };
}

function fixture() {
  let now = new Date('2026-08-28T09:45:00.000Z');
  const clock = () => new Date(now);
  const store = memoryStore(clock);
  const brokerCalls = [];
  const adapter = new TelegramBotApprovalAdapter({
    store,
    accountBroker: {
      async linkAndIssueSession(value) {
        brokerCalls.push(value);
        return { user_id: USER_ID, device_id: value.deviceId, access_token: 'server-issued-token' };
      },
      async close() {},
    },
    botUsername: 'ganj_vpn_bot',
    redirectUris: [REDIRECT_URI],
    internalToken: INTERNAL_TOKEN,
    codeKey: CODE_KEY,
    clock,
  });
  return {
    adapter,
    store,
    brokerCalls,
    advance(ms) { now = new Date(now.getTime() + ms); },
  };
}

function tokenFrom(url) {
  const start = new URL(url).searchParams.get('start');
  assert.match(start, /^APP-[A-Za-z0-9_-]{43}$/);
  return start.slice(4);
}

const principal = { userId: USER_ID, deviceId: DEVICE_ID };

test('Bot Approval completes one-shot PKCE exchange and only backend broker issues app session', async () => {
  const { adapter, brokerCalls } = fixture();
  const started = await adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  assert.match(started.approval_url, /^https:\/\/t\.me\/ganj_vpn_bot\?start=APP-/);
  const requestToken = tokenFrom(started.approval_url);

  const pending = await adapter.status({ principal, state: started.state });
  assert.equal(pending.status, 'pending');
  await adapter.decide({
    authorization: `Bearer ${INTERNAL_TOKEN}`,
    requestToken,
    action: 'approve',
    telegramUserId: '123456789',
    username: 'ganj_user',
    displayName: 'Ganj User',
  });
  const approved = await adapter.status({ principal, state: started.state });
  assert.equal(approved.status, 'approved');
  assert.match(approved.code, /^[A-Za-z0-9_-]{43}$/);

  const session = await adapter.exchangeAuthorizationCode({
    principal,
    code: approved.code,
    state: started.state,
    codeVerifier: VERIFIER,
  });
  assert.equal(session.access_token, 'server-issued-token');
  assert.equal(brokerCalls.length, 1);
  assert.equal(brokerCalls[0].telegramSubject, '123456789');
  assert.equal(brokerCalls[0].providerClaims.method, 'bot-approval');

  await assert.rejects(
    () => adapter.exchangeAuthorizationCode({ principal, code: approved.code, state: started.state, codeVerifier: VERIFIER }),
    (error) => error?.code === 'invalid_telegram_bot_exchange' || error?.code === 'telegram_bot_exchange_replayed',
  );
});

test('approval decision requires internal bot bearer and rejects conflicting Telegram identity', async () => {
  const { adapter } = fixture();
  const started = await adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  const requestToken = tokenFrom(started.approval_url);
  await assert.rejects(
    () => adapter.decide({ authorization: 'Bearer wrong', requestToken, action: 'approve', telegramUserId: '123456789' }),
    (error) => error?.code === 'telegram_bot_approval_unauthorized',
  );
  await adapter.decide({ authorization: `Bearer ${INTERNAL_TOKEN}`, requestToken, action: 'approve', telegramUserId: '123456789' });
  await assert.rejects(
    () => adapter.decide({ authorization: `Bearer ${INTERNAL_TOKEN}`, requestToken, action: 'approve', telegramUserId: '987654321' }),
    (error) => error?.code === 'telegram_bot_identity_conflict',
  );
});

test('state and exchange are bound to initiating user and exact device', async () => {
  const { adapter } = fixture();
  const started = await adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  const requestToken = tokenFrom(started.approval_url);
  await adapter.decide({ authorization: `Bearer ${INTERNAL_TOKEN}`, requestToken, action: 'approve', telegramUserId: '123456789' });
  const approved = await adapter.status({ principal, state: started.state });
  const wrongDevice = { userId: USER_ID, deviceId: OTHER_DEVICE_ID };
  await assert.rejects(
    () => adapter.status({ principal: wrongDevice, state: started.state }),
    (error) => error?.code === 'telegram_bot_state_not_found',
  );
  await assert.rejects(
    () => adapter.exchangeAuthorizationCode({
      principal: wrongDevice,
      code: approved.code,
      state: started.state,
      codeVerifier: VERIFIER,
    }),
    (error) => error?.code === 'invalid_telegram_bot_exchange',
  );
});

test('expired and cancelled approvals never produce an exchange code', async () => {
  const expired = fixture();
  const started = await expired.adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  expired.advance(11 * 60 * 1000);
  const expiredStatus = await expired.adapter.status({ principal, state: started.state });
  assert.equal(expiredStatus.status, 'expired');
  assert.equal('code' in expiredStatus, false);

  const cancelled = fixture();
  const cancelledStart = await cancelled.adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  await cancelled.adapter.decide({
    authorization: `Bearer ${INTERNAL_TOKEN}`,
    requestToken: tokenFrom(cancelledStart.approval_url),
    action: 'cancel',
  });
  const cancelledStatus = await cancelled.adapter.status({ principal, state: cancelledStart.state });
  assert.equal(cancelledStatus.status, 'cancelled');
  assert.equal('code' in cancelledStatus, false);
});

test('paid guest holdings fail closed before account merge or token issuance', async () => {
  const { adapter, store, brokerCalls } = fixture();
  store.setMergePreparation('merge_required');
  const started = await adapter.start({ principal, codeChallenge: CHALLENGE, redirectUri: REDIRECT_URI });
  const requestToken = tokenFrom(started.approval_url);
  await adapter.decide({ authorization: `Bearer ${INTERNAL_TOKEN}`, requestToken, action: 'approve', telegramUserId: '123456789' });
  const approved = await adapter.status({ principal, state: started.state });
  await assert.rejects(
    () => adapter.exchangeAuthorizationCode({ principal, code: approved.code, state: started.state, codeVerifier: VERIFIER }),
    (error) => error?.code === 'telegram_account_merge_required',
  );
  assert.equal(brokerCalls.length, 0);
});
