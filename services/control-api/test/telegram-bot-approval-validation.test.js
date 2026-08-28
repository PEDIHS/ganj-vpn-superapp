import assert from 'node:assert/strict';
import test from 'node:test';
import { TelegramBotApprovalAdapter } from '../src/adapters/telegram-bot-approval.js';
import { createSafeApplicationBoundary } from '../src/safe-application-boundary.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const STATE = 's'.repeat(43);
const REQUEST = 'r'.repeat(43);
const INTERNAL = 'i'.repeat(32);
const CHALLENGE = 'c'.repeat(43);
const NOW = new Date('2026-08-28T10:00:00.000Z');
const EXPIRES = new Date('2026-08-28T10:10:00.000Z');

function makeAdapter(overrides = {}) {
  const store = {
    async create() {},
    async approve() { return true; },
    async cancel() { return true; },
    async findStatus() { return null; },
    async loadForExchange() { return null; },
    async prepareAccountLink() { return 'ready'; },
    async markConsumed() { return true; },
    async close() {},
    ...overrides.store,
  };
  const accountBroker = {
    async linkAndIssueSession() { return { ok: true }; },
    async close() {},
    ...overrides.accountBroker,
  };
  return new TelegramBotApprovalAdapter({
    store,
    accountBroker,
    botUsername: 'ganj_vpn_bot',
    redirectUris: ['https://app.ganj.test/telegram'],
    internalToken: INTERNAL,
    codeKey: 'k'.repeat(32),
    clock: () => new Date(NOW),
  });
}

async function rejectsCode(promise, code) {
  await assert.rejects(promise, (error) => error?.code === code);
}

test('Bot Approval rejects missing session, malformed PKCE and unsafe decision inputs', async () => {
  const adapter = makeAdapter();
  await rejectsCode(
    adapter.start({ principal: null, codeChallenge: CHALLENGE, redirectUri: 'https://app.ganj.test/telegram' }),
    'telegram_bot_login_requires_session',
  );
  await rejectsCode(
    adapter.start({ principal: { userId: USER_ID, deviceId: DEVICE_ID }, codeChallenge: 'short', redirectUri: 'https://app.ganj.test/telegram' }),
    'invalid_telegram_bot_login',
  );
  await rejectsCode(
    adapter.decide({ authorization: null, requestToken: REQUEST, action: 'cancel' }),
    'telegram_bot_approval_unauthorized',
  );
  await rejectsCode(
    adapter.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: 'bad', action: 'cancel' }),
    'invalid_telegram_bot_request',
  );
  await rejectsCode(
    adapter.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: REQUEST, action: 'reject', telegramUserId: '123456789' }),
    'invalid_telegram_bot_decision',
  );
  await rejectsCode(
    adapter.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: REQUEST, action: 'approve', telegramUserId: '123456789', username: 'bad!' }),
    'invalid_telegram_bot_identity',
  );
  await rejectsCode(
    adapter.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: REQUEST, action: 'approve', telegramUserId: '123456789', displayName: '   ' }),
    'invalid_telegram_bot_identity',
  );
});

test('Bot Approval reports unavailable cancel and approval without mutating identity state', async () => {
  const cancelled = makeAdapter({ store: { async cancel() { return false; } } });
  await rejectsCode(
    cancelled.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: REQUEST, action: 'cancel' }),
    'telegram_bot_request_unavailable',
  );

  const unavailable = makeAdapter({ store: { async approve() { return false; } } });
  await rejectsCode(
    unavailable.decide({ authorization: `Bearer ${INTERNAL}`, requestToken: REQUEST, action: 'approve', telegramUserId: '123456789' }),
    'telegram_bot_request_unavailable',
  );
});

test('Bot Approval status covers missing state, missing row and pending state without emitting an exchange code', async () => {
  const adapter = makeAdapter();
  await rejectsCode(adapter.status({ principal: null, state: STATE }), 'invalid_telegram_bot_state');
  await rejectsCode(
    adapter.status({ principal: { userId: USER_ID, deviceId: DEVICE_ID }, state: STATE }),
    'telegram_bot_state_not_found',
  );

  const pending = makeAdapter({
    store: {
      async findStatus() {
        return {
          status: 'pending',
          stateDigest: 'digest',
          codeChallenge: CHALLENGE,
          userId: USER_ID,
          deviceId: DEVICE_ID,
          telegramSubject: null,
          username: null,
          displayName: null,
          expiresAt: EXPIRES,
        };
      },
    },
  });
  assert.deepEqual(
    await pending.status({ principal: { userId: USER_ID, deviceId: DEVICE_ID }, state: STATE }),
    { status: 'pending', expires_at: EXPIRES.toISOString() },
  );
});

test('Bot Approval exchange rejects malformed principal, unavailable approval and PKCE mismatch', async () => {
  const adapter = makeAdapter();
  await rejectsCode(
    adapter.exchangeAuthorizationCode({ principal: null, code: 'x'.repeat(43), state: STATE, codeVerifier: 'v'.repeat(43) }),
    'invalid_telegram_bot_exchange',
  );
  await rejectsCode(
    adapter.exchangeAuthorizationCode({
      principal: { userId: USER_ID, deviceId: DEVICE_ID },
      code: 'x'.repeat(43), state: STATE, codeVerifier: 'v'.repeat(43),
    }),
    'invalid_telegram_bot_exchange',
  );

  const mismatch = makeAdapter({
    store: {
      async loadForExchange() {
        return {
          status: 'approved',
          stateDigest: 'state-digest',
          codeChallenge: CHALLENGE,
          userId: USER_ID,
          deviceId: DEVICE_ID,
          telegramSubject: '123456789',
          username: 'ganj_user',
          displayName: 'Ganj User',
          expiresAt: EXPIRES,
        };
      },
    },
  });
  await rejectsCode(
    mismatch.exchangeAuthorizationCode({
      principal: { userId: USER_ID, deviceId: DEVICE_ID },
      code: 'x'.repeat(43), state: STATE, codeVerifier: 'v'.repeat(43),
    }),
    'invalid_telegram_bot_exchange',
  );
});

test('final safe application boundary preserves valid request id and sanitizes unknown rejected promises', async () => {
  const requestId = '123e4567-e89b-42d3-a456-426614174000';
  const app = createSafeApplicationBoundary(async () => {
    throw new Error('secret internal detail');
  }, { clock: () => new Date(NOW) });
  const response = await app(new Request('https://control.ganj.test/v1/services', {
    headers: { 'x-request-id': requestId },
  }));
  assert.equal(response.status, 500);
  assert.equal(response.body.meta.request_id, requestId);
  assert.equal(response.body.error.code, 'internal_error');
  assert.equal(JSON.stringify(response.body).includes('secret internal detail'), false);
});
