import assert from 'node:assert/strict';
import test from 'node:test';
import { createLegacyBotConnectionSource } from '../src/adapters/legacy-bot-connections.js';

const SERVICE_ID = '10000000-0000-4000-8000-000000000001';
const USER_ID = '20000000-0000-4000-8000-000000000001';
const CANDIDATE = 'a'.repeat(40);
const TOKEN = 'resolver-token-'.padEnd(40, 'x');

function repository({ mapped = true } = {}) {
  return {
    database() {
      return {
        async query(sql, values) {
          assert.match(sql, /control_legacy_service_projections/);
          assert.deepEqual(values, [SERVICE_ID, USER_ID]);
          return mapped ? {
            rowCount: 1,
            rows: [{ source_key: 'ganj-bot-primary', external_service_id: 'invoice-42' }],
          } : { rowCount: 0, rows: [] };
        },
      };
    },
  };
}

function payload() {
  return {
    connections: [{
      candidate_ref: CANDIDATE,
      name: 'Germany Premium',
      country_code: 'DE',
      city: 'Frankfurt',
      connection: {
        endpoint: 'edge.example.com',
        port: 443,
        protocol: 'vless',
        credential: '11111111-1111-4111-8111-111111111111',
        transport: { type: 'tcp' },
        security: { type: 'tls', server_name: 'edge.example.com', fingerprint: 'chrome' },
      },
    }],
  };
}

function source(fetchImpl, repo = repository()) {
  return createLegacyBotConnectionSource({
    environment: {
      GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/api/internal/ganj-app/connection-v1/',
      GANJ_BOT_CONNECTION_RESOLVER_TOKEN: TOKEN,
    },
    repository: repo,
    fetchImpl,
  });
}

const service = { id: SERVICE_ID, tier: 'premium' };
const principal = { userId: USER_ID };

test('legacy bot connection source resolves projection with bearer auth and deterministic safe server metadata', async () => {
  let observed;
  const resolver = source(async (url, options) => {
    observed = { url: url.toString(), options };
    return new Response(JSON.stringify(payload()), { status: 200, headers: { 'content-type': 'application/json' } });
  });

  const first = await resolver.listServers({ principal, services: [service] });
  const second = await resolver.listServers({ principal, services: [service] });
  assert.equal(first.length, 1);
  assert.equal(first[0].id, second[0].id);
  assert.match(first[0].id, /^[0-9a-f-]{36}$/);
  assert.equal(first[0].name, 'Germany Premium');
  assert.equal(first[0].country_code, 'DE');
  assert.deepEqual(first[0].protocols, ['vless']);
  assert.equal(observed.url, 'https://bot.example.com/api/internal/ganj-app/connection-v1/');
  assert.equal(observed.options.headers.authorization, `Bearer ${TOKEN}`);
  assert.deepEqual(JSON.parse(observed.options.body), { external_service_id: 'invoice-42' });

  const resolved = await resolver.resolveServer({ principal, service, serverId: first[0].id });
  assert.equal(resolved.id, first[0].id);
  assert.equal(resolved.connection.credential, payload().connections[0].connection.credential);
  assert.equal(await resolver.resolveServer({ principal, service, serverId: '30000000-0000-4000-8000-000000000001' }), null);
});

test('legacy bot connection source returns no candidates for unmapped and inactive resolver records', async () => {
  let calls = 0;
  const unmapped = source(async () => { calls += 1; return new Response('{}'); }, repository({ mapped: false }));
  assert.deepEqual(await unmapped.listServers({ principal, services: [service] }), []);
  assert.equal(calls, 0);

  for (const status of [404, 409, 422]) {
    const resolver = source(async () => new Response(JSON.stringify({ error: 'not_available' }), { status }));
    assert.deepEqual(await resolver.listServers({ principal, services: [service] }), []);
  }
});

test('legacy bot connection source fails closed on unsafe configuration and malformed upstream data', async () => {
  assert.throws(() => createLegacyBotConnectionSource({
    environment: {
      GANJ_BOT_CONNECTION_RESOLVER_URL: 'http://bot.example.com/api/internal/ganj-app/connection-v1/',
      GANJ_BOT_CONNECTION_RESOLVER_TOKEN: TOKEN,
    },
    repository: repository(),
  }), /HTTPS/);

  assert.throws(() => createLegacyBotConnectionSource({
    environment: {
      GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/api/internal/ganj-app/connection-v1/',
      GANJ_BOT_CONNECTION_RESOLVER_TOKEN: 'short',
    },
    repository: repository(),
  }), /token is invalid/);

  const malformed = source(async () => new Response(JSON.stringify({ connections: [{ candidate_ref: 'bad' }] }), { status: 200 }));
  await assert.rejects(() => malformed.listServers({ principal, services: [service] }), /candidate_ref/);

  const failed = source(async () => new Response(JSON.stringify({ error: 'temporary' }), { status: 503 }));
  await assert.rejects(() => failed.listServers({ principal, services: [service] }), /http_503/);
});
