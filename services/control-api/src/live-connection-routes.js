import { createHash, randomUUID } from 'node:crypto';
import { ApiError, rejectUnknown, requireString, requireUuid, success } from './errors.js';
import { parseBody } from './application.js';

const TIER_RANK = Object.freeze({ free: 0, premium: 1, vip: 2 });
const SERVER_PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);
const PROFILE_TRANSPORTS = new Set(['tcp', 'ws', 'grpc']);
const PROFILE_SECURITY = new Set(['none', 'tls', 'reality']);
const TLS_FINGERPRINTS = new Set(['chrome', 'firefox', 'safari', 'ios', 'android', 'randomized']);
const SHADOWSOCKS_METHODS = new Set([
  '2022-blake3-aes-128-gcm', '2022-blake3-aes-256-gcm', 'aes-128-gcm', 'aes-256-gcm',
  'chacha20-poly1305', 'xchacha20-poly1305',
]);
const CANONICAL_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const SAFE_HOST = /^(?=.{1,253}$)(?:(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)\.)*(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)$/;

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, stable(item)]));
  }
  return value;
}

function digest(value) {
  return createHash('sha256').update(JSON.stringify(stable(value))).digest('hex');
}

function isUsable(service, now) {
  if (service.status !== 'active') return false;
  if (service.expires_at && Date.parse(service.expires_at) <= now.getTime()) return false;
  if (service.traffic_limit_bytes !== null && service.traffic_used_bytes >= service.traffic_limit_bytes) return false;
  return true;
}

function asServer(server) {
  return {
    id: server.id,
    code: server.code,
    name: server.name,
    country_code: server.country_code,
    city: server.city,
    tier: server.tier,
    status: server.status,
    load_ratio: server.load_ratio,
    latency_hint_ms: server.latency_hint_ms,
    favorite: false,
    protocols: [...server.protocols],
  };
}

function trustedConnection(server, service) {
  const value = server.connection;
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Server connection material is unavailable.');
  const allowed = new Set(['endpoint', 'port', 'protocol', 'credential', 'transport', 'security', 'flow', 'shadowsocks_method']);
  if (Object.keys(value).some((key) => !allowed.has(key))) throw new Error('Server connection material contains unsupported fields.');
  if (typeof value.endpoint !== 'string' || !SAFE_HOST.test(value.endpoint)
    || !Number.isInteger(value.port) || value.port < 1 || value.port > 65_535
    || !SERVER_PROTOCOLS.has(value.protocol) || !server.protocols.includes(value.protocol)
    || !service.allowed_protocols.includes(value.protocol)
    || typeof value.credential !== 'string' || value.credential.length < 8 || value.credential.length > 4096) {
    throw new Error('Server connection material is invalid or is not entitled.');
  }
  if (['vless', 'vmess'].includes(value.protocol) && !CANONICAL_UUID.test(value.credential)) {
    throw new Error('Server UUID credential is invalid.');
  }
  if (value.flow != null && (value.protocol !== 'vless' || value.flow !== 'xtls-rprx-vision')) throw new Error('Server flow is invalid.');
  if (value.protocol === 'shadowsocks' && !SHADOWSOCKS_METHODS.has(value.shadowsocks_method)) throw new Error('Server Shadowsocks method is invalid.');

  const transport = value.transport;
  if (!transport || typeof transport !== 'object' || Array.isArray(transport) || !PROFILE_TRANSPORTS.has(transport.type)) {
    throw new Error('Server transport is invalid.');
  }
  const transportAllowed = transport.type === 'ws' ? new Set(['type', 'path', 'host'])
    : transport.type === 'grpc' ? new Set(['type', 'service_name']) : new Set(['type']);
  if (Object.keys(transport).some((key) => !transportAllowed.has(key))) throw new Error('Server transport contains unsupported fields.');
  if (transport.type === 'ws' && (typeof transport.path !== 'string' || !transport.path.startsWith('/')
    || transport.path.length > 2048 || transport.host != null && !SAFE_HOST.test(transport.host))) throw new Error('Server WebSocket transport is invalid.');
  if (transport.type === 'grpc' && (typeof transport.service_name !== 'string'
    || !/^[A-Za-z0-9._/-]{1,256}$/.test(transport.service_name))) throw new Error('Server gRPC transport is invalid.');

  const security = value.security;
  if (!security || typeof security !== 'object' || Array.isArray(security) || !PROFILE_SECURITY.has(security.type)) {
    throw new Error('Server transport security is invalid.');
  }
  const securityAllowed = security.type === 'tls' ? new Set(['type', 'server_name', 'fingerprint', 'allow_insecure'])
    : security.type === 'reality' ? new Set(['type', 'server_name', 'fingerprint', 'public_key', 'short_id']) : new Set(['type']);
  if (Object.keys(security).some((key) => !securityAllowed.has(key))) throw new Error('Server transport security contains unsupported fields.');
  if (security.type !== 'none' && (!SAFE_HOST.test(security.server_name) || !TLS_FINGERPRINTS.has(security.fingerprint))) {
    throw new Error('Server TLS identity is invalid.');
  }
  if (security.allow_insecure === true) throw new Error('Disabling server certificate validation is forbidden.');
  if (security.type === 'reality' && (!/^[A-Za-z0-9_-]{43}$/.test(security.public_key)
    || !/^(?:[a-fA-F0-9]{2}){0,8}$/.test(security.short_id))) throw new Error('Server Reality identity is invalid.');

  return {
    schema_version: 1,
    endpoint: value.endpoint,
    port: value.port,
    protocol: value.protocol,
    credential: value.credential,
    transport,
    security: { ...security, ...(security.allow_insecure === undefined ? {} : { allow_insecure: false }) },
    flow: value.flow ?? null,
    shadowsocks_method: value.shadowsocks_method ?? null,
  };
}

