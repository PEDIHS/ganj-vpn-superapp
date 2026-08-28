import assert from 'node:assert/strict';
import test from 'node:test';
import { createApplication } from '../src/application.js';
import { createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createTestPurchaseVerifier } from '../src/adapters/test-purchase-verifier.js';
import { createTestTelegramAuthAdapter } from '../src/adapters/test-telegram-auth.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T03:00:00.000Z');
const DEVICE_SECRET = 'auth-route-device-secret';

function setup() {
  const calls = [];
  const authSession = {
    kind: 'auth-route-test',
    jwks() {
      calls.push({ operation: 'jwks' });
      return { keys: [{ kty: 'OKP', crv: 'Ed25519', x: 'A'.repeat(43), kid: 'auth-v1', alg: 'EdDSA', use: 'sig' }] };
    },
    async guest(value) {
      calls.push({ operation: 'guest', value });
      return {
        user_id: FIXTURES.users.primary,
        device_id: value.deviceId,
        access_token: 'route-access-token',
        access_token_expires_at: new Date(NOW.getTime() + 900_000).toISOString(),
        refresh_token: 'r'.repeat(43),
        refresh_token_expires_at: new Date(NOW.getTime() + 86_400_000).toISOString(),
        token_type: 'Bearer',
      };
    },
    async beginTelegram(value) {
      calls.push({ operation: 'telegram-start', value });
      return {
        authorization_url: 'https://oauth.telegram.org/auth?state=test',
        state: 's'.repeat(43),
        expires_at: new Date(NOW.getTime() + 600_000).toISOString(),
      };
    },
    async refresh(value) {
      calls.push({ operation: 'refresh', value });
      return {
        user_id: FIXTURES.users.primary,
        device_id: value.deviceId,
        access_token: 'rotated-access-token',
        access_token_expires_at: new Date(NOW.getTime() + 900_000).toISOString(),
        refresh_token: 'n'.repeat(43),
        refresh_token_expires_at: new Date(NOW.getTime() + 86_400_000).toISOString(),
        token_type: 'Bearer',
      };
    },
    async logout(principal) {
      calls.push({ operation: 'logout', principal });
      return { logged_out: true };
    },
  };
  const repository = new InMemoryRepository(createSeed(NOW));
  const auth = createTestAuthAdapter({
    deviceSecrets: { [FIXTURES.devices.primary]: DEVICE_SECRET },
  });
  const app = createApplication({
    repository,
    auth,
    authSession,
    telegramAuth: createTestTelegramAuthAdapter(),
    purchaseVerifier: createTestPurchaseVerifier({ approvedTokens: {} }),
    playNotifications: { kind: 'test-only', async verifyAndDecode() { throw new Error('not configured'); } },
    clock: () => new Date(NOW),
  });
  return { app, calls };
}

function request(app, method, path, { body, authenticate = false } = {}) {
  const headers = new Headers();
  if (authenticate) {
    headers.set('x-test-user-id', FIXTURES.users.primary);
    headers.set('x-test-device-id', FIXTURES.devices.primary);
  }
  if (body !== undefined) headers.set('content-type', 'application/json');
  return app(new Request(`http://control.test${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}

function guestBody() {
  return {
    device_id: FIXTURES.devices.primary,
    key_version: 'v1',
    signing_public_key_spki: 'S'.repeat(96),
    encryption_public_key_raw: 'E'.repeat(43),
    device_proof: 'P'.repeat(96),
  };
}

test('JWKS endpoint is public and exposes only the session verification set', async () => {
  const { app, calls } = setup();
  const response = await request(app, 'GET', '/.well-known/jwks.json');
  assert.equal(response.status, 200);
  assert.equal(response.body.keys[0].alg, 'EdDSA');
  assert.equal(calls.at(-1).operation, 'jwks');
});

test('guest route accepts the canonical flat device identity and proof contract', async () => {
  const { app, calls } = setup();
  const body = guestBody();
  const response = await request(app, 'POST', '/v1/auth/guest', { body });
  assert.equal(response.status, 201);
  assert.equal(response.body.data.device_id, FIXTURES.devices.primary);
  const call = calls.find((item) => item.operation === 'guest');
  assert.deepEqual(call.value, {
    deviceId: body.device_id,
    keyVersion: body.key_version,
    signingPublicKeySpki: body.signing_public_key_spki,
    encryptionPublicKeyRaw: body.encryption_public_key_raw,
    deviceProof: body.device_proof,
  });
});

test('guest route rejects stale nested device shapes instead of ambiguously accepting two contracts', async () => {
  const { app, calls } = setup();
  const response = await request(app, 'POST', '/v1/auth/guest', {
    body: { device: guestBody() },
  });
  assert.equal(response.status, 400);
  assert.equal(calls.some((item) => item.operation === 'guest'), false);
});

test('refresh is public but remains device-bound and proof-bound', async () => {
  const { app, calls } = setup();
  const body = {
    refresh_token: 'r'.repeat(43),
    device_id: FIXTURES.devices.primary,
    device_proof: 'P'.repeat(96),
  };
  const response = await request(app, 'POST', '/v1/auth/refresh', { body });
  assert.equal(response.status, 200);
  assert.equal(response.body.data.refresh_token, 'n'.repeat(43));
  const call = calls.find((item) => item.operation === 'refresh');
  assert.equal(call.value.deviceId, FIXTURES.devices.primary);
  assert.equal(call.value.deviceProof, body.device_proof);
});

test('Telegram authorization start is bound to an authenticated guest/device session', async () => {
  const { app, calls } = setup();
  const body = {
    code_challenge: 'C'.repeat(43),
    redirect_uri: 'ganjvpn://oauth/telegram',
  };
  const anonymous = await request(app, 'POST', '/v1/auth/telegram/start', { body });
  assert.equal(anonymous.status, 401);
  assert.equal(calls.some((item) => item.operation === 'telegram-start'), false);

  const authenticated = await request(app, 'POST', '/v1/auth/telegram/start', { body, authenticate: true });
  assert.equal(authenticated.status, 200);
  const call = calls.find((item) => item.operation === 'telegram-start');
  assert.equal(call.value.principal.userId, FIXTURES.users.primary);
  assert.equal(call.value.principal.deviceId, FIXTURES.devices.primary);
  assert.equal(call.value.codeChallenge, body.code_challenge);
});

test('logout is authenticated and delegates revocation to the session boundary', async () => {
  const { app, calls } = setup();
  const anonymous = await request(app, 'POST', '/v1/auth/logout');
  assert.equal(anonymous.status, 401);

  const response = await request(app, 'POST', '/v1/auth/logout', { authenticate: true });
  assert.equal(response.status, 200);
  assert.equal(response.body.data.logged_out, true);
  const call = calls.find((item) => item.operation === 'logout');
  assert.equal(call.principal.userId, FIXTURES.users.primary);
  assert.equal(call.principal.deviceId, FIXTURES.devices.primary);
});
