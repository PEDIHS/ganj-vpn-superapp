import assert from 'node:assert/strict';
import test from 'node:test';
import { GanjBotProjectionSource } from '../src/adapters/ganj-bot-projection-source.js';

const TOKEN = 't'.repeat(48);
const SAFE_ITEM = Object.freeze({
  source_key: 'ganj-bot-primary',
  event_id: 'event-1001',
  external_customer_id: '123456789',
  telegram_subject: '123456789',
  external_service_id: 'svc-42',
  plan_code: 'premium-30d',
  display_name: 'Ganj Premium',
  status: 'active',
  expires_at: '2026-09-30T00:00:00.000Z',
  traffic_limit_bytes: 107374182400,
  traffic_used_bytes: 1024,
  device_limit: 2,
  allowed_protocols: ['vless'],
  source_updated_at: '2026-08-28T12:00:00.000Z',
  upstream_binding: {
    provider_type: 'pasarguard',
    external_service_username: 'ganj_123456789',
    connector_ref: 'primary-pasarguard',
  },
});

test('projection source requires a credential-free HTTPS endpoint', () => {
  assert.throws(() => new GanjBotProjectionSource({ endpoint: 'http://bot.test/feed', bearerToken: TOKEN }), /HTTPS/);
  assert.throws(() => new GanjBotProjectionSource({ endpoint: 'https://user@bot.test/feed', bearerToken: TOKEN }), /HTTPS/);
  assert.throws(() => new GanjBotProjectionSource({ endpoint: 'https://bot.test/feed?token=x', bearerToken: TOKEN }), /HTTPS/);
});

test('projection source uses bearer auth, bounded pagination and returns normalized worker page', async () => {
  let captured;
  const source = new GanjBotProjectionSource({
    endpoint: 'https://bot.example/api/app/entitlements',
    bearerToken: TOKEN,
    fetchImpl: async (url, options) => {
      captured = { url: String(url), options };
      return new Response(JSON.stringify({ items: [SAFE_ITEM], next_cursor: 'cursor-2', has_more: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    },
  });
  const page = await source.pullPage({ cursor: 'cursor-1', limit: 20 });
  assert.equal(captured.options.redirect, 'error');
  assert.equal(captured.options.headers.authorization, `Bearer ${TOKEN}`);
  assert.match(captured.url, /limit=20/);
  assert.match(captured.url, /cursor=cursor-1/);
  assert.equal(page.items.length, 1);
  assert.equal(page.nextCursor, 'cursor-2');
  assert.equal(page.hasMore, true);
  assert.equal(source.toString().includes(TOKEN), false);
});

test('projection source rejects raw reusable VPN material even when the Bot accidentally exposes it', async () => {
  for (const poisoned of [
    { ...SAFE_ITEM, subscription_url: 'https://secret.example/sub' },
    { ...SAFE_ITEM, note: 'vless://00000000-0000-4000-8000-000000000000@example.com:443' },
    { ...SAFE_ITEM, upstream_binding: { ...SAFE_ITEM.upstream_binding, password: 'secret' } },
  ]) {
    const source = new GanjBotProjectionSource({
      endpoint: 'https://bot.example/api/app/entitlements',
      bearerToken: TOKEN,
      fetchImpl: async () => new Response(JSON.stringify({ items: [poisoned], next_cursor: null, has_more: false }), { status: 200 }),
    });
    await assert.rejects(source.pullPage({ cursor: null, limit: 10 }), /projection_(?:forbidden_field|raw_secret_rejected)/);
  }
});

test('projection source rejects oversized, malformed and inconsistent pages', async () => {
  const cases = [
    { items: [], next_cursor: null, has_more: true },
    { items: Array.from({ length: 3 }, () => SAFE_ITEM), next_cursor: null, has_more: false },
    { items: [], next_cursor: null, has_more: 'no' },
  ];
  for (const body of cases) {
    const source = new GanjBotProjectionSource({
      endpoint: 'https://bot.example/api/app/entitlements', bearerToken: TOKEN,
      fetchImpl: async () => new Response(JSON.stringify(body), { status: 200 }),
    });
    await assert.rejects(source.pullPage({ cursor: null, limit: 2 }));
  }
});

test('projection source marks provider 429/5xx as retryable without leaking body', async () => {
  for (const status of [429, 503]) {
    const source = new GanjBotProjectionSource({
      endpoint: 'https://bot.example/api/app/entitlements', bearerToken: TOKEN,
      fetchImpl: async () => new Response('provider secret details', { status }),
    });
    await assert.rejects(source.pullPage({ cursor: null, limit: 10 }), (error) => {
      assert.equal(error.status, status);
      assert.equal(error.retryable, true);
      assert.equal(String(error).includes('provider secret details'), false);
      return true;
    });
  }
});
