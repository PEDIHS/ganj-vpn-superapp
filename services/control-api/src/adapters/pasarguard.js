import { createHash } from 'node:crypto';
import { ApiError } from '../errors.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SAFE_HOST = /^(?=.{1,253}$)(?:(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)\.)*(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)$/;
const SUPPORTED_PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);
const SUPPORTED_TRANSPORTS = new Set(['tcp', 'ws', 'grpc']);
const SUPPORTED_SECURITY = new Set(['none', 'tls', 'reality']);
const TLS_FINGERPRINTS = new Set(['chrome', 'firefox', 'safari', 'ios', 'android', 'randomized']);
const SHADOWSOCKS_METHODS = new Set([
  '2022-blake3-aes-128-gcm',
  '2022-blake3-aes-256-gcm',
  'aes-128-gcm',
  'aes-256-gcm',
  'chacha20-poly1305',
  'xchacha20-poly1305',
  'chacha20-ietf-poly1305',
]);

function fail(code, message, status = 502, retryable = true) {
  throw new ApiError(status, code, message, { retryable });
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, canonical(item)]),
    );
  }
  return value;
}

function digest(value) {
  return createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex');
}

function normalizeBaseUrl(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('PasarGuard base URL is invalid.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
    throw new Error('PasarGuard base URL must be a credential-free HTTPS URL.');
  }
  url.pathname = url.pathname.replace(/\/+$/, '') || '/';
  return url;
}

function apiUrl(baseUrl, path) {
  const prefix = baseUrl.pathname === '/' ? '' : baseUrl.pathname.replace(/\/+$/, '');
  return new URL(`${prefix}${path}`, baseUrl.origin);
}

function validateConnector(connector) {
  if (!connector || typeof connector !== 'object') throw new Error('PasarGuard connector is required.');
  const baseUrl = normalizeBaseUrl(connector.baseUrl);
  if (typeof connector.adminUsername !== 'string' || connector.adminUsername.length < 1 || connector.adminUsername.length > 256) {
    throw new Error('PasarGuard admin username is invalid.');
  }
  if (typeof connector.adminPassword !== 'string' || connector.adminPassword.length < 1 || connector.adminPassword.length > 8192) {
    throw new Error('PasarGuard admin credential is invalid.');
  }
  const extraOrigins = Array.isArray(connector.subscriptionOrigins) ? connector.subscriptionOrigins : [];
  const allowedOrigins = new Set([baseUrl.origin]);
  for (const candidate of extraOrigins) {
    let origin;
    try { origin = new URL(candidate).origin; } catch { throw new Error('PasarGuard subscription origin is invalid.'); }
    if (!origin.startsWith('https://')) throw new Error('PasarGuard subscription origins must use HTTPS.');
    allowedOrigins.add(origin);
  }
  return { baseUrl, allowedOrigins };
}

function safeServiceUsername(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_.@-]{1,128}$/.test(value)) {
    throw new ApiError(400, 'invalid_upstream_service', 'Upstream service username is invalid.');
  }
  return value;
}

function decodeMaybeBase64Subscription(raw) {
  const trimmed = raw.trim();
  if (trimmed.includes('://')) return trimmed;
  if (!/^[A-Za-z0-9+/_=-]+$/.test(trimmed) || trimmed.length < 8) return trimmed;
  try {
    const normalized = trimmed.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = Buffer.from(normalized, 'base64').toString('utf8').trim();
    return decoded.includes('://') ? decoded : trimmed;
  } catch {
    return trimmed;
  }
}

function queryValue(url, ...names) {
  for (const name of names) {
    const value = url.searchParams.get(name);
    if (value != null && value !== '') return value;
  }
  return null;
}

