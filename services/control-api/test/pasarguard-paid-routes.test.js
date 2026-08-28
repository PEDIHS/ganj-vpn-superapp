import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createPasarGuardPaidApplication,
  PasarGuardConnectorRegistry,
  stableVirtualServerId,
} from '../src/pasarguard-paid-routes.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const PAID_ID = '30000000-0000-4000-8000-000000000001';
const FREE_ID = '30000000-0000-4000-8000-000000000002';
const STATIC_FREE_SERVER = '40000000-0000-4000-8000-000000000001';
const STATIC_PREMIUM_SERVER = '40000000-0000-4000-8000-000000000002';
const NODE_ID = 'a'.repeat(64);
const CLIENT_NONCE = 'client-nonce-device-bound-value-000000001';
const DEVICE_PROOF = 'proof-device-bound-value-000000000000000001';
const BOT_TOKEN = 'bot-sync-secret-'.padEnd(64, 'x');

function service(overrides = {}) {
  return {
    id: PAID_ID,
    userId: USER_ID,
    planId: '50000000-0000-4000-8000-000000000001',
    name: 'Bot Premium',
    status: 'active',
    tier: 'premium',
    country_code: 'DE',
    traffic_limit_bytes: 1000,
    traffic_used_bytes: 100,
    expires_at: '2027-01-01T00:00:00.000Z',
    device_limit: 2,
    allowed_protocols: ['vless'],
    deviceIds: [],
    ...overrides,
  };
}

function freeService() {
  return {
    ...service({
      id: FREE_ID,
      name: 'Free',
      tier: 'free',
      traffic_limit_bytes: 500,
      traffic_used_bytes: 0,
      device_limit: 1,
    }),
  };
}

function staticServer(id, tier) {
  return {
    id,
    code: `${tier}-01`,
    name: `${tier} static`,
    country_code: 'DE',
    city: 'Frankfurt',
    tier,
    status: 'active',
    load_ratio: 0.1,
    latency_hint_ms: 20,
    protocols: ['vless'],
  };
}

