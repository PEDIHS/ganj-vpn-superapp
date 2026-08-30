import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import test from 'node:test';
import {
  InMemoryTelegramBotApprovalStore,
  TelegramBotApprovalService,
} from '../src/adapters/bot-approval.js';

const USER = '10000000-0000-4000-8000-000000000001';
const DEVICE = '20000000-0000-4000-8000-000000000001';
const OTHER_DEVICE = '20000000-0000-4000-8000-000000000002';
const REDIRECT = 'https://auth.invalid/telegram/callback';
const HMAC_SECRET = 'test-only-bot-approval-secret-32-bytes-minimum';
const VERIFIER = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~abc';

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, stable(item)]));
  }
  return value;
}

function challenge(verifier) {
  return (await import('node:crypto')).createHash('sha256').update(verifier).digest('base64url');
}

function signature(timestamp, body) {
  return createHmac('sha256', HMAC_SECRET)
    .update(`${timestamp}.${JSON.stringify(stable(body))}`)
    .digest('hex');
}

function service(nowRef) {
  return new TelegramBotApprovalService({
    store: new InMemoryTelegramBotApprovalStore(),
    botUsername: 'GanjTestBot',
    redirectUris: [REDIRECT],
    hmacSecret: HMAC_SECRET,
    clock: () => new Date(nowRef.value),
  });
}

async function approvedFlow(subject = 'tg:123456') {
  const now = { value: '2026-08-30T06:00:00.000Z' };
  const sut = service(now);
  const codeChallenge = await challenge(VERIFIER);
  const started = await sut.start({
    principal: { userId: USER, deviceId: DEVICE },
    codeChallenge,
    redirectUri: REDIRECT,
  });
  const approvalToken = new URL(started.bot_url).searchParams.get('start').slice(3);
  const body = {
    approval_token: approvalToken,
    event_id: 'evt_bot_approval_00000001',
    decision: 'approve',
    telegram_subject: subject,
    telegram_username: 'ganj_test',
    telegram_display_name: 'Ganj Test',
  };
  const timestamp = Math.floor(new Date(now.value).getTime() / 1000);
  const decision = await sut.applyBotDecision({
    timestamp: String(timestamp),
    signature: signature(timestamp, body),
    body,
  });
  return { now, sut, started, body, decision };
}

test('approved request is device/state/PKCE bound and one-time', async () => {
  const { sut, started, decision } = await approvedFlow();
  assert.equal(decision.status, 'approved');
  const exchanged = await sut.consume({
    principal: { userId: USER, deviceId: DEVICE },
    requestId: started.request_id,
    state: started.state,
    codeVerifier: VERIFIER,
  });
  assert.equal(exchanged.telegramSubject, 'tg:123456');

  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: DEVICE },
      requestId: started.request_id,
      state: started.state,
      codeVerifier: VERIFIER,
    }),
    (error) => error?.code === 'bot_approval_replayed' || error?.message?.includes('already consumed'),
  );
});

test('wrong device cannot observe or exchange an approval', async () => {
  const { sut, started } = await approvedFlow();
  await assert.rejects(
    () => sut.status({ principal: { userId: USER, deviceId: OTHER_DEVICE }, requestId: started.request_id }),
    (error) => error?.status === 404,
  );
  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: OTHER_DEVICE },
      requestId: started.request_id,
      state: started.state,
      codeVerifier: VERIFIER,
    }),
    (error) => error?.status === 404,
  );
});

test('wrong state or verifier fails closed', async () => {
  const { sut, started } = await approvedFlow();
  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: DEVICE },
      requestId: started.request_id,
      state: 'z'.repeat(43),
      codeVerifier: VERIFIER,
    }),
    (error) => error?.status === 401,
  );
  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: DEVICE },
      requestId: started.request_id,
      state: started.state,
      codeVerifier: `${VERIFIER.slice(0, -1)}x`,
    }),
    (error) => error?.status === 401,
  );
});

test('denial cannot be exchanged', async () => {
  const now = { value: '2026-08-30T06:00:00.000Z' };
  const sut = service(now);
  const started = await sut.start({
    principal: { userId: USER, deviceId: DEVICE },
    codeChallenge: await challenge(VERIFIER),
    redirectUri: REDIRECT,
  });
  const approvalToken = new URL(started.bot_url).searchParams.get('start').slice(3);
  const body = {
    approval_token: approvalToken,
    event_id: 'evt_bot_approval_00000002',
    decision: 'deny',
    telegram_subject: null,
    telegram_username: null,
    telegram_display_name: null,
  };
  const timestamp = Math.floor(new Date(now.value).getTime() / 1000);
  await sut.applyBotDecision({ timestamp: String(timestamp), signature: signature(timestamp, body), body });
  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: DEVICE },
      requestId: started.request_id,
      state: started.state,
      codeVerifier: VERIFIER,
    }),
    (error) => error?.status === 403,
  );
});

test('expired approval and stale bot signatures are rejected', async () => {
  const now = { value: '2026-08-30T06:00:00.000Z' };
  const sut = service(now);
  const started = await sut.start({
    principal: { userId: USER, deviceId: DEVICE },
    codeChallenge: await challenge(VERIFIER),
    redirectUri: REDIRECT,
  });
  const approvalToken = new URL(started.bot_url).searchParams.get('start').slice(3);
  const body = {
    approval_token: approvalToken,
    event_id: 'evt_bot_approval_00000003',
    decision: 'approve',
    telegram_subject: 'tg:9',
    telegram_username: null,
    telegram_display_name: null,
  };
  const oldTimestamp = Math.floor(new Date(now.value).getTime() / 1000);
  now.value = '2026-08-30T06:20:00.000Z';
  await assert.rejects(
    () => sut.applyBotDecision({
      timestamp: String(oldTimestamp),
      signature: signature(oldTimestamp, body),
      body,
    }),
    (error) => error?.status === 401,
  );
  await assert.rejects(
    () => sut.consume({
      principal: { userId: USER, deviceId: DEVICE },
      requestId: started.request_id,
      state: started.state,
      codeVerifier: VERIFIER,
    }),
    (error) => error?.status === 410,
  );
});
