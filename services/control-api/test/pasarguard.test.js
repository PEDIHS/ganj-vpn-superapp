import assert from 'node:assert/strict';
import test from 'node:test';
import { PasarGuardLiveServiceAdapter } from '../src/adapters/pasarguard.js';

const CONNECTOR = Object.freeze({
  baseUrl: 'https://panel.example.com',
  adminUsername: 'ganj-service',
  adminPassword: 'secret-from-vault',
});

const VLESS_ID = '123e4567-e89b-42d3-a456-426614174000';
const VLESS = `vless://${VLESS_ID}@de1.example.com:443?type=ws&security=tls&sni=edge.example.com&fp=chrome&path=%2Fws&host=edge.example.com#Germany%20One`;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function upstreamFetch({
  serviceStatus = 'active',
  subscriptionUrl = '/sub/abc',
  subscriptionBody = Buffer.from(`${VLESS}\n`, 'utf8').toString('base64'),
} = {}) {
  return async (input, init = {}) => {
    const url = new URL(input);
    if (url.pathname === '/api/admin/token') {
      assert.equal(init.method, 'POST');
      assert.match(String(init.body), /username=ganj-service/);
      return json({ access_token: 'a'.repeat(48) });
    }
    if (url.pathname === '/api/user/customer_101') {
      assert.equal(init.headers.authorization, `Bearer ${'a'.repeat(48)}`);
      return json({
        username: 'customer_101',
        status: serviceStatus,
        expire: '2026-09-28T00:00:00Z',
        data_limit: 10_000_000_000,
        used_traffic: 123_456,
        subscription_url: subscriptionUrl,
        proxy_settings: { vless: { id: VLESS_ID } },
      });
    }
    if (url.pathname === '/sub/abc') {
      return new Response(subscriptionBody, { status: 200, headers: { 'content-type': 'text/plain' } });
    }
    throw new Error(`Unexpected upstream URL: ${url}`);
  };
}

test('PasarGuard safe node inventory never exposes subscription URL or credentials', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch() });
  const result = await adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' });

  assert.equal(result.service.status, 'active');
  assert.equal(result.nodes.length, 1);
  assert.equal(result.nodes[0].name, 'Germany One');
  assert.equal(result.nodes[0].protocol, 'vless');
  assert.equal(result.nodes[0].transport, 'ws');
  assert.equal(result.nodes[0].security, 'tls');
  assert.match(result.nodes[0].id, /^[a-f0-9]{64}$/);

  const serialized = JSON.stringify(result);
  assert.equal(serialized.includes(VLESS_ID), false);
  assert.equal(serialized.includes('/sub/abc'), false);
  assert.equal(serialized.includes('de1.example.com'), false);
  assert.equal(serialized.includes('secret-from-vault'), false);
});

test('PasarGuard node ID can be resolved back to a live backend-only connection', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch() });
  const list = await adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' });
  const result = await adapter.resolveConnection({
    connector: CONNECTOR,
    serviceUsername: 'customer_101',
    nodeId: list.nodes[0].id,
  });

  assert.equal(result.connection.protocol, 'vless');
  assert.equal(result.connection.endpoint, 'de1.example.com');
  assert.equal(result.connection.port, 443);
  assert.equal(result.connection.credential, VLESS_ID);
  assert.deepEqual(result.connection.transport, { type: 'ws', path: '/ws', host: 'edge.example.com' });
  assert.deepEqual(result.connection.security, {
    type: 'tls',
    server_name: 'edge.example.com',
    fingerprint: 'chrome',
    allow_insecure: false,
  });
});

test('PasarGuard subscription fetch rejects an unapproved cross-origin subscription URL', async () => {
  let evilFetch = false;
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: async (input, init) => {
      const url = new URL(input);
      if (url.hostname === 'evil.example') {
        evilFetch = true;
        throw new Error('must never fetch evil origin');
      }
      return upstreamFetch({ subscriptionUrl: 'https://evil.example/sub/leak' })(input, init);
    },
  });

  await assert.rejects(
    () => adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_subscription_origin_forbidden',
  );
  assert.equal(evilFetch, false);
});

test('PasarGuard inactive service fails closed before returning node inventory', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch({ serviceStatus: 'disabled' }) });
  await assert.rejects(
    () => adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'upstream_service_inactive',
  );
});

test('PasarGuard inventory skips unsupported lines and fails when no secure supported node remains', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: upstreamFetch({ subscriptionBody: 'socks://unsafe.example:1080\nnot-a-config\n' }),
  });
  await assert.rejects(
    () => adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_subscription_unsupported',
  );
});
