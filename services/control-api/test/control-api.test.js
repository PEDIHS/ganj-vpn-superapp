import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';
import { createApplication } from '../src/application.js';
import { createDeviceProof, createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createTestPurchaseVerifier } from '../src/adapters/test-purchase-verifier.js';
import { createTestTelegramAuthAdapter } from '../src/adapters/test-telegram-auth.js';
import { createHttpServer } from '../src/http.js';
import { createRuntime } from '../src/runtime.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-24T12:00:00.000Z');
const SECRETS = Object.freeze({
  [FIXTURES.devices.primary]: 'primary-secret',
  [FIXTURES.devices.second]: 'second-secret',
  [FIXTURES.devices.secondary]: 'secondary-secret',
});
const PURCHASE_PROOFS = Object.freeze({
  premiumOne: 'aaaaaaaaaaaaaaaa',
  premiumTwo: 'bbbbbbbbbbbbbbbb',
  vip: 'cccccccccccccccc',
});
const TOKENS = Object.freeze({
  [PURCHASE_PROOFS.premiumOne]: 'ganj.premium.30d',
  [PURCHASE_PROOFS.premiumTwo]: 'ganj.premium.30d',
  [PURCHASE_PROOFS.vip]: 'ganj.vip.90d',
});

function setup({ purchaseVerifier: purchaseOverride, playNotifications: notificationsOverride, auth: authOverride } = {}) {
  const repository = new InMemoryRepository(createSeed(NOW));
  const auth = authOverride ?? createTestAuthAdapter({ deviceSecrets: SECRETS });
  const purchaseVerifier = purchaseOverride ?? createTestPurchaseVerifier({ approvedTokens: TOKENS });
  const telegramAuth = createTestTelegramAuthAdapter();
  const playNotifications = notificationsOverride ?? { kind: 'test-only', async verifyAndDecode() { throw new Error('not configured'); } };
  const app = createApplication({ repository, auth, purchaseVerifier, telegramAuth, playNotifications, clock: () => new Date(NOW) });
  return { app, repository };
}

