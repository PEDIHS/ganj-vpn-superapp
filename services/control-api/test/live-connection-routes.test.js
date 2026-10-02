import assert from 'node:assert/strict';
import test from 'node:test';
import { createLiveConnectionRouter } from '../src/live-connection-routes.js';

const NOW = new Date('2026-09-12T06:00:00.000Z');
const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const SERVICE_ID = '30000000-0000-4000-8000-000000000001';
const SERVER_ID = '40000000-0000-4000-8000-000000000001';

function service(overrides = {}) {
  return {
    id: SERVICE_ID,
    userId: USER_ID,
    status: 'active',
    tier: 'premium',
    country_code: null,
    traffic_limit_bytes: 10_000,
    traffic_used_bytes: 100,
    expires_at: '2026-10-12T06:00:00.000Z',
    device_limit: 2,
    deviceIds: [],
    allowed_protocols: ['vless', 'trojan'],
    ...overrides,
  };
}

function server(overrides = {}) {
  return {
    id: SERVER_ID,
    code: 'legacy-aabbccddeeff',
    name: 'Germany Premium',
    country_code: 'DE',
    city: 'Frankfurt',
    tier: 'premium',
    status: 'active',
    load_ratio: null,
    latency_hint_ms: null,
    protocols: ['vless'],
    connection: {
      endpoint: 'edge.example.com',
      port: 443,
      protocol: 'vless',
      credential: '11111111-1111-4111-8111-111111111111',
      transport: { type: 'tcp' },
      security: { type: 'tls', server_name: 'edge.example.com', fingerprint: 'chrome' },
    },
    ...overrides,
  };
}

function fixture({ currentService = service(), currentServer = server(), reserve = true } = {}) {
  const observed = { sealed: null, proof: null, saved: null, reserved: null, listCalls: 0, resolveCalls: 0 };
  const repository = {
    async listServices(userId) {
      assert.equal(userId, USER_ID);
      return [currentService];
    },
    async findOwnedService(userId, id) {
      return userId === USER_ID && id === SERVICE_ID ? currentService : null;
    },
    async transaction(callback) { return callback(); },
    async saveService(value) { observed.saved = value; return value; },
    async reserveConnectionProfile(value) { observed.reserved = value; return reserve; },
  };
  const connectionSource = {
    async listServers({ principal, services }) {
      observed.listCalls += 1;
      assert.equal(principal.userId, USER_ID);
      assert.equal(services[0].id, SERVICE_ID);
      return [currentServer];
    },
    async resolveServer({ principal, service: owned, serverId }) {
      observed.resolveCalls += 1;
      assert.equal(principal.userId, USER_ID);
      assert.equal(owned.id, SERVICE_ID);
      return serverId === SERVER_ID ? currentServer : null;
    },
  };
  const auth = {
    async verifyDeviceProof(input) { observed.proof = input; return true; },
    async sealConnectionProfile(input) {
      observed.sealed = input;
      return { algorithm: 'GVP1-X25519-HKDF-SHA256-A256GCM', keyVersion: 'v1', nonce: 'bm9uY2U=', ciphertext: 'Y2lwaGVy' };
    },
  };
  const route = createLiveConnectionRouter({ auth, repository, connectionSource, clock: () => new Date(NOW) });
  return { route, observed };
}

const principal = { userId: USER_ID, deviceId: DEVICE_ID };

function call(route, method, path, body) {
  const headers = new Headers();
  if (body !== undefined) headers.set('content-type', 'application/json');
  const request = new Request(`https://api.example.test${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return route({ request, url: new URL(request.url), principal, requestId: '50000000-0000-4000-8000-000000000001' });
}

test('live catalog exposes entitled server metadata without raw connection material', async () => {
  const { route, observed } = fixture();
  const response = await call(route, 'GET', '/v1/servers?protocol=vless&country=DE&tier=premium');
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.data.map((item) => item.id), [SERVER_ID]);
  assert.equal(response.body.data[0].name, 'Germany Premium');
  assert.equal(JSON.stringify(response.body).includes('credential'), false);
  assert.equal(JSON.stringify(response.body).includes('endpoint'), false);
  assert.equal(observed.listCalls, 1);

  const filtered = await call(route, 'GET', '/v1/servers?country=US');
  assert.deepEqual(filtered.body.data, []);
  await assert.rejects(() => call(route, 'GET', '/v1/servers?protocol=openvpn'), /protocol is invalid/);
});

test('live profile is device-bound, sealed, reserves nonce and binds the active device', async () => {
  const { route, observed } = fixture();
  const body = {
    device_id: DEVICE_ID,
    server_id: SERVER_ID,
    client_nonce: 'client-generated-nonce-000000000001',
    device_proof: 'gdp1-proof-material-that-is-long-enough-for-route',
  };
  const response = await call(route, 'POST', `/v1/services/${SERVICE_ID}/connection-profile`, body);
  assert.equal(response.status, 201);
  assert.equal(response.body.data.server_id, SERVER_ID);
  assert.equal(JSON.stringify(response.body).includes('credential'), false);
  assert.equal(observed.proof.principal.deviceId, DEVICE_ID);
  assert.deepEqual(observed.proof.unsignedBody, {
    client_nonce: body.client_nonce,
    device_id: DEVICE_ID,
    server_id: SERVER_ID,
  });
  assert.equal(observed.sealed.plaintext.protocol, 'vless');
  assert.equal(observed.sealed.plaintext.endpoint, 'edge.example.com');
  assert.equal(observed.sealed.plaintext.service_id, SERVICE_ID);
  assert.equal(observed.saved.deviceIds.includes(DEVICE_ID), true);
  assert.equal(observed.reserved.serviceId, SERVICE_ID);
  assert.equal(observed.reserved.deviceId, DEVICE_ID);
  assert.equal(observed.resolveCalls, 2);
});

test('live profile fails closed for wrong device, unavailable server, exhausted service and replayed nonce', async () => {
  const baseBody = {
    device_id: DEVICE_ID,
    server_id: SERVER_ID,
    client_nonce: 'client-generated-nonce-000000000002',
    device_proof: 'gdp1-proof-material-that-is-long-enough-for-route',
  };

  const wrongDevice = fixture();
  await assert.rejects(() => call(wrongDevice.route, 'POST', `/v1/services/${SERVICE_ID}/connection-profile`, {
    ...baseBody,
    device_id: '60000000-0000-4000-8000-000000000001',
  }), /authenticated device/);

  const missingServer = fixture();
  await assert.rejects(() => call(missingServer.route, 'POST', `/v1/services/${SERVICE_ID}/connection-profile`, {
    ...baseBody,
    server_id: '70000000-0000-4000-8000-000000000001',
  }), /unavailable/);

  const exhausted = fixture({ currentService: service({ traffic_used_bytes: 10_000 }) });
  await assert.rejects(() => call(exhausted.route, 'POST', `/v1/services/${SERVICE_ID}/connection-profile`, baseBody), /not active/);

  const replay = fixture({ reserve: false });
  await assert.rejects(() => call(replay.route, 'POST', `/v1/services/${SERVICE_ID}/connection-profile`, baseBody), /already used/);
});
