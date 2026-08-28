import assert from 'node:assert/strict';
import { chmod, mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { createServerSecretResolver } from '../src/adapters/file-server-secret.js';
import { loadProductionEnvironment } from '../src/production-environment.js';

async function secretFile(path, content, mode = 0o600) {
  await writeFile(path, content, { mode });
  await chmod(path, mode);
}

test('server secret resolver reads only restricted in-root JSON payloads', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'ganj-secrets-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const servers = join(root, 'servers');
  await mkdir(servers);
  await chmod(servers, 0o700);
  await secretFile(join(servers, 'de-01.json'), JSON.stringify({
    endpoint: 'vpn.example.internal',
    port: 443,
    protocol: 'vless',
    credential: '60000000-0000-4000-8000-000000000001',
    transport: { type: 'tcp' },
    security: { type: 'tls', server_name: 'vpn.example.internal', fingerprint: 'chrome' },
    flow: 'xtls-rprx-vision',
  }));

  const resolver = await createServerSecretResolver({ environment: { CONTROL_API_SERVER_SECRET_DIR: servers } });
  const value = await resolver.resolveServerConnection({ secretRef: 'file:de-01.json' });
  assert.equal(value.endpoint, 'vpn.example.internal');
  assert.equal(value.protocol, 'vless');
  assert.equal(value.credential, '60000000-0000-4000-8000-000000000001');
});

test('server secret resolver rejects traversal, broad permissions and unsupported secret fields', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'ganj-secrets-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const servers = join(root, 'servers');
  await mkdir(servers);
  await chmod(servers, 0o700);
  await secretFile(join(root, 'outside.json'), JSON.stringify({ endpoint: 'outside', credential: 'do-not-read' }));
  await secretFile(join(servers, 'broad.json'), JSON.stringify({ endpoint: 'vpn.example', credential: 'credential-value' }), 0o644);
  await secretFile(join(servers, 'unknown.json'), JSON.stringify({ endpoint: 'vpn.example', credential: 'credential-value', admin_token: 'forbidden' }));

  const resolver = await createServerSecretResolver({ environment: { CONTROL_API_SERVER_SECRET_DIR: servers } });
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'file:../outside.json' }));
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'file:broad.json' }), /permissions/);
  await assert.rejects(() => resolver.resolveServerConnection({ secretRef: 'file:unknown.json' }), /unsupported/);
});

test('production environment loads approved secret files without mutating source environment', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'ganj-env-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const jwk = join(root, 'auth.jwk');
  const telegram = join(root, 'telegram-secret');
  await secretFile(jwk, '{"kty":"OKP","crv":"Ed25519","d":"private"}');
  await secretFile(telegram, 'telegram-secret-value');
  const source = {
    AUTH_SESSION_SIGNING_PRIVATE_JWK_FILE: jwk,
    TELEGRAM_OIDC_CLIENT_SECRET_FILE: telegram,
    NODE_ENV: 'production',
  };

  const loaded = await loadProductionEnvironment(source);
  assert.equal(loaded.AUTH_SESSION_SIGNING_PRIVATE_JWK, '{"kty":"OKP","crv":"Ed25519","d":"private"}');
  assert.equal(loaded.TELEGRAM_OIDC_CLIENT_SECRET, 'telegram-secret-value');
  assert.equal(loaded.AUTH_SESSION_SIGNING_PRIVATE_JWK_FILE, undefined);
  assert.equal(source.AUTH_SESSION_SIGNING_PRIVATE_JWK, undefined);
});

test('production environment rejects ambiguous direct plus file secret configuration', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'ganj-env-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const telegram = join(root, 'telegram-secret');
  await secretFile(telegram, 'file-value');
  await assert.rejects(() => loadProductionEnvironment({
    TELEGRAM_OIDC_CLIENT_SECRET_FILE: telegram,
    TELEGRAM_OIDC_CLIENT_SECRET: 'direct-value',
  }), /cannot both be configured/);
});
