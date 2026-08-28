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
const TROJAN = 'trojan://password-123456@tr1.example.com:443?security=tls&sni=tr1.example.com&fp=chrome#Turkey';
const VMESS = `vmess://${Buffer.from(JSON.stringify({
  v: '2',
  ps: 'VMess gRPC',
  add: 'vm.example.com',
  port: '443',
  id: VLESS_ID,
  net: 'grpc',
  path: 'ganj-grpc',
  tls: 'tls',
  sni: 'vm.example.com',
  fp: 'chrome',
}), 'utf8').toString('base64')}`;

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

test('PasarGuard explicitly allowlisted HTTPS subscription CDN is accepted', async () => {
  const connector = {
    ...CONNECTOR,
    subscriptionOrigins: ['https://cdn.example.com'],
  };
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: async (input, init) => {
      const url = new URL(input);
      if (url.hostname === 'cdn.example.com') {
        assert.equal(url.pathname, '/subscription/customer_101');
        return new Response(`${TROJAN}\n`, { status: 200 });
      }
      return upstreamFetch({ subscriptionUrl: 'https://cdn.example.com/subscription/customer_101' })(input, init);
    },
  });

  const result = await adapter.listSafeNodes({ connector, serviceUsername: 'customer_101' });
  assert.equal(result.nodes.length, 1);
  assert.equal(result.nodes[0].protocol, 'trojan');
  assert.equal(result.nodes[0].transport, 'tcp');
  assert.equal(result.nodes[0].security, 'tls');
});

test('PasarGuard panel base URL may safely contain a deployment sub-path', async () => {
  const connector = { ...CONNECTOR, baseUrl: 'https://panel.example.com/ganj-proxy/' };
  const seen = [];
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: async (input, init = {}) => {
      const url = new URL(input);
      seen.push(url.pathname);
      if (url.pathname === '/ganj-proxy/api/admin/token') return json({ access_token: 'b'.repeat(48) });
      if (url.pathname === '/ganj-proxy/api/user/customer_101') {
        assert.equal(init.headers.authorization, `Bearer ${'b'.repeat(48)}`);
        return json({
          username: 'customer_101',
          status: 'active',
          expire: null,
          data_limit: null,
          used_traffic: 0,
          subscription_url: '/sub/abc',
        });
      }
      if (url.pathname === '/sub/abc') return new Response(`${VLESS}\n`, { status: 200 });
      throw new Error(`unexpected ${url.pathname}`);
    },
  });

  const result = await adapter.listSafeNodes({ connector, serviceUsername: 'customer_101' });
  assert.equal(result.nodes.length, 1);
  assert.deepEqual(seen.slice(0, 2), ['/ganj-proxy/api/admin/token', '/ganj-proxy/api/user/customer_101']);
});

test('PasarGuard plain multi-protocol inventory normalizes VMess gRPC and Trojan TLS', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: upstreamFetch({ subscriptionBody: `${VMESS}\n${TROJAN}\n` }),
  });

  const result = await adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' });
  assert.equal(result.nodes.length, 2);
  assert.deepEqual(result.nodes.map((node) => [node.protocol, node.transport, node.security]), [
    ['vmess', 'grpc', 'tls'],
    ['trojan', 'tcp', 'tls'],
  ]);
});

test('PasarGuard rejects WebSocket REALITY inventory before Android profile issuance', async () => {
  const realityWebSocket =
    `vless://${VLESS_ID}@de1.example.com:443?type=ws&security=reality&sni=edge.example.com&fp=chrome&pbk=${'A'.repeat(43)}&sid=aabbccdd&path=%2Fws`;
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: upstreamFetch({ subscriptionBody: `${realityWebSocket}\n` }),
  });

  await assert.rejects(
    () => adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_subscription_unsupported',
  );
});

test('PasarGuard accepts standard SIP002 Shadowsocks userinfo with supported strong credential', async () => {
  const shadowsocks = 'ss://aes-256-gcm:strong-password@ss1.example.com:443#Secure';
  const adapter = new PasarGuardLiveServiceAdapter({
    fetchImpl: upstreamFetch({ subscriptionBody: `${shadowsocks}\n` }),
  });

  const list = await adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' });
  assert.equal(list.nodes.length, 1);
  assert.equal(list.nodes[0].protocol, 'shadowsocks');

  const resolved = await adapter.resolveConnection({
    connector: CONNECTOR,
    serviceUsername: 'customer_101',
    nodeId: list.nodes[0].id,
  });
  assert.equal(resolved.connection.shadowsocks_method, 'aes-256-gcm');
  assert.equal(resolved.connection.credential, 'strong-password');
});

test('PasarGuard rejects insecure connector configuration and invalid constructor limits', async () => {
  assert.throws(
    () => new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch(), timeoutMs: 100 }),
    /timeout is invalid/,
  );
  assert.throws(
    () => new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch(), maximumBytes: 1_000 }),
    /response limit is invalid/,
  );

  const adapter = new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch() });
  await assert.rejects(
    () => adapter.listSafeNodes({
      connector: { ...CONNECTOR, baseUrl: 'http://panel.example.com' },
      serviceUsername: 'customer_101',
    }),
    /credential-free HTTPS URL/,
  );
  await assert.rejects(
    () => adapter.listSafeNodes({ connector: CONNECTOR, serviceUsername: '../admin' }),
    (error) => error?.code === 'invalid_upstream_service',
  );
});

test('PasarGuard authentication and service protocol failures remain typed and fail closed', async () => {
  const authRejected = new PasarGuardLiveServiceAdapter({
    fetchImpl: async () => json({ error: 'nope' }, 401),
  });
  await assert.rejects(
    () => authRejected.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_auth_failed',
  );

  const invalidAuthBody = new PasarGuardLiveServiceAdapter({
    fetchImpl: async () => json({ access_token: 'tiny' }),
  });
  await assert.rejects(
    () => invalidAuthBody.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_protocol_error',
  );

  const missingService = new PasarGuardLiveServiceAdapter({
    fetchImpl: async (input) => {
      const url = new URL(input);
      if (url.pathname === '/api/admin/token') return json({ access_token: 'a'.repeat(48) });
      return json({ error: 'missing' }, 404);
    },
  });
  await assert.rejects(
    () => missingService.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'upstream_service_not_found',
  );

  const mismatchedService = new PasarGuardLiveServiceAdapter({
    fetchImpl: async (input) => {
      const url = new URL(input);
      if (url.pathname === '/api/admin/token') return json({ access_token: 'a'.repeat(48) });
      return json({ username: 'someone_else', status: 'active', subscription_url: '/sub/abc' });
    },
  });
  await assert.rejects(
    () => mismatchedService.listSafeNodes({ connector: CONNECTOR, serviceUsername: 'customer_101' }),
    (error) => error?.code === 'pasarguard_protocol_error',
  );
});

test('PasarGuard stale or malformed node identifiers cannot resolve connection material', async () => {
  const adapter = new PasarGuardLiveServiceAdapter({ fetchImpl: upstreamFetch() });
  await assert.rejects(
    () => adapter.resolveConnection({ connector: CONNECTOR, serviceUsername: 'customer_101', nodeId: 'bad-id' }),
    (error) => error?.code === 'invalid_node_id',
  );
  await assert.rejects(
    () => adapter.resolveConnection({ connector: CONNECTOR, serviceUsername: 'customer_101', nodeId: 'f'.repeat(64) }),
    (error) => error?.code === 'subscription_node_not_found',
  );
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