function transportFromUrl(url) {
  const type = (queryValue(url, 'type', 'net') ?? 'tcp').toLowerCase();
  if (!SUPPORTED_TRANSPORTS.has(type)) throw new Error('Unsupported transport.');
  if (type === 'ws') {
    const path = queryValue(url, 'path') ?? '/';
    if (!path.startsWith('/') || path.length > 2048) throw new Error('Invalid WebSocket path.');
    const host = queryValue(url, 'host');
    if (host && !SAFE_HOST.test(host)) throw new Error('Invalid WebSocket host.');
    return { type, path, ...(host ? { host } : {}) };
  }
  if (type === 'grpc') {
    const serviceName = queryValue(url, 'serviceName', 'service_name') ?? '';
    if (!/^[A-Za-z0-9._/-]{1,256}$/.test(serviceName)) throw new Error('Invalid gRPC service name.');
    return { type, service_name: serviceName };
  }
  return { type: 'tcp' };
}

function securityFromUrl(url) {
  const type = (queryValue(url, 'security') ?? 'none').toLowerCase();
  if (!SUPPORTED_SECURITY.has(type)) throw new Error('Unsupported security.');
  const insecure = (queryValue(url, 'allowInsecure', 'allow_insecure') ?? '').toLowerCase();
  if (['1', 'true', 'yes'].includes(insecure)) throw new Error('Insecure TLS is forbidden.');
  if (type === 'none') return { type };
  const serverName = queryValue(url, 'sni', 'serverName', 'server_name');
  const fingerprint = (queryValue(url, 'fp', 'fingerprint') ?? 'chrome').toLowerCase();
  if (!serverName || !SAFE_HOST.test(serverName) || !TLS_FINGERPRINTS.has(fingerprint)) {
    throw new Error('Invalid TLS identity.');
  }
  if (type === 'tls') return { type, server_name: serverName, fingerprint, allow_insecure: false };
  const publicKey = queryValue(url, 'pbk', 'publicKey', 'public_key');
  const shortId = queryValue(url, 'sid', 'shortId', 'short_id') ?? '';
  if (!publicKey || !/^[A-Za-z0-9_-]{43}$/.test(publicKey) || !/^(?:[a-fA-F0-9]{2}){0,8}$/.test(shortId)) {
    throw new Error('Invalid REALITY identity.');
  }
  return { type, server_name: serverName, fingerprint, public_key: publicKey, short_id: shortId };
}

function displayName(url, fallback) {
  const fragment = url.hash ? decodeURIComponent(url.hash.slice(1)) : '';
  return (fragment || fallback).replace(/[\r\n\t]/g, ' ').trim().slice(0, 120) || fallback;
}

function connectionIdentity(connection) {
  return {
    endpoint: connection.endpoint,
    port: connection.port,
    protocol: connection.protocol,
    credential: connection.credential,
    transport: connection.transport,
    security: connection.security,
    flow: connection.flow ?? null,
    shadowsocks_method: connection.shadowsocks_method ?? null,
  };
}

function safeNode(connection, name) {
  const identity = connectionIdentity(connection);
  return {
    id: digest(identity),
    name,
    protocol: connection.protocol,
    transport: connection.transport.type,
    security: connection.security.type,
    flow: connection.flow ?? null,
  };
}

function parseUrlNode(line) {
  const url = new URL(line);
  const protocol = url.protocol.replace(':', '').toLowerCase();
  if (!['vless', 'trojan'].includes(protocol)) throw new Error('Unsupported URL node protocol.');
  const endpoint = url.hostname;
  const port = Number(url.port);
  if (!SAFE_HOST.test(endpoint) || !Number.isInteger(port) || port < 1 || port > 65_535) throw new Error('Invalid endpoint.');
  const credential = decodeURIComponent(url.username);
  if (protocol === 'vless' && !UUID.test(credential)) throw new Error('Invalid VLESS UUID.');
  if (protocol === 'trojan' && (credential.length < 8 || credential.length > 4096)) throw new Error('Invalid Trojan credential.');
  const security = securityFromUrl(url);
  const flow = queryValue(url, 'flow');
  if (flow && (protocol !== 'vless' || flow !== 'xtls-rprx-vision')) throw new Error('Invalid flow.');
  if (security.type === 'reality' && protocol !== 'vless') throw new Error('REALITY is VLESS-only.');
  if (protocol === 'trojan' && security.type === 'none') throw new Error('Trojan requires authenticated transport security.');
  return {
    name: displayName(url, protocol.toUpperCase()),
    connection: {
      endpoint,
      port,
      protocol,
      credential,
      transport: transportFromUrl(url),
      security,
      flow: flow ?? null,
      shadowsocks_method: null,
    },
  };
}

