import { readFile, stat } from 'node:fs/promises';

const REFERENCE = /^vault:([A-Za-z0-9][A-Za-z0-9._/-]{2,240})$/;
const MAX_RESPONSE_BYTES = 32 * 1024;
const ALLOWED_FIELDS = new Set(['endpoint', 'port', 'protocol', 'credential', 'transport', 'security', 'flow', 'shadowsocks_method']);

function baseAddress(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('CONTROL_API_VAULT_ADDR must be a valid HTTPS URL.'); }
  if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.search || url.hash) {
    throw new Error('CONTROL_API_VAULT_ADDR must be an HTTPS origin without credentials/query/fragment.');
  }
  return url.origin;
}

async function tokenFromFile(path) {
  if (!path) throw new Error('CONTROL_API_VAULT_TOKEN_FILE is required.');
  const info = await stat(path);
  if (!info.isFile() || info.size < 8 || info.size > 4096) throw new Error('Vault token file is invalid.');
  if ((info.mode & 0o077) !== 0) throw new Error('Vault token file must not grant group/world permissions.');
  const token = (await readFile(path, 'utf8')).trim();
  if (token.length < 8 || /\s/.test(token)) throw new Error('Vault token file content is invalid.');
  return token;
}

function secretPath(reference, prefix) {
  const match = REFERENCE.exec(reference ?? '');
  if (!match) throw new Error('Server secret reference must use vault:<path>.');
  const path = match[1];
  if (path.includes('..') || path.includes('//') || !path.startsWith(prefix)) {
    throw new Error('Vault secret reference is outside the configured server-secret prefix.');
  }
  return path;
}

function validatePayload(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Vault server secret payload must be an object.');
  if (Object.keys(value).some((key) => !ALLOWED_FIELDS.has(key))) throw new Error('Vault server secret payload contains unsupported fields.');
  if (typeof value.endpoint !== 'string' || typeof value.credential !== 'string') throw new Error('Vault server secret payload is incomplete.');
  return structuredClone(value);
}

async function boundedJson(response) {
  const declared = Number(response.headers.get('content-length') ?? 0);
  if (declared > MAX_RESPONSE_BYTES) throw new Error('Vault response is too large.');
  const text = await response.text();
  if (Buffer.byteLength(text, 'utf8') > MAX_RESPONSE_BYTES) throw new Error('Vault response is too large.');
  try { return JSON.parse(text); } catch { throw new Error('Vault returned invalid JSON.'); }
}

export async function createServerSecretResolver({ environment = process.env, fetchImpl = fetch } = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('Vault fetch implementation is required.');
  const address = baseAddress(environment.CONTROL_API_VAULT_ADDR);
  const token = await tokenFromFile(environment.CONTROL_API_VAULT_TOKEN_FILE);
  const namespace = environment.CONTROL_API_VAULT_NAMESPACE?.trim() || null;
  const prefix = environment.CONTROL_API_VAULT_SECRET_PREFIX?.trim() || 'kv/data/ganj/servers/';
  if (!/^[A-Za-z0-9][A-Za-z0-9._/-]{2,200}\/$/.test(prefix) || prefix.includes('..')) {
    throw new Error('CONTROL_API_VAULT_SECRET_PREFIX is invalid.');
  }

  async function call(path, { authenticated = true } = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Number(environment.CONTROL_API_VAULT_TIMEOUT_MS ?? 4000));
    try {
      const headers = new Headers({ Accept: 'application/json' });
      if (authenticated) headers.set('X-Vault-Token', token);
      if (namespace) headers.set('X-Vault-Namespace', namespace);
      return await fetchImpl(`${address}/v1/${path}`, {
        method: 'GET', headers, signal: controller.signal, redirect: 'error', cache: 'no-store',
      });
    } finally {
      clearTimeout(timeout);
    }
  }

  const health = await call('sys/health?standbyok=true&perfstandbyok=true', { authenticated: false });
  if (![200, 429, 472, 473].includes(health.status)) throw new Error('Vault health check failed during startup.');

  return {
    kind: 'vault-kv-v1',
    async resolveServerConnection({ secretRef }) {
      const path = secretPath(secretRef, prefix);
      const response = await call(path);
      if (response.status === 404) throw new Error('Vault server secret was not found.');
      if (!response.ok) throw new Error('Vault server secret lookup failed.');
      const payload = await boundedJson(response);
      // KV v2 response: { data: { data: { ...secret }, metadata: ... } }
      const secret = payload?.data?.data;
      return validatePayload(secret);
    },
    async close() {},
  };
}
