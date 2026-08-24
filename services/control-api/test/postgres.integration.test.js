import assert from 'node:assert/strict';
import test from 'node:test';
import { createApplication } from '../src/application.js';
import { createDeviceProof, createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createTestPurchaseVerifier } from '../src/adapters/test-purchase-verifier.js';
import { createTestTelegramAuthAdapter } from '../src/adapters/test-telegram-auth.js';
import { PostgresRepository } from '../src/adapters/postgres.js';
import { FIXTURES } from '../src/repository.js';

const databaseUrl = process.env.TEST_DATABASE_URL;

test('PostgreSQL repository enforces the subscription vertical slice atomically', { skip: !databaseUrl }, async () => {
  const { Pool } = await import('pg');
  const pool = new Pool({ connectionString: databaseUrl, max: 5 });
  const repository = new PostgresRepository({
    pool,
    serverSecretResolver: {
      kind: 'test-only',
      async resolveServerConnection() {
        return { endpoint: 'integration.internal.invalid', port: 443, protocol: 'vless', credential: 'integration-only-credential' };
      },
    },
  });
  const secret = 'postgres-integration-device-secret';
  const auth = createTestAuthAdapter({ deviceSecrets: { [FIXTURES.devices.primary]: secret } });
  const purchaseToken = 'bbbbbbbbbbbbbbbb';
  const productId = 'ganj.premium.30d';
  const purchaseVerifier = createTestPurchaseVerifier({
    approvedTokens: { [purchaseToken]: productId },
  });
  const app = createApplication({
    repository,
    auth,
    telegramAuth: createTestTelegramAuthAdapter(),
    purchaseVerifier,
    playNotifications: { async verifyAndDecode() { throw new Error('not used'); } },
  });
  const headers = {
    'content-type': 'application/json',
    'x-test-user-id': FIXTURES.users.primary,
    'x-test-device-id': FIXTURES.devices.primary,
  };
  const call = (method, path, body, extra = {}) => app(new Request(`http://integration.test${path}`, {
    method,
    headers: { ...headers, ...extra },
    body: body === undefined ? undefined : JSON.stringify(body),
  }));

  try {
    const services = await call('GET', '/v1/services');
    assert.equal(services.status, 200);
    assert.deepEqual(services.body.data.map((item) => item.id), [FIXTURES.services.premium]);

    const clientNonce = 'postgres-client-nonce-at-least-32-bytes';
    const proofPayload = {
      serviceId: FIXTURES.services.premium,
      deviceId: FIXTURES.devices.primary,
      serverId: FIXTURES.servers.premium,
      clientNonce,
    };
    const profileBody = {
      device_id: FIXTURES.devices.primary,
      server_id: FIXTURES.servers.premium,
      client_nonce: clientNonce,
      device_proof: createDeviceProof(secret, proofPayload),
    };
    const profile = await call('POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, profileBody);
    assert.equal(profile.status, 201);
    const replay = await call('POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, profileBody);
    assert.equal(replay.status, 409);
    assert.equal(replay.body.error.code, 'profile_nonce_replayed');
    assert.equal(await repository.consumeConnectionProfile({
      profileId: profile.body.data.profile_id,
      deviceId: FIXTURES.devices.primary,
    }), true);
    assert.equal(await repository.consumeConnectionProfile({
      profileId: profile.body.data.profile_id,
      deviceId: FIXTURES.devices.primary,
    }), false);

    const orderRequest = {
      plan_id: FIXTURES.plans.premium,
      channel: 'play',
    };
    const [first, concurrent] = await Promise.all([
      call('POST', '/v1/orders', orderRequest, { 'idempotency-key': 'postgres-order-idempotency-01' }),
      call('POST', '/v1/orders', orderRequest, { 'idempotency-key': 'postgres-order-idempotency-01' }),
    ]);
    assert.equal(first.status, 201);
    assert.equal(concurrent.status, 201);
    assert.equal(first.body.data.id, concurrent.body.data.id);

    const verified = await call('POST', '/v1/billing/play/verify', {
      order_id: first.body.data.id,
      product_id: productId,
      purchase_token: purchaseToken,
    }, { 'idempotency-key': 'postgres-verify-idempotency-1' });
    assert.equal(verified.status, 200);
    assert.equal(verified.body.data.status, 'fulfilled');

    const foreign = await repository.findOwnedOrder(FIXTURES.users.secondary, first.body.data.id);
    assert.equal(foreign, null);
  } finally {
    await repository.close();
  }
});
