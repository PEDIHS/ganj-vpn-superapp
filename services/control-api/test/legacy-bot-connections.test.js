import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
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

function realityPayload(shortId = 'abcd') {
  const value = payload();
  value.connections[0].connection.security = {
    type: 'reality', server_name: 'edge.example.com', fingerprint: 'chrome',
    public_key: 'a'.repeat(43), short_id: shortId,
  };
  value.connections[0].candidate_ref = (shortId === 'abcd' ? 'a' : 'b').repeat(40);
  return value;
}

test('Reality selection survives short-ID rotation and resolves current handshake material', async () => {
  let reads = 0;
  const resolver = source(async () => new Response(JSON.stringify(realityPayload(reads++ === 0 ? 'abcd' : 'ef01'))));
  const [selected] = await resolver.listServers({ principal, services: [service] });
  const [refreshed] = await resolver.listServers({ principal, services: [service] });
  assert.equal(refreshed.id, selected.id);
  assert.equal(refreshed.code, selected.code);
  const resolved = await resolver.resolveServer({ principal, service, serverId: selected.id });
  assert.equal(resolved.id, selected.id);
  assert.equal(resolved.connection.security.short_id, 'ef01');
});

test('Reality selection never follows a changed endpoint, credential, key, SNI or transport', async () => {
  for (const mutate of [
    (c) => { c.endpoint = 'other.example.com'; },
    (c) => { c.port = 8443; },
    (c) => { c.credential = '22222222-2222-4222-8222-222222222222'; },
    (c) => { c.security.public_key = 'b'.repeat(43); },
    (c) => { c.security.server_name = 'other.example.com'; },
    (c) => { c.transport = { type: 'xhttp', path: '/different', mode: 'auto' }; },
  ]) {
    let changed = false;
    const resolver = source(async () => {
      const value = realityPayload(changed ? 'ef01' : 'abcd');
      if (changed) mutate(value.connections[0].connection);
      return new Response(JSON.stringify(value));
    });
    const [selected] = await resolver.listServers({ principal, services: [service] });
    changed = true;
    assert.equal(await resolver.resolveServer({ principal, service, serverId: selected.id }), null);
  }
});

test('Reality identity is canonical, preserves distinct routes and deduplicates short-ID alternatives', async () => {
  const value = realityPayload();
  const alternative = structuredClone(value.connections[0]);
  alternative.candidate_ref = 'b'.repeat(40);
  alternative.connection.security.short_id = 'ef01';
  alternative.connection = Object.fromEntries(Object.entries(alternative.connection).reverse());
  const other = structuredClone(alternative);
  other.candidate_ref = 'c'.repeat(40);
  other.connection.port = 8443;
  const named = structuredClone(alternative);
  named.name = 'Separate named config';
  named.candidate_ref = 'd'.repeat(40);
  value.connections.push(alternative, other, named);
  const resolver = source(async () => new Response(JSON.stringify(value)));
  const servers = await resolver.listServers({ principal, services: [service] });
  assert.equal(servers.length, 3);
  assert.notEqual(servers[0].id, servers[1].id);
});

test('Reality identity remains subscription scoped', async () => {
  const repo = { database() { return { async query(sql, values) {
    assert.match(sql, /control_service_id = \$1 AND user_id = \$2/);
    return { rowCount: 1, rows: [{ source_key: 'ganj-bot-primary', external_service_id: 'invoice-42' }] };
  } }; } };
  const resolver = source(async () => new Response(JSON.stringify(realityPayload())), repo);
  const [selected] = await resolver.listServers({ principal, services: [service] });
  const other = { ...service, id: '10000000-0000-4000-8000-000000000002' };
  const [otherServer] = await resolver.listServers({ principal, services: [other] });
  assert.notEqual(otherServer.id, selected.id);
  assert.equal(await resolver.resolveServer({ principal, service: other, serverId: selected.id }), null);
});

test('pre-hotfix Reality ID resolves only its exact existing candidate and preserves response binding', async () => {
  const bytes = createHash('sha256').update(`ganj-bot-primary:${SERVICE_ID}:${CANDIDATE}`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  const previous = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  let rotated = false;
  const resolver = source(async () => new Response(JSON.stringify(realityPayload(rotated ? 'ef01' : 'abcd'))));
  const resolved = await resolver.resolveServer({ principal, service, serverId: previous });
  assert.equal(resolved.id, previous);
  rotated = true;
  assert.equal(await resolver.resolveServer({ principal, service, serverId: previous }), null);
});

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