function setup({ liveStatus = 'active', used = 200, limit = 2000, rotateNode = false } = {}) {
  const binding = {
    serviceId: PAID_ID,
    userId: USER_ID,
    providerType: 'pasarguard',
    sourceKey: 'ganj-bot',
    externalServiceId: 'legacy-service-1',
    serviceUsername: 'ganj_user_1',
    connectorRef: 'pg-main',
  };
  const repo = {
    async listServices() { return [service(), freeService()]; },
    async findOwnedService(userId, id) {
      if (userId !== USER_ID || id !== PAID_ID) return null;
      return service();
    },
    async listServers() {
      return [
        staticServer(STATIC_FREE_SERVER, 'free'),
        staticServer(STATIC_PREMIUM_SERVER, 'premium'),
      ];
    },
  };
  const reservations = [];
  const upserts = [];
  const bindingStore = {
    async listForUser() { return [binding]; },
    async find(userId, serviceId) { return userId === USER_ID && serviceId === PAID_ID ? binding : null; },
    async upsert(value) { upserts.push(value); return { ...binding, ...value, userId: USER_ID, providerType: 'pasarguard' }; },
    async bindDeviceAndReserve(value) { reservations.push(value); return true; },
  };
  const liveCalls = [];
  let safeCalls = 0;
  const liveAdapter = {
    async readLiveService() {
      return {
        username: 'ganj_user_1', status: liveStatus,
        expiresAt: '1893456000', dataLimitBytes: limit, usedTrafficBytes: used,
      };
    },
    async listSafeNodes() {
      safeCalls += 1;
      liveCalls.push('safe');
      const nodeId = rotateNode && safeCalls > 1 ? 'b'.repeat(64) : NODE_ID;
      return {
        service: {
          username: 'ganj_user_1', status: liveStatus,
          expiresAt: '1893456000', dataLimitBytes: limit, usedTrafficBytes: used,
        },
        nodes: [{
          id: nodeId,
          name: 'Germany Live 01',
          protocol: 'vless',
          transport: 'tcp',
          security: 'reality',
          flow: null,
        }],
      };
    },
    async resolveConnection({ nodeId }) {
      liveCalls.push(`resolve:${nodeId}`);
      if (rotateNode) {
        const error = new Error('removed');
        error.status = 404;
        error.code = 'subscription_node_not_found';
        throw error;
      }
      return {
        service: {
          username: 'ganj_user_1', status: liveStatus,
          expiresAt: '1893456000', dataLimitBytes: limit, usedTrafficBytes: used,
        },
        connection: {
          endpoint: 'vpn.example.test',
          port: 443,
          protocol: 'vless',
          credential: '11111111-1111-4111-8111-111111111111',
          transport: { type: 'tcp' },
          security: {
            type: 'reality', server_name: 'cdn.example.test', fingerprint: 'chrome',
            public_key: 'A'.repeat(43), short_id: 'aabbccdd',
          },
          flow: null,
          shadowsocks_method: null,
        },
      };
    },
  };
  const proofCalls = [];
  const auth = {
    async authenticate(request) {
      if (request.headers.get('authorization') !== 'Bearer session') throw new Error('unauthorized');
      return { userId: USER_ID, deviceId: DEVICE_ID, authMethod: 'telegram' };
    },
    async verifyDeviceProof(value) { proofCalls.push(value); return true; },
    async sealConnectionProfile(value) {
      assert.equal(value.plaintext.credential, '11111111-1111-4111-8111-111111111111');
      return { algorithm: 'GVP1', keyVersion: 'v1', nonce: 'sealed-nonce', ciphertext: 'sealed-ciphertext' };
    },
  };
  const connectorRegistry = new PasarGuardConnectorRegistry(JSON.stringify({
    'pg-main': {
      baseUrl: 'https://panel.example.test',
      adminUsername: 'admin',
      adminPassword: 'secret',
      countryCode: 'DE',
      city: 'Frankfurt',
    },
  }));
  const app = createPasarGuardPaidApplication({
    baseApplication: async () => ({ status: 404, body: { error: { code: 'base' } } }),
    repository: repo,
    auth,
    bindingStore,
    connectorRegistry,
    liveAdapter,
    internalToken: BOT_TOKEN,
    clock: () => new Date('2026-08-28T10:00:00.000Z'),
  });
  return { app, reservations, upserts, proofCalls, liveCalls };
}