function parseVmess(line) {
  const encoded = line.slice('vmess://'.length).trim();
  let data;
  try { data = JSON.parse(Buffer.from(encoded, 'base64').toString('utf8')); } catch { throw new Error('Invalid VMess payload.'); }
  const endpoint = String(data.add ?? '');
  const port = Number(data.port);
  const credential = String(data.id ?? '').toLowerCase();
  if (!SAFE_HOST.test(endpoint) || !Number.isInteger(port) || port < 1 || port > 65_535 || !UUID.test(credential)) {
    throw new Error('Invalid VMess endpoint.');
  }
  const transportType = String(data.net ?? 'tcp').toLowerCase();
  if (!SUPPORTED_TRANSPORTS.has(transportType)) throw new Error('Unsupported VMess transport.');
  const transport = transportType === 'ws'
    ? { type: 'ws', path: String(data.path ?? '/'), ...(data.host ? { host: String(data.host) } : {}) }
    : transportType === 'grpc'
      ? { type: 'grpc', service_name: String(data.path ?? data.serviceName ?? '') }
      : { type: 'tcp' };
  if (transport.type === 'ws' && (!transport.path.startsWith('/') || transport.host && !SAFE_HOST.test(transport.host))) {
    throw new Error('Invalid VMess WebSocket transport.');
  }
  if (transport.type === 'grpc' && !/^[A-Za-z0-9._/-]{1,256}$/.test(transport.service_name)) {
    throw new Error('Invalid VMess gRPC service.');
  }
  const tls = String(data.tls ?? '').toLowerCase();
  const security = tls === 'tls'
    ? {
      type: 'tls',
      server_name: String(data.sni ?? data.host ?? endpoint),
      fingerprint: String(data.fp ?? 'chrome').toLowerCase(),
      allow_insecure: false,
    }
    : { type: 'none' };
  if (security.type === 'tls' && (!SAFE_HOST.test(security.server_name) || !TLS_FINGERPRINTS.has(security.fingerprint))) {
    throw new Error('Invalid VMess TLS identity.');
  }
  return {
    name: String(data.ps ?? 'VMess').replace(/[\r\n\t]/g, ' ').trim().slice(0, 120) || 'VMess',
    connection: {
      endpoint,
      port,
      protocol: 'vmess',
      credential,
      transport,
      security,
      flow: null,
      shadowsocks_method: null,
    },
  };
}

function parseShadowsocks(line) {
  const url = new URL(line);
  if (url.protocol !== 'ss:') throw new Error('Invalid Shadowsocks URL.');
  const endpoint = url.hostname;
  const port = Number(url.port);
  let auth = decodeURIComponent(url.username);
  try { auth = Buffer.from(auth, 'base64').toString('utf8'); } catch { /* use plaintext */ }
  const separator = auth.indexOf(':');
  if (separator <= 0) throw new Error('Invalid Shadowsocks auth.');
  const method = auth.slice(0, separator);
  const credential = auth.slice(separator + 1);
  if (!SAFE_HOST.test(endpoint) || !Number.isInteger(port) || port < 1 || port > 65_535
    || !SHADOWSOCKS_METHODS.has(method) || credential.length < 1 || credential.length > 4096) {
    throw new Error('Invalid Shadowsocks node.');
  }
  return {
    name: displayName(url, 'Shadowsocks'),
    connection: {
      endpoint,
      port,
      protocol: 'shadowsocks',
      credential,
      transport: { type: 'tcp' },
      security: { type: 'none' },
      flow: null,
      shadowsocks_method: method,
    },
  };
}

