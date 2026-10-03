import assert from 'node:assert/strict';
import test from 'node:test';
import { createLegacyBotServiceMetadataSource } from '../src/adapters/legacy-bot-service-metadata.js';

const USER = '20000000-0000-4000-8000-000000000001';
const SERVICE = '10000000-0000-4000-8000-000000000001';
const UNMAPPED = '10000000-0000-4000-8000-000000000002';
const environment = { GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/api/internal/ganj-app/connection-v1/',
  GANJ_BOT_CONNECTION_RESOLVER_TOKEN: 'metadata-token-'.padEnd(40, 'x') };
const service = { id: SERVICE, name: 'Plan name', traffic_limit_bytes: 1000, traffic_used_bytes: 0 };
const row = { control_service_id: SERVICE, external_service_id: '42', external_customer_id: '99' };
function fixture(fetchImpl, rows = [row]) {
  return createLegacyBotServiceMetadataSource({ environment, fetchImpl, repository: {
    database() { return { async query(sql, values) {
      assert.match(sql, /user_id = \$1/);
      assert.deepEqual(values, [USER, [SERVICE, UNMAPPED], 'ganj-bot-primary']); return { rows };
    } }; },
  } });
}
const input = { principal: { userId: USER }, services: [service, { ...service, id: UNMAPPED }] };
function item(available = true) {
  return { external_service_id: '42', external_customer_id: '99', service_username: 'fixture_user_99',
    traffic_limit_bytes: available ? 1000 : null, traffic_used_bytes: available ? 250 : null, traffic_usage_available: available };
}
function response(items) { return new Response(JSON.stringify({ items }), { status: 200 }); }

test('service metadata is owner scoped, read only and uses actual provider traffic', async () => {
  const calls = [];
  const source = fixture(async (url, options) => {
    calls.push(JSON.parse(options.body));
    assert.equal(url.toString(), 'https://bot.example.com/api/internal/ganj-app/service-metadata-v1/');
    assert.equal(options.redirect, 'error');
    assert.equal(options.headers.authorization, `Bearer ${environment.GANJ_BOT_CONNECTION_RESOLVER_TOKEN}`);
    return response([item(JSON.parse(options.body).include_usage)]);
  });
  const result = await source.decorate(input);
  assert.deepEqual(calls.map((c) => c.include_usage), [false, true]);
  assert.deepEqual(calls[0].records, [{ external_service_id: '42', external_customer_id: '99' }]);
  assert.equal(result[0].service_username, 'fixture_user_99');
  assert.equal(result[0].traffic_used_bytes, 250); assert.equal(result[0].traffic_limit_bytes, 1000);
  assert.equal(result[0].traffic_usage_available, true); assert.equal(result[1], input.services[1]);
  assert.equal(service.traffic_used_bytes, 0, 'Display enrichment must not mutate entitlement storage');
});

test('unavailable provider keeps the username and explicitly marks usage unknown', async () => {
  const source = fixture(async (_, options) => {
    if (JSON.parse(options.body).include_usage) throw new Error('upstream_timeout'); return response([item(false)]);
  });
  const result = await source.decorate(input);
  assert.equal(result[0].service_username, 'fixture_user_99'); assert.equal(result[0].traffic_usage_available, false);
  assert.equal(result[0].traffic_limit_bytes, service.traffic_limit_bytes);
});

test('unlimited quota does not invent a consumption percentage', async () => {
  const result = await fixture(async () => response([{ ...item(), traffic_limit_bytes: null }])).decorate(input);
  assert.equal(result[0].traffic_limit_bytes, null);
});

test('unmapped and malformed projection locators never call the endpoint', async () => {
  for (const rows of [[], [{ ...row, control_service_id: 'not-owned' }], [{ ...row, external_service_id: 'vless://private' }],
    [{ ...row, external_customer_id: 'invalid' }]]) {
    assert.deepEqual(await fixture(async () => { throw new Error('Must not fetch'); }, rows).decorate(input), input.services);
  }
  assert.deepEqual(await fixture(async () => { throw new Error('Must not fetch'); }).decorate({ ...input, services: [] }), []);
});

test('invalid, cross-owner, duplicate and private metadata never enter the response', async () => {
  for (const items of [
    [{ ...item(), external_customer_id: 'wrong-owner' }], [{ ...item(), external_service_id: 'other-invoice' }], [item(), item()],
    [{ ...item(), service_username: 'vless://credential@example.com' }], [{ ...item(), subscription_url: 'https://private.example.com' }],
    [{ ...item(), traffic_usage_available: 'true' }], [{ ...item(), traffic_limit_bytes: -1 }], [{ ...item(), traffic_used_bytes: -1 }],
    [{ ...item(), traffic_used_bytes: null }], [{ ...item(), traffic_used_bytes: 1.5 }],
    [{ ...item(), traffic_used_bytes: Number.MAX_SAFE_INTEGER + 1 }], [null],
  ]) {
    const result = await fixture(async () => response(items)).decorate(input);
    assert.equal(result[0].traffic_usage_available, false); assert.equal(result[0].service_username, null);
    assert.equal(JSON.stringify(result).includes('credential'), false); assert.equal(JSON.stringify(result).includes('subscription_url'), false);
  }
});

test('failed HTTP, oversized and malformed bodies keep subscriptions available', async () => {
  for (const fetchImpl of [async () => new Response('{}', { status: 503 }),
    async () => new Response('{}', { headers: { 'content-length': String(600 * 1024) } }),
    async () => new Response('x'.repeat(600 * 1024)), async () => new Response('{invalid'), async () => new Response('{}')]) {
    const result = await fixture(fetchImpl).decorate(input);
    assert.equal(result.length, 2); assert.equal(result[0].traffic_usage_available, false);
  }
});

test('unsafe endpoints, short tokens and invalid source keys are rejected', () => {
  for (const override of [
    { GANJ_BOT_CONNECTION_RESOLVER_URL: 'http://bot.example.com/api/internal/ganj-app/connection-v1/' },
    { GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://user:password@bot.example.com/api/internal/ganj-app/connection-v1/' },
    { GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/api/internal/ganj-app/connection-v1/?token=x' },
    { GANJ_BOT_CONNECTION_RESOLVER_URL: 'https://bot.example.com/other/' },
    { GANJ_BOT_CONNECTION_RESOLVER_TOKEN: 'short' }, { LEGACY_SOURCE_KEY: '../wrong' },
  ]) assert.throws(() => createLegacyBotServiceMetadataSource({ environment: { ...environment, ...override }, repository: {} }));
});