async function ownedService(repository, userId, serviceId) {
  requireUuid(serviceId, 'service_id');
  const service = await repository.findOwnedService(userId, serviceId);
  if (!service) throw new ApiError(404, 'service_not_found', 'Service was not found.');
  return service;
}

function entitled(server, service) {
  return server.status === 'active'
    && TIER_RANK[server.tier] <= TIER_RANK[service.tier]
    && (!service.country_code || service.country_code === server.country_code)
    && server.protocols.some((item) => service.allowed_protocols.includes(item));
}

export function createLiveConnectionRouter({ auth, repository, connectionSource, clock = () => new Date() }) {
  if (!connectionSource) return null;
  return async function liveConnectionRouter({ request, url, principal, requestId }) {
    if (request.method === 'GET' && url.pathname === '/v1/servers') {
      const now = clock();
      const services = (await repository.listServices(principal.userId)).filter((service) => isUsable(service, now));
      const tier = url.searchParams.get('tier');
      const country = url.searchParams.get('country');
      const protocol = url.searchParams.get('protocol');
      if (tier && !(tier in TIER_RANK)) throw new ApiError(400, 'invalid_tier', 'tier is invalid.');
      if (country && !/^[A-Z]{2}$/.test(country)) throw new ApiError(400, 'invalid_country', 'country must be ISO 3166-1 alpha-2 uppercase.');
      if (protocol && !SERVER_PROTOCOLS.has(protocol)) throw new ApiError(400, 'invalid_protocol', 'protocol is invalid.');
      const servers = await connectionSource.listServers({ principal, services });
      const visible = servers.filter((server) => services.some((service) => entitled(server, service)))
        .filter((server) => !tier || server.tier === tier)
        .filter((server) => !country || server.country_code === country)
        .filter((server) => !protocol || server.protocols.includes(protocol));
      return { status: 200, body: success(visible.map(asServer), requestId, clock) };
    }

    const match = url.pathname.match(/^\/v1\/services\/([0-9a-f-]{36})\/connection-profile$/i);
    if (request.method !== 'POST' || !match) return null;
    const service = await ownedService(repository, principal.userId, match[1]);
    const now = clock();
    if (!isUsable(service, now)) throw new ApiError(403, 'service_inactive', 'Service is not active.');
    const body = await parseBody(request);
    rejectUnknown(body, ['device_id', 'server_id', 'client_nonce', 'device_proof']);
    const deviceId = requireUuid(body.device_id, 'device_id');
    const serverId = requireUuid(body.server_id, 'server_id');
    const clientNonce = requireString(body.client_nonce, 'client_nonce', { min: 32, max: 256 });
    const proof = requireString(body.device_proof, 'device_proof', { min: 32, max: 1024 });
    if (deviceId !== principal.deviceId) throw new ApiError(403, 'device_mismatch', 'Profile can only be issued to the authenticated device.');

    const server = await connectionSource.resolveServer({ principal, service, serverId });
    if (!server || !entitled(server, service)) throw new ApiError(403, 'server_unavailable', 'Server is unavailable.');
    const connection = trustedConnection(server, service);
    const proofPayload = { serviceId: service.id, deviceId, serverId, clientNonce };
    const unsignedBody = { client_nonce: clientNonce, device_id: deviceId, server_id: serverId };
    if (!await auth.verifyDeviceProof({
      principal,
      payload: proofPayload,
      method: request.method,
      pathAndQuery: `${url.pathname}${url.search}`,
      unsignedBody,
      proof,
    })) throw new ApiError(403, 'invalid_device_proof', 'Device proof is invalid.');

    const profileId = randomUUID();
    const expiresAt = new Date(now.getTime() + 5 * 60_000).toISOString();
    const associatedData = { profileId, userId: principal.userId, serviceId: service.id, deviceId, serverId, expiresAt };
    const sealed = await auth.sealConnectionProfile({
      principal,
      associatedData,
      plaintext: { ...connection, profile_id: profileId, service_id: service.id, server_id: serverId, device_id: deviceId, expires_at: expiresAt },
    });

    await repository.transaction(async () => {
      const current = await ownedService(repository, principal.userId, service.id);
      if (!isUsable(current, clock())) throw new ApiError(403, 'entitlement_changed', 'Service entitlement changed during profile issuance.');
      const currentServer = await connectionSource.resolveServer({ principal, service: current, serverId });
      if (!currentServer || !entitled(currentServer, current)) throw new ApiError(403, 'entitlement_changed', 'Service entitlement changed during profile issuance.');
      const alreadyBound = current.deviceIds.includes(deviceId);
      if (!alreadyBound && current.deviceIds.length >= current.device_limit) throw new ApiError(403, 'device_limit_reached', 'Service device limit has been reached.');
      if (!alreadyBound) await repository.saveService({ ...current, deviceIds: [...current.deviceIds, deviceId] });
      const reserved = await repository.reserveConnectionProfile({
        profileId,
        userId: principal.userId,
        serviceId: service.id,
        deviceId,
        serverId,
        clientNonceDigest: digest(clientNonce),
        expiresAt,
      });
      if (!reserved) throw new ApiError(409, 'profile_nonce_replayed', 'Client nonce was already used for profile issuance.');
    });

    return {
      status: 201,
      body: success({
        profile_id: profileId,
        server_id: serverId,
        algorithm: sealed.algorithm,
        key_version: sealed.keyVersion,
        nonce: sealed.nonce,
        ciphertext: sealed.ciphertext,
        expires_at: expiresAt,
      }, requestId, clock),
    };
  };
}
