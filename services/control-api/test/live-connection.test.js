import test from 'node:test';
import assert from 'node:assert/strict';
import { createLegacyBotConnectionSource } from '../src/adapters/legacy-bot-connections.js';
import { createLiveConnectionRouter } from '../src/live-connection-routes.js';

const USER_ID = '11111111-1111-4111-8111-111111111111';
const DEVICE_ID = '22222222-2222-4222-8222-222222222222';
const SERVICE_ID = '33333333-3333-4333-8333-333333333333';
const UUID = '44444444-4444-4444-8444-444444444444';
const TOKEN = 'x'.repeat(48);

function service(overrides = {}) {
  return {
    id: SERVICE_ID,
    userId: USER_ID,
    planId: null,
    name: 'Legacy Premium',
    status: 'active',
    tier: 'premium',
    country_code: null,
    traffic_limit_bytes: null,
    traffic_used_bytes: 0,
    expires_at: null,
    device_limit: 2,
    allowed_protocols: ['vless', 'trojan'],
    deviceIds: [],
    ...overrides,
  };
}

function resolverBody() {
  return {
    provider_type: 'marzban',
    connections: [{
      candidate_ref: 'a'.repeat(40),
      name: 'Germany Alpha',
      country_code: 'DE',
      city: 'Frankfurt',
      connection: {
        endpoint: 'edge.example.com',
        port: 443,
        protocol: 'vless',
        credential: UUID,
        transport: { type: 'ws', path: '/ganj', host: 'edge.example.com' },
        security: { type: 'tls', server_name: 'edge.example.com', fingerprint: 'chrome' },
      },
    }],
  };
}

function createSource(fetchImpl) {
  return createLegacyBotConnectionSource({
    environment: {
      GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/api/internal/ganj-app/connection-v1/',
      GANJ_BOT_CONNECTION_RESOLVER_TOKEN: TOKEN,
    },
    repository: {
      database: () => ({
        query: async () => ({
          rowCount: 1,
          rows: [{ source_key: 'ganj-bot-primary', external_service_id: 'INV-123' }],
        }),
      }),
    },
    fetchImpl,
  });
}

test('live legacy source resolves only structured server candidates for the owned projection', async () => {
  let request;
  const source = createSource(async (url, options) => {
    request = { url: url.toString(), options };
    return new Response(JSON.stringify(resolverBody()), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  });
  const principal = { userId: USER_ID, deviceId: DEVICE_ID };
  const servers = await source.listServers({ principal, services: [service()] });
  assert.equal(servers.length, 1);
  assert.match(servers[0].id, /^[0-9a-f-]{36}$/);
  assert.equal(servers[0].name, 'Germany Alpha');
  assert.deepEqual(servers[0].protocols, ['vless']);
  assert.equal(servers[0].connection.credential, UUID);
  assert.equal(request.url, 'https://bot.example.com/api/internal/ganj-app/connection-v1/');
  assert.equal(request.options.headers.authorization, `Bearer ${TOKEN}`);
  assert.deepEqual(JSON.parse(request.options.body), { external_service_id: 'INV-123' });

  const resolved = await source.resolveServer({ principal, service: service(), serverId: servers[0].id });
  assert.equal(resolved.id, servers[0].id);
  assert.equal(resolved.connection.endpoint, 'edge.example.com');
});

test('legacy source fails closed for unsafe endpoint and treats inactive upstream records as empty', async () => {
  assert.throws(() => createLegacyBotConnectionSource({
    environment: {
      GANJ_BOT_CONNECTION_RESOLVER_URL: 'http://bot.example.com/api/internal/ganj-app/connection-v1/',
      GANJ_BOT_CONNECTION_RESOLVER_TOKEN: TOKEN,
    },
    repository: { database() {} },
  }), /credential-free HTTPS/);

  const source = createSource(async () => new Response('{}', { status: 409 }));
  const servers = await source.listServers({
    principal: { userId: USER_ID, deviceId: DEVICE_ID },
    services: [service()],
  });
  assert.deepEqual(servers, []);
});

test('live router lists entitlement-filtered servers and issues a sealed device-bound profile', async () => {
  const principal = { userId: USER_ID, deviceId: DEVICE_ID };
  const current = service();
  let savedService = null;
  let reserved = null;
  const repository = {
    listServices: async () => [current],
    findOwnedService: async (userId, id) => userId === USER_ID && id === SERVICE_ID ? current : null,
    transaction: async (work) => work(),
    saveService: async (value) => { savedService = value; return value; },
    reserveConnectionProfile: async (value) => { reserved = value; return true; },
  };
  const liveServer = {
    id: '55555555-5555-4555-8555-555555555555',
    code: 'legacy-alpha',
    name: 'Germany Alpha',
    country_code: 'DE',
    city: 'Frankfurt',
    tier: 'premium',
    status: 'active',
    load_ratio: null,
    latency_hint_ms: null,
    protocols: ['vless'],
    connection: resolverBody().connections[0].connection,
  };
  const connectionSource = {
    listServers: async () => [liveServer],
    resolveServer: async ({ serverId }) => serverId === liveServer.id ? liveServer : null,
  };
  const auth = {
    verifyDeviceProof: async ({ principal: verified, unsignedBody }) => {
      assert.equal(verified.deviceId, DEVICE_ID);
      assert.equal(unsignedBody.server_id, liveServer.id);
      return true;
    },
    sealConnectionProfile: async ({ plaintext }) => {
      assert.equal(plaintext.credential, UUID);
      assert.equal(plaintext.protocol, 'vless');
      return { algorithm: 'X25519+HKDF-SHA256+AES-256-GCM/GVP1', keyVersion: 'v1', nonce: 'bm9uY2U=', ciphertext: 'Y2lwaGVy' };
    },
  };
  const router = createLiveConnectionRouter({ auth, repository, connectionSource, clock: () => new Date('2026-09-12T06:00:00Z') });

  const listed = await router({
    request: new Request('https://api.example/v1/servers', { headers: { authorization: 'Bearer opaque' } }),
    url: new URL('https://api.example/v1/servers'),
    principal,
    requestId: '66666666-6666-4666-8666-666666666666',
  });
  assert.equal(listed.status, 200);
  assert.equal(listed.body.data.length, 1);
  assert.equal(listed.body.data[0].id, liveServer.id);
  assert.equal('connection' in listed.body.data[0], false);

  const body = {
    device_id: DEVICE_ID,
    server_id: liveServer.id,
    client_nonce: 'n'.repeat(32),
    device_proof: 'p'.repeat(32),
  };
  const issued = await router({
    request: new Request(`https://api.example/v1/services/${SERVICE_ID}/connection-profile`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer opaque' },
      body: JSON.stringify(body),
    }),
    url: new URL(`https://api.example/v1/services/${SERVICE_ID}/connection-profile`),
    principal,
    requestId: '77777777-7777-4777-8777-777777777777',
  });
  assert.equal(issued.status, 201);
  assert.equal(issued.body.data.server_id, liveServer.id);
  assert.equal(savedService.deviceIds[0], DEVICE_ID);
  assert.equal(reserved.serviceId, SERVICE_ID);
  assert.equal(reserved.serverId, liveServer.id);
});