function request(app, path, { method = 'GET', body = null, authorization = 'Bearer session' } = {}) {
  const headers = new Headers();
  if (authorization) headers.set('authorization', authorization);
  if (body) headers.set('content-type', 'application/json');
  return app(new Request(`https://control.ganj.test${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  }));
}

test('paid My Services uses live PasarGuard status traffic and expiry', async () => {
  const { app } = setup({ used: 777, limit: 9000 });
  const response = await request(app, '/v1/services');
  assert.equal(response.status, 200);
  const paid = response.body.data.find((item) => item.id === PAID_ID);
  assert.equal(paid.status, 'active');
  assert.equal(paid.traffic_used_bytes, 777);
  assert.equal(paid.traffic_limit_bytes, 9000);
  assert.equal(paid.expires_at, '2030-01-01T00:00:00.000Z');
});

test('bound paid service exposes only live virtual nodes while Free keeps static registry', async () => {
  const { app } = setup();
  const response = await request(app, '/v1/servers');
  assert.equal(response.status, 200);
  const ids = response.body.data.map((item) => item.id);
  assert.ok(ids.includes(STATIC_FREE_SERVER));
  assert.ok(!ids.includes(STATIC_PREMIUM_SERVER));
  const virtualId = stableVirtualServerId(PAID_ID, NODE_ID);
  const virtual = response.body.data.find((item) => item.id === virtualId);
  assert.ok(virtual);
  assert.deepEqual(virtual.protocols, ['vless']);
  for (const forbidden of ['endpoint', 'port', 'credential', 'subscription_url', 'connection', '_nodeId']) {
    assert.equal(forbidden in virtual, false);
  }
});

test('paid profile revalidates live node, verifies exact device proof and reserves virtual grant', async () => {
  const { app, reservations, proofCalls, liveCalls } = setup();
  const serverId = stableVirtualServerId(PAID_ID, NODE_ID);
  const response = await request(app, `/v1/services/${PAID_ID}/connection-profile`, {
    method: 'POST',
    body: {
      device_id: DEVICE_ID,
      server_id: serverId,
      client_nonce: CLIENT_NONCE,
      device_proof: DEVICE_PROOF,
    },
  });
  assert.equal(response.status, 201);
  assert.equal(response.body.data.server_id, serverId);
  assert.equal(response.body.data.ciphertext, 'sealed-ciphertext');
  assert.deepEqual(liveCalls, ['safe', `resolve:${NODE_ID}`]);
  assert.equal(proofCalls.length, 1);
  assert.deepEqual(proofCalls[0].unsignedBody, {
    client_nonce: CLIENT_NONCE,
    device_id: DEVICE_ID,
    server_id: serverId,
  });
  assert.equal(reservations.length, 1);
  assert.equal(reservations[0].nodeId, NODE_ID);
  assert.equal(reservations[0].virtualServerId, serverId);
});

test('disabled or exhausted PasarGuard service cannot advertise nodes or issue a profile', async () => {
  const disabled = setup({ liveStatus: 'disabled' });
  const servers = await request(disabled.app, '/v1/servers');
  assert.equal(servers.status, 200);
  assert.equal(servers.body.data.some((item) => item.id === stableVirtualServerId(PAID_ID, NODE_ID)), false);

  const exhausted = setup({ used: 1000, limit: 1000 });
  const response = await request(exhausted.app, `/v1/services/${PAID_ID}/connection-profile`, {
    method: 'POST',
    body: {
      device_id: DEVICE_ID,
      server_id: stableVirtualServerId(PAID_ID, NODE_ID),
      client_nonce: CLIENT_NONCE,
      device_proof: DEVICE_PROOF,
    },
  });
  assert.equal(response.status, 403);
  assert.equal(exhausted.reservations.length, 0);
});

test('node rotation between listing and issuance fails closed', async () => {
  const rotated = setup({ rotateNode: true });
  const first = await request(rotated.app, '/v1/servers');
  const serverId = first.body.data.find((item) => item.code.startsWith('pg-')).id;
  const response = await request(rotated.app, `/v1/services/${PAID_ID}/connection-profile`, {
    method: 'POST',
    body: {
      device_id: DEVICE_ID,
      server_id: serverId,
      client_nonce: CLIENT_NONCE,
      device_proof: DEVICE_PROOF,
    },
  });
  assert.ok(response.status >= 400);
  assert.equal(rotated.reservations.length, 0);
});

test('Bot sync can persist locator metadata only through its internal bearer boundary', async () => {
  const { app, upserts } = setup();
  const body = {
    service_id: PAID_ID,
    source_key: 'ganj-bot',
    external_service_id: 'legacy-service-1',
    external_service_username: 'ganj_user_1',
    connector_ref: 'pg-main',
  };
  const denied = await request(app, '/v1/internal/bot/service-upstream', {
    method: 'POST', body, authorization: 'Bearer wrong',
  });
  assert.equal(denied.status, 401);
  const accepted = await request(app, '/v1/internal/bot/service-upstream', {
    method: 'POST', body, authorization: `Bearer ${BOT_TOKEN}`,
  });
  assert.equal(accepted.status, 200);
  assert.equal(upserts.length, 1);
  assert.deepEqual(Object.keys(accepted.body.data).sort(), [
    'connector_ref', 'external_service_id', 'provider_type', 'service_id', 'source_key',
  ]);
});