function request(app, method, path, {
  userId = FIXTURES.users.primary,
  deviceId = FIXTURES.devices.primary,
  body,
  idempotencyKey,
  authenticate = true,
  headers = {},
} = {}) {
  const requestHeaders = new Headers(headers);
  if (authenticate) {
    requestHeaders.set('x-test-user-id', userId);
    requestHeaders.set('x-test-device-id', deviceId);
  }
  if (idempotencyKey) requestHeaders.set('idempotency-key', idempotencyKey);
  if (body !== undefined) requestHeaders.set('content-type', 'application/json');
  return app(new Request(`http://control.test${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  }));
}

function profileBody({
  serviceId = FIXTURES.services.premium,
  deviceId = FIXTURES.devices.primary,
  serverId = FIXTURES.servers.premium,
  secret = SECRETS[deviceId],
  clientNonce = 'client-generated-nonce-with-32-bytes',
} = {}) {
  const payload = { serviceId, deviceId, serverId, clientNonce };
  return {
    device_id: deviceId,
    server_id: serverId,
    client_nonce: clientNonce,
    device_proof: createDeviceProof(secret, payload),
  };
}

async function createOrder(app, {
  planId = FIXTURES.plans.premium,
  channel = 'play',
  serviceId,
  key = 'create-order-key-00000001',
  userId = FIXTURES.users.primary,
  deviceId = FIXTURES.devices.primary,
} = {}) {
  const body = { plan_id: planId, channel };
  if (serviceId) body.service_id = serviceId;
  return request(app, 'POST', '/v1/orders', { body, idempotencyKey: key, userId, deviceId });
}

test('public plan catalog is channel-filtered and does not leak billing internals', async () => {
  const { app } = setup();
  const response = await request(app, 'GET', '/v1/store/plans?channel=play', { authenticate: false });
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data.map((plan) => plan.code), ['premium-30d', 'vip-90d']);
  assert.equal(JSON.stringify(response.body).includes('play_product_id'), false);
  assert.equal(JSON.stringify(response.body).includes('channels'), false);
});

test('health, request correlation and validation failures use safe envelopes', async () => {
  const { app } = setup();
  const health = await request(app, 'GET', '/healthz', { authenticate: false });
  assert.deepEqual(health, { status: 200, body: { status: 'ok' } });

  const requestId = '90000000-0000-4000-8000-000000000001';
  const correlated = await request(app, 'GET', '/v1/store/plans?channel=direct', {
    authenticate: false,
    headers: { 'x-request-id': requestId },
  });
  assert.equal(correlated.body.meta.request_id, requestId);

  const channel = await request(app, 'GET', '/v1/store/plans?channel=wallet', { authenticate: false });
  assert.equal(channel.status, 400);
  assert.equal(channel.body.error.code, 'invalid_channel');

  const route = await request(app, 'GET', '/v1/not-a-route');
  assert.equal(route.status, 404);
  assert.equal(route.body.error.code, 'route_not_found');
});

test('Telegram exchange is public, PKCE-shaped, and one-time through its boundary', async () => {
  const { app } = setup();
  const body = {
    code: 'test-telegram-code',
    state: 's'.repeat(32),
    code_verifier: 'v'.repeat(43),
  };
  const exchanged = await request(app, 'POST', '/v1/auth/telegram/exchange', { body, authenticate: false });
  assert.equal(exchanged.status, 200);
  assert.equal(exchanged.body.data.user.telegram_linked, true);
  assert.equal(exchanged.body.data.device_id, FIXTURES.devices.primary);
  const replay = await request(app, 'POST', '/v1/auth/telegram/exchange', { body, authenticate: false });
  assert.equal(replay.status, 400);
  assert.equal(replay.body.error.code, 'invalid_login_state');
});

test('protected routes fail closed without an authenticated principal', async () => {
  const { app } = setup();
  const response = await request(app, 'GET', '/v1/services', { authenticate: false });
  assert.equal(response.status, 401);
  assert.equal(response.body.error.code, 'unauthorized');
});

test('my services are ownership-scoped and contain no config material', async () => {
  const { app } = setup();
  const response = await request(app, 'GET', '/v1/services', {
    userId: FIXTURES.users.secondary,
    deviceId: FIXTURES.devices.secondary,
  });
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data.map((service) => service.id), [FIXTURES.services.expired]);
  assert.equal(JSON.stringify(response.body).includes('deviceIds'), false);
  assert.equal(JSON.stringify(response.body).includes('credential'), false);
});

test('server catalog is filtered by active entitlement, tier, protocol and status', async () => {
  const { app } = setup();
  const primary = await request(app, 'GET', '/v1/servers?protocol=trojan');
  assert.equal(primary.status, 200);
  assert.deepEqual(primary.body.data.map((server) => server.id), [FIXTURES.servers.premium]);
  assert.equal(JSON.stringify(primary.body).includes('connection'), false);

  const expired = await request(app, 'GET', '/v1/servers', {
    userId: FIXTURES.users.secondary,
    deviceId: FIXTURES.devices.secondary,
  });
  assert.equal(expired.status, 200);
  assert.deepEqual(expired.body.data, []);
});

test('server catalog validates filters and supports country/tier selection', async () => {
  const { app } = setup();
  const selected = await request(app, 'GET', '/v1/servers?tier=premium&country=DE');
  assert.equal(selected.status, 200);
  assert.deepEqual(selected.body.data.map((server) => server.id), [FIXTURES.servers.premium]);
  for (const [query, code] of [
    ['tier=enterprise', 'invalid_tier'],
    ['country=de', 'invalid_country'],
    ['protocol=openvpn', 'invalid_protocol'],
  ]) {
    const invalid = await request(app, 'GET', `/v1/servers?${query}`);
    assert.equal(invalid.status, 400);
    assert.equal(invalid.body.error.code, code);
  }
});

test('connection profile is device-bound, short-lived, encrypted and contains no raw config', async () => {
  const { app } = setup();
  const response = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });
  assert.equal(response.status, 201);
  assert.equal(response.body.data.server_id, FIXTURES.servers.premium);
  assert.match(response.body.data.ciphertext, /^[A-Za-z0-9+/]+=*$/);
  assert.equal(Date.parse(response.body.data.expires_at) - NOW.getTime(), 300_000);
  const serialized = JSON.stringify(response.body);
  for (const forbidden of ['endpoint', 'credential', 'uri', 'server-side-premium-secret']) {
    assert.equal(serialized.includes(forbidden), false);
  }
});

test('connection profile plaintext is a strict versioned server provisioned Xray schema', async () => {
  const observed = {};
  const delegate = createTestAuthAdapter({ deviceSecrets: SECRETS });
  const auth = {
    ...delegate,
    async sealConnectionProfile(input) {
      observed.plaintext = input.plaintext;
      observed.associatedData = input.associatedData;
      return delegate.sealConnectionProfile(input);
    },
  };
  const { app } = setup({ auth });

  const response = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });

  assert.equal(response.status, 201);
  assert.deepEqual(observed.plaintext.transport, { type: 'tcp' });
  assert.deepEqual(observed.plaintext.security, {
    type: 'tls', server_name: 'de-premium.internal.invalid', fingerprint: 'chrome',
  });
  assert.equal(observed.plaintext.schema_version, 1);
  assert.equal(observed.plaintext.protocol, 'vless');
  assert.equal(observed.plaintext.service_id, FIXTURES.services.premium);
  assert.equal(observed.plaintext.server_id, FIXTURES.servers.premium);
  assert.equal(observed.plaintext.device_id, FIXTURES.devices.primary);
  assert.equal(observed.associatedData.profileId, observed.plaintext.profile_id);
  assert.equal(observed.associatedData.expiresAt, observed.plaintext.expires_at);
});

test('misconfigured or insecure server material fails closed without leaking the resolver payload', async () => {
  const { app, repository } = setup();
  const current = repository.servers.get(FIXTURES.servers.premium);
  repository.servers.set(FIXTURES.servers.premium, {
    ...current,
    connection: {
      ...current.connection,
      security: { ...current.connection.security, allow_insecure: true },
    },
  });

  const response = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });

  assert.equal(response.status, 500);
  assert.equal(response.body.error.code, 'internal_error');
  assert.equal(JSON.stringify(response.body).includes('allow_insecure'), false);
  assert.equal(JSON.stringify(response.body).includes('credential'), false);
});

test('manual configuration fields are explicitly rejected', async () => {
  const { app } = setup();
  const response = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: { ...profileBody(), config: 'vless://manual-import' },
  });
  assert.equal(response.status, 400);
  assert.equal(response.body.error.code, 'unsupported_fields');
  assert.deepEqual(response.body.error.details.fields, ['config']);
});

test('connection profile nonce is replay-safe and grant is consumable only once', async () => {
  const { app, repository } = setup();
  const body = profileBody();
  const first = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, { body });
  assert.equal(first.status, 201);
  const replay = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, { body });
  assert.equal(replay.status, 409);
  assert.equal(replay.body.error.code, 'profile_nonce_replayed');
  assert.equal(repository.consumeConnectionProfile({
    profileId: first.body.data.profile_id,
    deviceId: FIXTURES.devices.primary,
    now: NOW,
  }), true);
  assert.equal(repository.consumeConnectionProfile({
    profileId: first.body.data.profile_id,
    deviceId: FIXTURES.devices.primary,
    now: NOW,
  }), false);
});

test('expired, exhausted and higher-tier entitlements cannot issue profiles', async () => {
  const { app } = setup();
  const expired = await request(app, 'POST', `/v1/services/${FIXTURES.services.expired}/connection-profile`, {
    userId: FIXTURES.users.secondary,
    deviceId: FIXTURES.devices.secondary,
    body: profileBody({
      serviceId: FIXTURES.services.expired,
      deviceId: FIXTURES.devices.secondary,
      serverId: FIXTURES.servers.free,
    }),
  });
  assert.equal(expired.status, 403);
  assert.equal(expired.body.error.code, 'service_expired');

  const exhausted = await request(app, 'POST', `/v1/services/${FIXTURES.services.exhausted}/connection-profile`, {
    body: profileBody({ serviceId: FIXTURES.services.exhausted, serverId: FIXTURES.servers.free }),
  });
  assert.equal(exhausted.status, 403);
  assert.equal(exhausted.body.error.code, 'traffic_exhausted');

  const vip = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody({ serverId: FIXTURES.servers.vip }),
  });
  assert.equal(vip.status, 403);
  assert.equal(vip.body.error.code, 'tier_not_entitled');
});

test('connection issuance enforces current device, proof and device limit', async () => {
  const { app, repository } = setup();
  const mismatch = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody({ deviceId: FIXTURES.devices.second }),
  });
  assert.equal(mismatch.status, 403);
  assert.equal(mismatch.body.error.code, 'device_mismatch');

  const invalidProofBody = profileBody();
  invalidProofBody.device_proof = '0'.repeat(64);
  const badProof = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, { body: invalidProofBody });
  assert.equal(badProof.status, 403);
  assert.equal(badProof.body.error.code, 'invalid_device_proof');

  const service = repository.findOwnedService(FIXTURES.users.primary, FIXTURES.services.premium);
  repository.saveService({ ...service, device_limit: 1 });
  const atLimit = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    deviceId: FIXTURES.devices.second,
    body: profileBody({ deviceId: FIXTURES.devices.second }),
  });
  assert.equal(atLimit.status, 403);
  assert.equal(atLimit.body.error.code, 'device_limit_reached');
});

test('connection issuance rejects inactive servers and policy mismatches', async () => {
  const { app, repository } = setup();
  const unavailable = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody({ serverId: FIXTURES.servers.maintenance }),
  });
  assert.equal(unavailable.status, 403);
  assert.equal(unavailable.body.error.code, 'server_unavailable');

  const service = repository.findOwnedService(FIXTURES.users.primary, FIXTURES.services.premium);
  repository.saveService({ ...service, country_code: 'NL', allowed_protocols: ['trojan'] });
  const country = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });
  assert.equal(country.status, 403);
  assert.equal(country.body.error.code, 'country_not_entitled');

  repository.saveService({ ...service, allowed_protocols: ['shadowsocks'] });
  const protocol = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });
  assert.equal(protocol.status, 403);
  assert.equal(protocol.body.error.code, 'protocol_not_entitled');

  repository.saveService({ ...service, status: 'disabled' });
  const inactive = await request(app, 'POST', `/v1/services/${FIXTURES.services.premium}/connection-profile`, {
    body: profileBody(),
  });
  assert.equal(inactive.status, 403);
  assert.equal(inactive.body.error.code, 'service_inactive');
});

test('JSON body, identifier and idempotency validation is strict', async () => {
  const { app } = setup();
  const missingType = await app(new Request('http://control.test/v1/orders', {
    method: 'POST',
    headers: {
      'x-test-user-id': FIXTURES.users.primary,
      'x-test-device-id': FIXTURES.devices.primary,
      'idempotency-key': 'valid-key-00000001',
    },
    body: '{}',
  }));
  assert.equal(missingType.status, 400);
  assert.equal(missingType.body.error.code, 'invalid_content_type');

  const invalidJson = await app(new Request('http://control.test/v1/orders', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-test-user-id': FIXTURES.users.primary,
      'x-test-device-id': FIXTURES.devices.primary,
      'idempotency-key': 'valid-key-00000002',
    },
    body: '{',
  }));
  assert.equal(invalidJson.status, 400);
  assert.equal(invalidJson.body.error.code, 'invalid_json');

  const arrayBody = await app(new Request('http://control.test/v1/orders', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-test-user-id': FIXTURES.users.primary,
      'x-test-device-id': FIXTURES.devices.primary,
      'idempotency-key': 'valid-key-00000003',
    },
    body: '[]',
  }));
  assert.equal(arrayBody.status, 400);
  assert.equal(arrayBody.body.error.code, 'invalid_request');

  const oversized = await app(new Request('http://control.test/v1/orders', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'content-length': '40000',
      'x-test-user-id': FIXTURES.users.primary,
      'x-test-device-id': FIXTURES.devices.primary,
      'idempotency-key': 'valid-key-00000004',
    },
    body: '{}',
  }));
  assert.equal(oversized.status, 413);

  const badId = await request(app, 'GET', '/v1/services/not-a-uuid');
  assert.equal(badId.status, 400);
  const noKey = await request(app, 'POST', '/v1/orders', {
    body: { plan_id: FIXTURES.plans.premium, channel: 'play' },
  });
  assert.equal(noKey.status, 400);
});

test('order creation is idempotent and conflicting payload reuse is rejected', async () => {
  const { app } = setup();
  const first = await createOrder(app);
  const replay = await createOrder(app);
  assert.equal(first.status, 201);
  assert.equal(replay.body.data.id, first.body.data.id);

  const conflict = await createOrder(app, { planId: FIXTURES.plans.vip });
  assert.equal(conflict.status, 409);
  assert.equal(conflict.body.error.code, 'idempotency_conflict');
});

test('order status does not reveal another user order', async () => {
  const { app } = setup();
  const created = await createOrder(app);
  const response = await request(app, 'GET', `/v1/orders/${created.body.data.id}`, {
    userId: FIXTURES.users.secondary,
    deviceId: FIXTURES.devices.secondary,
  });
  assert.equal(response.status, 404);
  assert.equal(response.body.error.code, 'order_not_found');
});

test('orders reject unavailable plans, invalid channels and foreign renewal targets', async () => {
  const { app } = setup();
  const invalidChannel = await createOrder(app, { channel: 'crypto', key: 'invalid-channel-key-0001' });
  assert.equal(invalidChannel.status, 400);
  assert.equal(invalidChannel.body.error.code, 'invalid_channel');

  const unavailable = await createOrder(app, {
    planId: '30000000-0000-4000-8000-000000000009',
    key: 'dddddddddddddddd',
  });
  assert.equal(unavailable.status, 400);
  assert.equal(unavailable.body.error.code, 'plan_unavailable');

  const foreign = await createOrder(app, {
    serviceId: FIXTURES.services.expired,
    key: 'foreign-service-key-00001',
  });
  assert.equal(foreign.status, 404);
  assert.equal(foreign.body.error.code, 'service_not_found');
});

test('Play verification fulfills a new entitlement only after server-side verification', async () => {
  const { app } = setup();
  const created = await createOrder(app);
  const before = await request(app, 'GET', '/v1/services');
  const verified = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-order-key-00000001',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumOne,
    },
  });
  assert.equal(verified.status, 200);
  assert.equal(verified.body.data.status, 'fulfilled');
  assert.match(verified.body.data.entitlement_service_id, /^[0-9a-f-]{36}$/);
  const after = await request(app, 'GET', '/v1/services');
  assert.equal(after.body.data.length, before.body.data.length + 1);

  const replay = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-order-key-00000001',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumOne,
    },
  });
  assert.deepEqual(replay.body.data, verified.body.data);
});

test('PENDING Play purchase never creates an entitlement', async () => {
  const pendingVerifier = {
    async verifyPlayPurchase() {
      return {
        valid: false,
        entitled: false,
        state: 'SUBSCRIPTION_STATE_PENDING',
        productId: 'ganj.premium.30d',
        externalTransactionId: null,
        requiresAcknowledgement: false,
      };
    },
    async getPlaySubscriptionState() { return this.verifyPlayPurchase(); },
    async acknowledgePlayPurchase() {},
    async cancelPlaySubscription() {},
    async revokePlaySubscription() {},
  };
  const { app } = setup({ purchaseVerifier: pendingVerifier });
  const before = await request(app, 'GET', '/v1/services');
  const created = await createOrder(app);
  const verified = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'pending-play-verify-key-001',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: 'pending-play-token-00000001',
    },
  });
  assert.equal(verified.status, 400);
  assert.equal(verified.body.error.code, 'purchase_not_verified');
  const after = await request(app, 'GET', '/v1/services');
  assert.equal(after.body.data.length, before.body.data.length);
});

test('new-token acknowledgement occurs after grant and is retryable with the same idempotency key', async () => {
  let repository;
  let orderId;
  let acknowledgementCalls = 0;
  const purchaseToken = 'aaaaaaaaaaaaaaaa';
  const productId = 'ganj.premium.30d';
  const base = createTestPurchaseVerifier({ approvedTokens: { [purchaseToken]: productId } });
  const verifier = {
    ...base,
    async verifyPlayPurchase(input) {
      return { ...await base.verifyPlayPurchase(input), requiresAcknowledgement: true };
    },
    async acknowledgePlayPurchase() {
      acknowledgementCalls += 1;
      const stored = repository.findOwnedOrder(FIXTURES.users.primary, orderId);
      assert.equal(stored.status, 'fulfilled');
      if (acknowledgementCalls === 1) throw new Error('transient Google failure');
    },
  };
  const context = setup({ purchaseVerifier: verifier });
  repository = context.repository;
  const created = await createOrder(context.app);
  orderId = created.body.data.id;
  const input = {
    idempotencyKey: 'ack-after-grant-key-00001',
    body: {
      order_id: orderId,
      product_id: 'ganj.premium.30d',
      purchase_token: purchaseToken,
    },
  };
  const first = await request(context.app, 'POST', '/v1/billing/play/verify', input);
  assert.equal(first.status, 503);
  assert.equal(first.body.error.code, 'play_acknowledgement_pending');
  const serviceCount = (await request(context.app, 'GET', '/v1/services')).body.data.length;
  const retry = await request(context.app, 'POST', '/v1/billing/play/verify', input);
  assert.equal(retry.status, 200);
  assert.equal(acknowledgementCalls, 2);
  assert.equal((await request(context.app, 'GET', '/v1/services')).body.data.length, serviceCount);
});

test('signed RTDN is only a signal; authoritative Publisher state is re-queried idempotently', async () => {
  const token = 'rtdn-approved-token-00000001';
  const base = createTestPurchaseVerifier({ approvedTokens: { [token]: 'ganj.premium.30d' } });
  let stateQueries = 0;
  const verifier = {
    ...base,
    async getPlaySubscriptionState() {
      stateQueries += 1;
      return {
        valid: false,
        entitled: false,
        state: 'SUBSCRIPTION_STATE_EXPIRED',
        productId: 'ganj.premium.30d',
        expiresAt: '2026-08-23T12:00:00Z',
      };
    },
  };
  const notifications = {
    async verifyAndDecode() {
      return {
        eventId: 'rtdn-event-1',
        eventType: 'subscription:13',
        purchaseToken: token,
        productId: 'ganj.premium.30d',
        payloadDigest: 'a'.repeat(64),
        receivedAt: NOW,
      };
    },
  };
  const { app, repository } = setup({ purchaseVerifier: verifier, playNotifications: notifications });
  const created = await createOrder(app);
  const granted = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'eeeeeeeeeeeeeeee',
    body: { order_id: created.body.data.id, product_id: 'ganj.premium.30d', purchase_token: token },
  });
  assert.equal(granted.status, 200);
  const callback = await request(app, 'POST', '/v1/billing/play/rtdn', { body: { signed: 'envelope' }, authenticate: false });
  assert.equal(callback.status, 200);
  assert.equal(stateQueries, 1);
  const service = repository.findOwnedService(FIXTURES.users.primary, granted.body.data.entitlement_service_id);
  assert.equal(service.status, 'expired');
  const replay = await request(app, 'POST', '/v1/billing/play/rtdn', { body: { signed: 'envelope' }, authenticate: false });
  assert.equal(replay.status, 200);
  assert.equal(replay.body.data.replayed, true);
  assert.equal(stateQueries, 1);
});

test('unverified or mismatched purchases never grant entitlement', async () => {
  const { app } = setup();
  const created = await createOrder(app);
  const mismatch = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'eeeeeeeeeeeeeeee',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.vip.90d',
      purchase_token: PURCHASE_PROOFS.vip,
    },
  });
  assert.equal(mismatch.status, 400);
  assert.equal(mismatch.body.error.code, 'product_mismatch');

  const rejected = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-rejected-key-000001',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: 'not-approved-token-000000001',
    },
  });
  assert.equal(rejected.status, 400);
  assert.equal(rejected.body.error.code, 'purchase_not_verified');
  const status = await request(app, 'GET', `/v1/orders/${created.body.data.id}`);
  assert.equal(status.body.data.status, 'pending');
  assert.equal(status.body.data.entitlement_service_id, null);
});

test('a verified purchase token cannot fulfill two orders', async () => {
  const { app } = setup();
  const first = await createOrder(app, { key: 'create-order-key-token-one' });
  const second = await createOrder(app, { key: 'create-order-key-token-two' });
  const verification = {
    product_id: 'ganj.premium.30d',
    purchase_token: PURCHASE_PROOFS.premiumOne,
  };
  const accepted = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-token-key-one-000001',
    body: { ...verification, order_id: first.body.data.id },
  });
  assert.equal(accepted.status, 200);
  const reused = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-token-key-two-000001',
    body: { ...verification, order_id: second.body.data.id },
  });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'purchase_token_reused');
});

test('verification enforces payment channel and fulfilled-order token identity', async () => {
  const { app } = setup();
  const direct = await createOrder(app, { channel: 'direct', key: 'direct-order-key-0000001' });
  const wrongChannel = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'wrong-channel-verify-00001',
    body: {
      order_id: direct.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumOne,
    },
  });
  assert.equal(wrongChannel.status, 409);
  assert.equal(wrongChannel.body.error.code, 'wrong_payment_channel');

  const play = await createOrder(app, { key: 'fulfilled-order-key-00001' });
  const first = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'fulfilled-verify-key-00001',
    body: {
      order_id: play.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumOne,
    },
  });
  assert.equal(first.status, 200);
  const differentToken = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'fulfilled-verify-key-00002',
    body: {
      order_id: play.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumTwo,
    },
  });
  assert.equal(differentToken.status, 409);
  assert.equal(differentToken.body.error.code, 'order_already_fulfilled');
});

test('renewal only targets an owned service and extends its entitlement', async () => {
  const { app, repository } = setup();
  const before = repository.findOwnedService(FIXTURES.users.primary, FIXTURES.services.premium);
  const created = await createOrder(app, {
    serviceId: FIXTURES.services.premium,
    key: 'create-renewal-key-0000001',
  });
  const verified = await request(app, 'POST', '/v1/billing/play/verify', {
    idempotencyKey: 'verify-renewal-key-0000001',
    body: {
      order_id: created.body.data.id,
      product_id: 'ganj.premium.30d',
      purchase_token: PURCHASE_PROOFS.premiumTwo,
    },
  });
  assert.equal(verified.status, 200);
  assert.equal(verified.body.data.entitlement_service_id, FIXTURES.services.premium);
  const after = repository.findOwnedService(FIXTURES.users.primary, FIXTURES.services.premium);
  assert.equal(Date.parse(after.expires_at) - Date.parse(before.expires_at), 30 * 86_400_000);
  assert.equal(after.traffic_used_bytes, 0);
});

test('runtime is fail-closed in production and rejects test adapters', async () => {
  await assert.rejects(() => createRuntime({ CONTROL_API_ADAPTER_MODE: 'production' }), /adapter module is required/);
  await assert.rejects(
    () => createRuntime({ CONTROL_API_ADAPTER_MODE: 'test', NODE_ENV: 'production' }),
    /forbidden/,
  );
  await assert.rejects(() => createRuntime({ CONTROL_API_ADAPTER_MODE: 'surprise' }), /Unsupported/);
});

test('application rejects incomplete ports and masks unexpected adapter failures', async () => {
  const { repository } = setup();
  assert.throws(() => createApplication({ repository: {}, auth: {}, purchaseVerifier: {} }), /repository adapter/);
  const app = createApplication({
    repository,
    auth: {
      authenticate() { throw new Error('sensitive adapter detail'); },
      verifyDeviceProof() {},
      sealConnectionProfile() {},
    },
    purchaseVerifier: {
      verifyPlayPurchase() {},
      getPlaySubscriptionState() {},
      acknowledgePlayPurchase() {},
      cancelPlaySubscription() {},
      revokePlaySubscription() {},
    },
    telegramAuth: { exchangeAuthorizationCode() {} },
    playNotifications: { verifyAndDecode() {} },
    clock: () => new Date(NOW),
  });
  const response = await request(app, 'GET', '/v1/services');
  assert.equal(response.status, 500);
  assert.equal(response.body.error.code, 'internal_error');
  assert.equal(JSON.stringify(response.body).includes('sensitive adapter detail'), false);
});

test('HTTP boundary serves the vertical slice with security headers', async (t) => {
  const { app } = setup();
  const server = createHttpServer(app, { logger: { info() {}, error() {} } });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())));
  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/v1/store/plans?channel=play`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
  const payload = await response.json();
  assert.equal(payload.error, null);
});

test('HTTP boundary masks failures before the application boundary', async (t) => {
  const server = createHttpServer(async () => { throw new Error('sensitive transport failure'); }, { logger: { info() {}, error() {} } });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())));
  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/healthz`);
  assert.equal(response.status, 500);
  const payload = await response.json();
  assert.equal(payload.error.code, 'http_boundary_error');
  assert.equal(JSON.stringify(payload).includes('sensitive transport failure'), false);
});
