import assert from 'node:assert/strict';
import { chmod, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { createServerSecretResolver } from '../src/adapters/vault-server-secret.js';

async function tokenFile(t, mode = 0o600) {
  const root = await mkdtemp(join(tmpdir(), 'ganj-vault-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const path = join(root, 'token');
  await writeFile(path, 'vault-token-value', { mode });
  await chmod(path, mode);
  return path;
}

function jsonResponse(status, value) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('Vault resolver health-checks startup and reads only configured KV prefix', async (t) => {
  const token = await tokenFile(t);
  const calls = [];
  const resolver = await createServerSecretResolver({
    environment: {
      CONTROL_API_VAULT_ADDR: 'https://vault.example.com',
      CONTROL_API_VAULT_TOKEN_FILE: token,
      CONTROL_API_VAULT_SECRET_PREFIX: 'kv/data/ganj/servers/',
    },
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      if (url.includes('/sys/health')) return jsonResponse(200, { initialized: true, sealed: false });
      return jsonResponse(200, { data: { data: {
        endpoint: 'de.example.internal', port: 443, protocol: 'vless',
        credential: '60000000-0000-4000-8000-000000000001',
        transport: { type: 'tcp' }, security: { type: 'tls', server_name: 'de.example.internal' },
      } } });
    },
  });

  const value = await resolver.resolveServerConnection({ secretRef: 'vault:kv/data/ganj/servers/de-01' });
  assert.equal(value.endpoint, 'de.example.internal');
  assert.equal(calls.length, 2);
  assert.equal(calls[0].init.headers.has('X-Vault-Token'), false);
  assert.equal(calls[1].init.headers.get('X-Vault-Token'), 'vault-token-value');
  assert.equal(calls[1].init.redirect, 'error');
});

test('Vault resolver rejects references outside the allowed prefix without network I/O', async (t) => {
  const token = await tokenFile(t);
  let calls = 0;
  const resolver = await createServerSecretResolver({
    environment: {
      CONTROL_API_VAULT_ADDR: 'https://vault.example.com',
      CONTROL_API_VAULT_TOKEN_FILE: token,
      CONTROL_API_VAULT_SECRET_PREFIX: 'kv/data/ganj/servers/',
    },
    fetchImpl: async () => {
      calls += 1;
      return jsonResponse(200, { initialized: true });
    },
  });
  assert.equal(calls, 1);
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'vault:kv/data/other/secret' }), /outside/);
  assert.equal(calls, 1);
});

test('Vault resolver rejects broad token file permissions and non-HTTPS endpoint', async (t) => {
  const broad = await tokenFile(t, 0o644);
  await assert.rejects(() => createServerSecretResolver({
    environment: { CONTROL_API_VAULT_ADDR: 'https://vault.example.com', CONTROL_API_VAULT_TOKEN_FILE: broad },
    fetchImpl: async () => jsonResponse(200, {}),
  }), /permissions/);

  const restricted = await tokenFile(t);
  await assert.rejects(() => createServerSecretResolver({
    environment: { CONTROL_API_VAULT_ADDR: 'http://vault.example.com', CONTROL_API_VAULT_TOKEN_FILE: restricted },
    fetchImpl: async () => jsonResponse(200, {}),
  }), /HTTPS/);
});

test('Vault resolver rejects oversized, missing and unsupported secret responses', async (t) => {
  const token = await tokenFile(t);
  let mode = 'missing';
  const resolver = await createServerSecretResolver({
    environment: { CONTROL_API_VAULT_ADDR: 'https://vault.example.com', CONTROL_API_VAULT_TOKEN_FILE: token },
    fetchImpl: async (url) => {
      if (url.includes('/sys/health')) return jsonResponse(200, {});
      if (mode === 'missing') return jsonResponse(404, {});
      if (mode === 'unknown') return jsonResponse(200, { data: { data: { endpoint: 'x', credential: 'y', root_token: 'z' } } });
      return new Response('x'.repeat(33 * 1024), { status: 200 });
    },
  });

  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'vault:kv/data/ganj/servers/de-01' }), /not found/);
  mode = 'unknown';
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'vault:kv/data/ganj/servers/de-01' }), /unsupported/);
  mode = 'oversized';
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'vault:kv/data/ganj/servers/de-01' }), /too large/);
});
