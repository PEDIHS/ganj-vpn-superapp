import { readFile } from 'node:fs/promises';
import { stat } from 'node:fs/promises';

const MAXIMUM_RESPONSE_BYTES = 512 * 1024;
const DEFAULT_TIMEOUT_MS = 4_000;
const TOKEN_MINIMUM_BYTES = 32;
const RAW_SECRET_KEY = /(config|credential|password|subscription.?url|proxy|uri|uuid|token|private.?key|server.?secret)/i;
const RAW_SECRET_VALUE = /(?:vless|vmess|trojan|ss):\/\//i;

function safeEndpoint(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.search || url.hash) {
    throw new Error('GANJ_BOT_PROJECTION_URL must be a credential-free HTTPS URL without query or fragment.');
  }
  return url;
}

async function readSecretFile(path) {
  if (typeof path !== 'string' || path.length < 1) throw new Error('GANJ_BOT_PROJECTION_TOKEN_FILE is required.');
  const info = await stat(path);
  if (!info.isFile() || info.isSymbolicLink?.() || (info.mode & 0o077) !== 0) {
    throw new Error('Ganj Bot projection token file must be a private regular file.');
  }
  const value = (await readFile(path, 'utf8')).trim();
  if (Buffer.byteLength(value, 'utf8') < TOKEN_MINIMUM_BYTES || value.length > 4096) {
    throw new Error('Ganj Bot projection token is invalid.');
  }
  return value;
}

function assertSafeTree(value, path = 'item', depth = 0) {
  if (depth > 6) throw new Error('ganj_bot_projection_too_deep');
  if (value == null || typeof value === 'boolean' || typeof value === 'number') return;
  if (typeof value === 'string') {
    if (value.length > 4096 || RAW_SECRET_VALUE.test(value)) throw new Error('ganj_bot_projection_raw_secret_rejected');
    return;
  }
  if (Array.isArray(value)) {
    if (value.length > 32) throw new Error('ganj_bot_projection_array_too_large');
    value.forEach((item, index) => assertSafeTree(item, `${path}[${index}]`, depth + 1));
    return;
  }
  if (typeof value !== 'object') throw new Error('ganj_bot_projection_invalid_value');
  const entries = Object.entries(value);
  if (entries.length > 32) throw new Error('ganj_bot_projection_object_too_large');
  for (const [key, item] of entries) {
    if (RAW_SECRET_KEY.test(key)) throw new Error(`ganj_bot_projection_forbidden_field:${path}.${key}`);
    assertSafeTree(item, `${path}.${key}`, depth + 1);
  }
}

function validatePage(value, requestedLimit) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !Array.isArray(value.items)) {
    throw new Error('ganj_bot_projection_invalid_page');
  }
  if (value.items.length > requestedLimit) throw new Error('ganj_bot_projection_page_limit_exceeded');
  if (typeof value.has_more !== 'boolean') throw new Error('ganj_bot_projection_invalid_has_more');
  if (value.next_cursor != null && (typeof value.next_cursor !== 'string' || value.next_cursor.length > 4096)) {
    throw new Error('ganj_bot_projection_invalid_cursor');
  }
  if (value.has_more && !value.next_cursor) throw new Error('ganj_bot_projection_missing_cursor');
  value.items.forEach((item) => assertSafeTree(item));
  return {
    items: value.items,
    nextCursor: value.next_cursor ?? null,
    hasMore: value.has_more,
  };
}

export class GanjBotProjectionSource {
  constructor({ endpoint, bearerToken, timeoutMillis = DEFAULT_TIMEOUT_MS, fetchImpl = fetch }) {
    this.endpoint = safeEndpoint(endpoint);
    if (typeof bearerToken !== 'string' || Buffer.byteLength(bearerToken, 'utf8') < TOKEN_MINIMUM_BYTES) {
      throw new Error('Ganj Bot projection bearer token is invalid.');
    }
    if (!Number.isSafeInteger(timeoutMillis) || timeoutMillis < 500 || timeoutMillis > 15_000) {
      throw new Error('Ganj Bot projection timeout is invalid.');
    }
    if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is required.');
    this.bearerToken = bearerToken;
    this.timeoutMillis = timeoutMillis;
    this.fetchImpl = fetchImpl;
  }

  async pullPage({ cursor, limit }) {
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 500) throw new Error('ganj_bot_projection_invalid_limit');
    if (cursor != null && (typeof cursor !== 'string' || cursor.length > 4096)) throw new Error('ganj_bot_projection_invalid_cursor');
    const url = new URL(this.endpoint);
    url.searchParams.set('limit', String(limit));
    if (cursor) url.searchParams.set('cursor', cursor);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMillis);
    let response;
    try {
      response = await this.fetchImpl(url, {
        method: 'GET',
        redirect: 'error',
        signal: controller.signal,
        headers: {
          accept: 'application/json',
          authorization: `Bearer ${this.bearerToken}`,
          'user-agent': 'ganj-vpn-control-api/legacy-reconciliation',
        },
      });
    } catch (error) {
      const wrapped = new Error(error?.name === 'AbortError' ? 'ganj_bot_projection_timeout' : 'ganj_bot_projection_unavailable');
      wrapped.code = error?.name === 'AbortError' ? 'ETIMEDOUT' : 'ECONNRESET';
      wrapped.retryable = true;
      throw wrapped;
    } finally {
      clearTimeout(timeout);
    }
    if (!response.ok) {
      const error = new Error(`ganj_bot_projection_http_${response.status}`);
      error.status = response.status;
      error.retryable = response.status === 429 || response.status >= 500;
      throw error;
    }
    const declared = Number(response.headers.get('content-length') ?? 0);
    if (declared > MAXIMUM_RESPONSE_BYTES) throw new Error('ganj_bot_projection_response_too_large');
    const raw = await response.text();
    if (Buffer.byteLength(raw, 'utf8') > MAXIMUM_RESPONSE_BYTES) throw new Error('ganj_bot_projection_response_too_large');
    let parsed;
    try { parsed = JSON.parse(raw); } catch { throw new Error('ganj_bot_projection_invalid_json'); }
    return validatePage(parsed, limit);
  }

  toString() { return 'GanjBotProjectionSource([REDACTED])'; }
}

export async function createGanjBotProjectionSource({ environment = process.env, fetchImpl = fetch } = {}) {
  const token = await readSecretFile(environment.GANJ_BOT_PROJECTION_TOKEN_FILE);
  return new GanjBotProjectionSource({
    endpoint: environment.GANJ_BOT_PROJECTION_URL,
    bearerToken: token,
    timeoutMillis: Number(environment.GANJ_BOT_PROJECTION_TIMEOUT_MS ?? DEFAULT_TIMEOUT_MS),
    fetchImpl,
  });
}
