import assert from 'node:assert/strict';
import test from 'node:test';
import { AdminApiClient, AdminApiError, createMemoryTokenProvider } from '../src/api.js';

globalThis.window = { location: { origin: 'https://admin.ganj.example' } };

function response(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('memory token provider never persists credentials and clears explicitly', async () => {
  const store = createMemoryTokenProvider();
  store.set('a'.repeat(32));
  assert.equal(await store.provider(), 'a'.repeat(32));
  assert.equal('localStorage' in store, false);
  assert.equal('sessionStorage' in store, false);
  store.clear();
  assert.equal(await store.provider(), null);
});

test('client sends bearer only to safe admin API path with redirect and cache disabled', async () => {
  const calls = [];
  const client = new AdminApiClient({
    baseUrl: 'https://api.ganj.example',
    accessTokenProvider: async () => 'b'.repeat(32),
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(200, { data: [], meta: { request_id: 'r1' }, error: null });
    },
  });
  await client.listServers();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://api.ganj.example/v1/admin/control-plane/servers');
  assert.equal(calls[0].init.redirect, 'error');
  assert.equal(calls[0].init.cache, 'no-store');
  assert.equal(calls[0].init.credentials, 'omit');
  assert.equal(calls[0].init.headers.get('Authorization'), `Bearer ${'b'.repeat(32)}`);
});

test('client rejects missing token before network I/O', async () => {
  let called = false;
  const client = new AdminApiClient({
    baseUrl: 'https://api.ganj.example',
    accessTokenProvider: async () => null,
    fetchImpl: async () => { called = true; },
  });
  await assert.rejects(() => client.listServers(), (error) => error instanceof AdminApiError && error.code === 'unauthorized');
  assert.equal(called, false);
});

test('client rejects non-HTTPS remote API origins', () => {
  assert.throws(() => new AdminApiClient({
    baseUrl: 'http://api.ganj.example',
    accessTokenProvider: async () => 'c'.repeat(32),
    fetchImpl: fetch,
  }), /HTTPS/);
});

test('error envelopes are reduced to safe operator messages without server detail reflection', async () => {
  const client = new AdminApiClient({
    baseUrl: 'https://api.ganj.example',
    accessTokenProvider: async () => 'd'.repeat(32),
    fetchImpl: async () => response(403, {
      data: null,
      meta: { request_id: 'req-safe' },
      error: { code: 'admin_scope_required', message: 'sensitive backend detail', retryable: false },
    }),
  });
  await assert.rejects(() => client.listServers(), (error) => {
    assert.equal(error instanceof AdminApiError, true);
    assert.equal(error.code, 'admin_scope_required');
    assert.equal(error.requestId, 'req-safe');
    assert.equal(error.message.includes('sensitive backend detail'), false);
    return true;
  });
});