function parseSubscription(raw) {
  const decoded = decodeMaybeBase64Subscription(raw);
  const lines = decoded.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  if (lines.length === 0 || lines.length > 512) fail('pasarguard_subscription_invalid', 'PasarGuard subscription inventory is invalid.');
  const parsed = [];
  for (const line of lines) {
    try {
      let node;
      if (line.startsWith('vless://') || line.startsWith('trojan://')) node = parseUrlNode(line);
      else if (line.startsWith('vmess://')) node = parseVmess(line);
      else if (line.startsWith('ss://')) node = parseShadowsocks(line);
      else continue;
      if (!SUPPORTED_PROTOCOLS.has(node.connection.protocol)) continue;
      parsed.push(node);
    } catch {
      // A malformed/unsupported line is skipped; the whole inventory fails only when none remain.
    }
  }
  if (parsed.length === 0) fail('pasarguard_subscription_unsupported', 'PasarGuard subscription has no supported secure nodes.');
  return parsed;
}

async function boundedText(response, maximumBytes) {
  const length = Number(response.headers?.get?.('content-length') ?? 0);
  if (Number.isFinite(length) && length > maximumBytes) fail('pasarguard_response_too_large', 'PasarGuard response exceeds the allowed size.');
  const text = await response.text();
  if (Buffer.byteLength(text, 'utf8') > maximumBytes) fail('pasarguard_response_too_large', 'PasarGuard response exceeds the allowed size.');
  return text;
}

async function boundedJson(response, maximumBytes) {
  const text = await boundedText(response, maximumBytes);
  try { return JSON.parse(text); } catch { fail('pasarguard_protocol_error', 'PasarGuard returned invalid JSON.'); }
}

