import assert from 'node:assert/strict';
import test from 'node:test';
import { createLiveConnectionRouter } from '../src/live-connection-routes.js';

const U = '10000000-0000-4000-8000-000000000001';
const D = '20000000-0000-4000-8000-000000000001';
const S = '30000000-0000-4000-8000-000000000001';
const V = '40000000-0000-4000-8000-000000000001';
const now = new Date('2026-09-12T06:00:00.000Z');
const entitlement = {
  id: S, status: 'active', tier: 'premium', country_code: null,
  traffic_limit_bytes: null, traffic_used_bytes: 0, expires_at: null,
  device_limit: 1, deviceIds: [D], allowed_protocols: ['vless'],
};
const liveServer = {
  id: V, code: 'live-one', name: 'Live One', country_code: 'DE', city: null,
  tier: 'premium', status: 'active', load_ratio: null, latency_hint_ms: null,
  protocols: ['vless'],
  connection: {
    endpoint: 'edge.example.com', port: 443, protocol: 'vless',
    credential: '11111111-1111-4111-8111-111111111111',
    transport: { type: 'tcp' },
    security: { type: 'tls', server_name: 'edge.example.com', fingerprint: 'chrome' },
  },
};

function makeRoute({ proofValid = true, server = liveServer, service = entitlement } = {}) {
  const repository = {
    async listServices() { return [service]; },
    async findOwnedService(userId, id) { return userId === U && id === S ? service : null; },
    async transaction(fn) { return fn(); },
    async saveService(value) { return value; },
    async reserveConnectionProfile() { return true; },
  };
  const connectionSource = {
    async listServers() { return [server]; },
    async resolveServer({ serverId }) { return serverId === V ? server : null; },
  };
  const auth = {
    async verifyDeviceProof() { return proofValid; },
    async sealConnectionProfile() {
      return { algorithm: 'GVP1-X25519-HKDF-SHA256-A256GCM', keyVersion: 'v1', nonce: 'bm9uY2U=', ciphertext: 'Y2lwaGVy' };
    },
  };
  return createLiveConnectionRouter({ auth, repository, connectionSource, clock: () => new Date(now) });
}

function invoke(route, method, path, body) {
  const request = new Request(`https://api.example.test${path}`, {
    method,
    headers: body ? { 'content-type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  return route({ request, url: new URL(request.url), principal: { userId: U, deviceId: D }, requestId: V });
}

const body = {
  device_id: D, server_id: V,
  client_nonce: 'client-generated-nonce-000000000003',
  device_proof: 'gdp1-proof-material-that-is-long-enough-for-route',
};

test('live catalog validates tier and country and enforces tier entitlement', async () => {
  const route = makeRoute();
  await assert.rejects(() => invoke(route, 'GET', '/v1/servers?tier=enterprise'), /tier is invalid/);
  await assert.rejects(() => invoke(route, 'GET', '/v1/servers?country=de'), /country/);
  const vipOnly = makeRoute({ server: { ...liveServer, tier: 'vip' } });
  const response = await invoke(vipOnly, 'GET', '/v1/servers');
  assert.deepEqual(response.body.data, []);
});

test('live profile rejects invalid proof and insecure or malformed connection policy', async () => {
  const proofRejected = makeRoute({ proofValid: false });
  await assert.rejects(() => invoke(proofRejected, 'POST', `/v1/services/${S}/connection-profile`, body), /proof is invalid/);

  const insecure = makeRoute({
    server: {
      ...liveServer,
      connection: { ...liveServer.connection, security: { ...liveServer.connection.security, allow_insecure: true } },
    },
  });
  await assert.rejects(() => invoke(insecure, 'POST', `/v1/services/${S}/connection-profile`, body), /certificate validation/);

  const unsupportedTransport = makeRoute({
    server: { ...liveServer, connection: { ...liveServer.connection, transport: { type: 'quic' } } },
  });
  await assert.rejects(() => invoke(unsupportedTransport, 'POST', `/v1/services/${S}/connection-profile`, body), /transport is invalid/);
});

test('live router ignores unrelated paths and optional source can remain disabled', async () => {
  const disabled = createLiveConnectionRouter({ auth: {}, repository: {}, connectionSource: null });
  assert.equal(disabled, null);
  const active = makeRoute();
  assert.equal(await invoke(active, 'GET', '/v1/other'), null);
});
