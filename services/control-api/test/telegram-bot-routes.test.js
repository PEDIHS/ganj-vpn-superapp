import assert from 'node:assert/strict';
import test from 'node:test';
import { createTelegramBotApprovalApplication } from '../src/telegram-bot-routes.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const STATE = 's'.repeat(43);
const CODE = 'c'.repeat(43);
const VERIFIER = 'v'.repeat(64);

function setup() {
  const calls = [];
  const auth = {
    async authenticate(request) {
      calls.push({ operation: 'authenticate' });
      if (request.headers.get('authorization') !== 'Bearer guest-session') {
        const error = new Error('Authentication required');
        error.status = 401;
        error.code = 'authentication_required';
        throw error;
      }
      return { userId: USER_ID, deviceId: DEVICE_ID };
    },
  };
  const telegramBotApproval = {
    async decide(value) { calls.push({ operation: 'decide', value }); return { status: value.action === 'cancel' ? 'cancelled' : 'approved' }; },
    async start(value) { calls.push({ operation: 'start', value }); return { approval_url: 'https://t.me/ganj_vpn_bot?start=APP-token', state: STATE, expires_at: '2026-08-28T10:00:00.000Z' }; },
    async status(value) { calls.push({ operation: 'status', value }); return { status: 'approved', code: CODE, expires_at: '2026-08-28T10:00:00.000Z' }; },
    async exchangeAuthorizationCode(value) { calls.push({ operation: 'exchange', value }); return { access_token: 'server-token', device_id: DEVICE_ID }; },
  };
  const baseApplication = async () => ({ status: 404, body: { error: { code: 'base_route' } } });
  const app = createTelegramBotApprovalApplication({ baseApplication, auth, telegramBotApproval });
  return { app, calls };
}

function request(app, path, body, authorization = null) {
  const headers = new Headers({ 'content-type': 'application/json' });
  if (authorization) headers.set('authorization', authorization);
  return app(new Request(`https://control.ganj.test${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  }));
}

test('bot internal decision bypasses user auth but forwards only internal bearer to adapter', async () => {
  const { app, calls } = setup();
  const response = await request(app, '/v1/internal/telegram/bot/approval', {
    request_token: 'r'.repeat(43),
    action: 'approve',
    telegram_user_id: '123456789',
    username: 'ganj_user',
  }, 'Bearer bot-internal-secret');
  assert.equal(response.status, 200);
  assert.equal(calls.some((call) => call.operation === 'authenticate'), false);
  const decision = calls.find((call) => call.operation === 'decide');
  assert.equal(decision.value.authorization, 'Bearer bot-internal-secret');
  assert.equal(decision.value.telegramUserId, '123456789');
});

test('start status and exchange all require the existing guest device session', async () => {
  const { app, calls } = setup();
  const unauthenticated = await request(app, '/v1/auth/telegram/bot/start', {
    code_challenge: 'x'.repeat(43),
    redirect_uri: 'https://app.ganj.test/auth/telegram',
  });
  assert.equal(unauthenticated.status, 401);
  assert.equal(calls.some((call) => call.operation === 'start'), false);

  const start = await request(app, '/v1/auth/telegram/bot/start', {
    code_challenge: 'x'.repeat(43),
    redirect_uri: 'https://app.ganj.test/auth/telegram',
  }, 'Bearer guest-session');
  assert.equal(start.status, 200);
  const startCall = calls.find((call) => call.operation === 'start');
  assert.equal(startCall.value.principal.userId, USER_ID);
  assert.equal(startCall.value.principal.deviceId, DEVICE_ID);

  const status = await request(app, '/v1/auth/telegram/bot/status', { state: STATE }, 'Bearer guest-session');
  assert.equal(status.status, 200);
  assert.equal(status.body.data.code, CODE);

  const exchange = await request(app, '/v1/auth/telegram/bot/exchange', {
    code: CODE,
    state: STATE,
    code_verifier: VERIFIER,
  }, 'Bearer guest-session');
  assert.equal(exchange.status, 200);
  const exchangeCall = calls.find((call) => call.operation === 'exchange');
  assert.equal(exchangeCall.value.principal.deviceId, DEVICE_ID);
  assert.equal(exchangeCall.value.codeVerifier, VERIFIER);
});

test('non Bot-Approval routes delegate unchanged to the base application', async () => {
  const { app } = setup();
  const response = await app(new Request('https://control.ganj.test/v1/services'));
  assert.equal(response.status, 404);
  assert.equal(response.body.error.code, 'base_route');
});