export class PasarGuardLiveServiceAdapter {
  constructor({ fetchImpl = fetch, timeoutMs = 8_000, maximumBytes = 512 * 1024 } = {}) {
    if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is required.');
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1_000 || timeoutMs > 30_000) throw new Error('PasarGuard timeout is invalid.');
    if (!Number.isSafeInteger(maximumBytes) || maximumBytes < 16_384 || maximumBytes > 2 * 1024 * 1024) {
      throw new Error('PasarGuard response limit is invalid.');
    }
    this.kind = 'pasarguard-marzban-compatible-v1';
    this.fetchImpl = fetchImpl;
    this.timeoutMs = timeoutMs;
    this.maximumBytes = maximumBytes;
  }

  async authenticate(connector) {
    const { baseUrl } = validateConnector(connector);
    const form = new URLSearchParams({ username: connector.adminUsername, password: connector.adminPassword });
    let response;
    try {
      response = await this.fetchImpl(apiUrl(baseUrl, '/api/admin/token'), {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
        body: form,
        redirect: 'error',
        signal: AbortSignal.timeout(this.timeoutMs),
      });
    } catch {
      fail('pasarguard_unavailable', 'PasarGuard authentication is unavailable.');
    }
    if (!response.ok) fail('pasarguard_auth_failed', 'PasarGuard rejected the connector credential.', 502, false);
    const body = await boundedJson(response, this.maximumBytes);
    if (typeof body?.access_token !== 'string' || body.access_token.length < 16 || body.access_token.length > 8192) {
      fail('pasarguard_protocol_error', 'PasarGuard authentication response is invalid.');
    }
    return body.access_token;
  }

  async readLiveService({ connector, serviceUsername }) {
    const { baseUrl } = validateConnector(connector);
    const username = safeServiceUsername(serviceUsername);
    const token = await this.authenticate(connector);
    let response;
    try {
      response = await this.fetchImpl(apiUrl(baseUrl, `/api/user/${encodeURIComponent(username)}`), {
        method: 'GET',
        headers: { accept: 'application/json', authorization: `Bearer ${token}` },
        redirect: 'error',
        signal: AbortSignal.timeout(this.timeoutMs),
      });
    } catch {
      fail('pasarguard_unavailable', 'PasarGuard service lookup is unavailable.');
    }
    if (response.status === 404) throw new ApiError(404, 'upstream_service_not_found', 'PasarGuard service was not found.');
    if (!response.ok) fail('pasarguard_service_lookup_failed', 'PasarGuard service lookup failed.');
    const body = await boundedJson(response, this.maximumBytes);
    if (!body || typeof body !== 'object' || Array.isArray(body) || body.username !== username) {
      fail('pasarguard_protocol_error', 'PasarGuard service response is invalid.');
    }
    return {
      username,
      status: typeof body.status === 'string' ? body.status : 'unknown',
      expiresAt: typeof body.expire === 'string' ? body.expire : body.expire == null ? null : String(body.expire),
      dataLimitBytes: Number.isSafeInteger(Number(body.data_limit)) ? Number(body.data_limit) : null,
      usedTrafficBytes: Number.isSafeInteger(Number(body.used_traffic)) ? Number(body.used_traffic) : null,
      subscriptionUrl: typeof body.subscription_url === 'string' ? body.subscription_url : null,
    };
  }

  resolveSubscriptionUrl(connector, subscriptionUrl) {
    const { baseUrl, allowedOrigins } = validateConnector(connector);
    if (typeof subscriptionUrl !== 'string' || subscriptionUrl.length < 1 || subscriptionUrl.length > 4096) {
      fail('pasarguard_subscription_missing', 'PasarGuard service has no usable subscription URL.', 409, false);
    }
    let url;
    try { url = new URL(subscriptionUrl, baseUrl); } catch { fail('pasarguard_subscription_invalid', 'PasarGuard subscription URL is invalid.', 502, false); }
    if (url.protocol !== 'https:' || url.username || url.password || !allowedOrigins.has(url.origin)) {
      fail('pasarguard_subscription_origin_forbidden', 'PasarGuard subscription URL is outside the connector allow-list.', 502, false);
    }
    return url;
  }

  async readInventory({ connector, serviceUsername }) {
    const service = await this.readLiveService({ connector, serviceUsername });
    if (!['active', 'on_hold'].includes(service.status)) {
      throw new ApiError(403, 'upstream_service_inactive', 'PasarGuard service is not active.');
    }
    const url = this.resolveSubscriptionUrl(connector, service.subscriptionUrl);
    let response;
    try {
      response = await this.fetchImpl(url, {
        method: 'GET',
        headers: { accept: 'text/plain, application/octet-stream;q=0.9' },
        redirect: 'error',
        signal: AbortSignal.timeout(this.timeoutMs),
      });
    } catch {
      fail('pasarguard_unavailable', 'PasarGuard subscription inventory is unavailable.');
    }
    if (!response.ok) fail('pasarguard_subscription_fetch_failed', 'PasarGuard subscription inventory could not be fetched.');
    const nodes = parseSubscription(await boundedText(response, this.maximumBytes));
    return {
      service: {
        username: service.username,
        status: service.status,
        expiresAt: service.expiresAt,
        dataLimitBytes: service.dataLimitBytes,
        usedTrafficBytes: service.usedTrafficBytes,
      },
      nodes,
    };
  }

  async listSafeNodes(value) {
    const inventory = await this.readInventory(value);
    return {
      service: inventory.service,
      nodes: inventory.nodes.map((node) => safeNode(node.connection, node.name)),
    };
  }

  async resolveConnection({ connector, serviceUsername, nodeId }) {
    if (typeof nodeId !== 'string' || !/^[a-f0-9]{64}$/.test(nodeId)) {
      throw new ApiError(400, 'invalid_node_id', 'Subscription node identifier is invalid.');
    }
    const inventory = await this.readInventory({ connector, serviceUsername });
    const node = inventory.nodes.find((candidate) => digest(connectionIdentity(candidate.connection)) === nodeId);
    if (!node) throw new ApiError(404, 'subscription_node_not_found', 'Subscription node is no longer available.');
    return {
      service: inventory.service,
      connection: { ...node.connection },
    };
  }
}
