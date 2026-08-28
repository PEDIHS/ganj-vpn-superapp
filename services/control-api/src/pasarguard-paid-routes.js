import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { ApiError, failure, rejectUnknown, requireString, requireUuid, success } from './errors.js';
import { parseBody } from './application.js';
import { PasarGuardLiveServiceAdapter } from './adapters/pasarguard.js';

const TIER_RANK = Object.freeze({ free: 0, premium: 1, vip: 2 });
const PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);
const BINDING_TOKEN_MINIMUM = 32;

function digest(value) {
  return createHash('sha256').update(value).digest('hex');
}

function stableVirtualServerId(serviceId, nodeId) {
  const bytes = createHash('sha256')
    .update(`GANJ-PASARGUARD-VIRTUAL-SERVER-V1\n${serviceId}\n${nodeId}`, 'utf8')
    .digest()
    .subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function safeSame(left, right) {
  const a = Buffer.from(String(left ?? ''));
  const b = Buffer.from(String(right ?? ''));
  return a.length === b.length && timingSafeEqual(a, b);
}

function bearer(request) {
  const value = request.headers.get('authorization');
  return typeof value === 'string' && value.startsWith('Bearer ') && !value.includes(',')
    ? value.slice(7) : null;
}

function liveExpiry(value) {
  if (value == null || value === '' || value === '0') return null;
  if (/^[0-9]{9,16}$/.test(String(value))) {
    const raw = Number(value);
    const millis = raw > 10_000_000_000 ? raw : raw * 1_000;
    if (!Number.isSafeInteger(millis)) {
      throw new ApiError(502, 'pasarguard_protocol_error', 'PasarGuard service expiry is invalid.');
    }
    const date = new Date(millis);
    if (!Number.isFinite(date.getTime())) {
      throw new ApiError(502, 'pasarguard_protocol_error', 'PasarGuard service expiry is invalid.');
    }
    return date.toISOString();
  }
  const parsed = Date.parse(String(value));
  if (!Number.isFinite(parsed)) {
    throw new ApiError(502, 'pasarguard_protocol_error', 'PasarGuard service expiry is invalid.');
  }
  return new Date(parsed).toISOString();
}

function projectStatus(commercialStatus, liveStatus, expiresAt, used, limit, now) {
  // Commercial ownership can revoke/expire access even while upstream is accidentally still active.
  if (commercialStatus !== 'active') return commercialStatus;
  if (expiresAt && Date.parse(expiresAt) <= now.getTime()) return 'expired';
  if (limit !== null && used >= limit) return 'disabled';
  if (['active', 'on_hold'].includes(liveStatus)) return 'active';
  if (['expired'].includes(liveStatus)) return 'expired';
  if (['disabled', 'limited'].includes(liveStatus)) return 'disabled';
  return 'disabled';
}

function projectService(service, live, now) {
  const expiresAt = liveExpiry(live.expiresAt);
  const limit = live.dataLimitBytes == null ? null : live.dataLimitBytes;
  const used = live.usedTrafficBytes == null ? 0 : live.usedTrafficBytes;
  return {
    ...service,
    status: projectStatus(service.status, live.status, expiresAt, used, limit, now),
    traffic_limit_bytes: limit,
    traffic_used_bytes: used,
    expires_at: expiresAt,
  };
}

function asService(service) {
  return {
    id: service.id,
    name: service.name,
    status: service.status,
    tier: service.tier,
    country_code: service.country_code,
    traffic_limit_bytes: service.traffic_limit_bytes,
    traffic_used_bytes: service.traffic_used_bytes,
    expires_at: service.expires_at,
    device_limit: service.device_limit,
    allowed_protocols: [...service.allowed_protocols],
  };
}

function usable(service, now) {
  return service.status === 'active'
    && (!service.expires_at || Date.parse(service.expires_at) > now.getTime())
    && (service.traffic_limit_bytes === null || service.traffic_used_bytes < service.traffic_limit_bytes);
}

function asStaticServer(server) {
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

function virtualServer({ service, node, connectorMeta }) {
  const id = stableVirtualServerId(service.id, node.id);
  return {
    id,
    code: `pg-${service.id.slice(0, 8)}-${node.id.slice(0, 8)}`,
    name: node.name,
    country_code: service.country_code ?? connectorMeta.countryCode ?? 'ZZ',
    city: connectorMeta.city ?? null,
    tier: service.tier,
    status: 'active',
    load_ratio: 0,
    latency_hint_ms: null,
    favorite: false,
    protocols: [node.protocol],
    _nodeId: node.id,
  };
}

function filteredServer(server, { tier, country, protocol }) {
  if (tier && server.tier !== tier) return false;
  if (country && server.country_code !== country) return false;
  if (protocol && !server.protocols.includes(protocol)) return false;
  return server.status === 'active';
}

function validateServerFilters(url) {
  const tier = url.searchParams.get('tier');
  const country = url.searchParams.get('country');
  const protocol = url.searchParams.get('protocol');
  if (tier && !(tier in TIER_RANK)) throw new ApiError(400, 'invalid_tier', 'tier is invalid.');
  if (country && !/^[A-Z]{2}$/.test(country)) {
    throw new ApiError(400, 'invalid_country', 'country must be ISO 3166-1 alpha-2 uppercase.');
  }
  if (protocol && !PROTOCOLS.has(protocol)) throw new ApiError(400, 'invalid_protocol', 'protocol is invalid.');
  return { tier, country, protocol };
}

function matchConnectionProfile(pathname) {
  const match = pathname.match(/^\/v1\/services\/([0-9a-f-]{36})\/connection-profile$/i);
  return match ? match[1] : null;
}

function matchService(pathname) {
  const match = pathname.match(/^\/v1\/services\/([0-9a-f-]{36})$/i);
  return match ? match[1] : null;
}

export class PasarGuardConnectorRegistry {
  constructor(raw) {
    let parsed;
    try { parsed = JSON.parse(raw); } catch { throw new Error('PASARGUARD_CONNECTORS_JSON must be valid JSON.'); }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || Object.keys(parsed).length === 0) {
      throw new Error('PASARGUARD_CONNECTORS_JSON must contain at least one connector.');
    }
    this.connectors = new Map();
    for (const [ref, value] of Object.entries(parsed)) {
      if (!/^[A-Za-z0-9._:-]{1,128}$/.test(ref) || !value || typeof value !== 'object' || Array.isArray(value)) {
        throw new Error('PasarGuard connector registry contains an invalid entry.');
      }
      if (typeof value.baseUrl !== 'string' || typeof value.adminUsername !== 'string'
        || typeof value.adminPassword !== 'string' || value.adminPassword.length < 1) {
        throw new Error(`PasarGuard connector ${ref} is incomplete.`);
      }
      const countryCode = value.countryCode == null ? null : String(value.countryCode).toUpperCase();
      if (countryCode && !/^[A-Z]{2}$/.test(countryCode)) throw new Error(`PasarGuard connector ${ref} countryCode is invalid.`);
      const city = value.city == null ? null : String(value.city).trim().slice(0, 200);
      this.connectors.set(ref, {
        connector: {
          baseUrl: value.baseUrl,
          adminUsername: value.adminUsername,
          adminPassword: value.adminPassword,
          subscriptionOrigins: Array.isArray(value.subscriptionOrigins) ? value.subscriptionOrigins : [],
        },
        meta: { countryCode, city: city || null },
      });
    }
  }

  resolve(ref) {
    const value = this.connectors.get(ref);
    if (!value) throw new ApiError(503, 'pasarguard_connector_unavailable', 'PasarGuard connector is not configured.', { retryable: true });
    return value;
  }

  toString() { return 'PasarGuardConnectorRegistry([REDACTED])'; }
}

export class PostgresPasarGuardBindingStore {
  constructor(pool) {
    if (!pool || typeof pool.query !== 'function' || typeof pool.connect !== 'function') throw new Error('PostgreSQL pool is required.');
    this.pool = pool;
  }

  map(row) {
    return {
      serviceId: row.service_id,
      userId: row.user_id,
      providerType: row.provider_type,
      sourceKey: row.source_key,
      externalServiceId: row.external_service_id,
      serviceUsername: row.external_service_username,
      connectorRef: row.connector_ref,
    };
  }

  async listForUser(userId) {
    const result = await this.pool.query(
      `SELECT * FROM control_service_upstream_bindings
        WHERE user_id = $1 AND provider_type = 'pasarguard'`,
      [userId],
    );
    return result.rows.map((row) => this.map(row));
  }

  async find(userId, serviceId) {
    const result = await this.pool.query(
      `SELECT * FROM control_service_upstream_bindings
        WHERE user_id = $1 AND service_id = $2 AND provider_type = 'pasarguard'`,
      [userId, serviceId],
    );
    return result.rowCount === 1 ? this.map(result.rows[0]) : null;
  }

  async upsert({ serviceId, sourceKey, externalServiceId, serviceUsername, connectorRef }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const service = await client.query(
        'SELECT user_id, tier FROM control_services WHERE id = $1 FOR UPDATE',
        [serviceId],
      );
      if (service.rowCount !== 1) throw new ApiError(404, 'service_not_found', 'Control service was not found.');
      if (service.rows[0].tier === 'free') throw new ApiError(409, 'free_service_binding_forbidden', 'Free services cannot be bound to a paid upstream.');
      const result = await client.query(
        `INSERT INTO control_service_upstream_bindings (
           service_id, user_id, provider_type, source_key, external_service_id,
           external_service_username, connector_ref
         ) VALUES ($1, $2, 'pasarguard', $3, $4, $5, $6)
         ON CONFLICT (service_id) DO UPDATE SET
           source_key = EXCLUDED.source_key,
           external_service_id = EXCLUDED.external_service_id,
           external_service_username = EXCLUDED.external_service_username,
           connector_ref = EXCLUDED.connector_ref,
           updated_at = now()
         RETURNING *`,
        [serviceId, service.rows[0].user_id, sourceKey, externalServiceId, serviceUsername, connectorRef],
      );
      await client.query('COMMIT');
      return this.map(result.rows[0]);
    } catch (error) {
      await client.query('ROLLBACK');
      if (error?.code === '23505') {
        throw new ApiError(409, 'upstream_binding_conflict', 'External service is already bound to another Ganj service.');
      }
      throw error;
    } finally {
      client.release();
    }
  }

  async bindDeviceAndReserve({
    binding,
    profileId,
    deviceId,
    virtualServerId,
    nodeId,
    clientNonceDigest,
    expiresAt,
    now,
  }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query("SET LOCAL statement_timeout = '10s'");
      await client.query("SET LOCAL lock_timeout = '3s'");
      const current = await client.query(
        `SELECT s.user_id, s.status, s.tier, s.device_limit,
                b.connector_ref, b.external_service_username
           FROM control_services s
           JOIN control_service_upstream_bindings b
             ON b.service_id = s.id AND b.user_id = s.user_id
          WHERE s.id = $1 AND s.user_id = $2
          FOR UPDATE OF s, b`,
        [binding.serviceId, binding.userId],
      );
      if (current.rowCount !== 1
        || current.rows[0].status !== 'active'
        || current.rows[0].tier === 'free'
        || current.rows[0].connector_ref !== binding.connectorRef
        || current.rows[0].external_service_username !== binding.serviceUsername) {
        throw new ApiError(403, 'entitlement_changed', 'Paid service ownership or upstream binding changed during profile issuance.');
      }
      const devices = await client.query(
        'SELECT device_id FROM control_service_devices WHERE service_id = $1 FOR UPDATE',
        [binding.serviceId],
      );
      const alreadyBound = devices.rows.some((row) => row.device_id === deviceId);
      if (!alreadyBound && devices.rowCount >= current.rows[0].device_limit) {
        throw new ApiError(403, 'device_limit_reached', 'Service device limit has been reached.');
      }
      if (!alreadyBound) {
        const inserted = await client.query(
          `INSERT INTO control_service_devices (service_id, device_id, user_id)
           SELECT $1, d.id, d.user_id FROM control_devices d
            WHERE d.id = $2 AND d.user_id = $3 AND d.status = 'active'
           ON CONFLICT DO NOTHING RETURNING device_id`,
          [binding.serviceId, deviceId, binding.userId],
        );
        if (inserted.rowCount !== 1) throw new ApiError(403, 'device_not_trusted', 'Device is not active and owned.');
      }
      const replay = await client.query(
        'SELECT 1 FROM control_connection_profile_grants WHERE device_id = $1 AND client_nonce_digest = $2',
        [deviceId, clientNonceDigest],
      );
      if (replay.rowCount > 0) throw new ApiError(409, 'profile_nonce_replayed', 'Client nonce was already used for profile issuance.');
      await client.query(
        `INSERT INTO control_connection_profile_grants (
           profile_id, user_id, service_id, device_id, server_id,
           upstream_node_id, upstream_virtual_server_id, client_nonce_digest, expires_at
         ) VALUES ($1, $2, $3, $4, NULL, $5, $6, $7, $8)`,
        [profileId, binding.userId, binding.serviceId, deviceId, nodeId,
          virtualServerId, clientNonceDigest, expiresAt],
      );
      await client.query('COMMIT');
      return true;
    } catch (error) {
      await client.query('ROLLBACK');
      if (error?.code === '23505') {
        throw new ApiError(409, 'profile_nonce_replayed', 'Client nonce was already used for profile issuance.');
      }
      throw error;
    } finally {
      client.release();
    }
  }

  async close() { await this.pool.end(); }
}

export function createPasarGuardPaidApplication({
  baseApplication,
  repository,
  auth,
  bindingStore,
  connectorRegistry,
  liveAdapter = new PasarGuardLiveServiceAdapter(),
  internalToken,
  clock = () => new Date(),
}) {
  if (typeof baseApplication !== 'function') throw new Error('Base Control API application is required.');
  if (!repository || typeof repository.listServices !== 'function' || typeof repository.findOwnedService !== 'function'
    || typeof repository.listServers !== 'function') throw new Error('Repository is incomplete for paid PasarGuard routing.');
  if (!auth || typeof auth.authenticate !== 'function' || typeof auth.verifyDeviceProof !== 'function'
    || typeof auth.sealConnectionProfile !== 'function') throw new Error('Auth adapter is incomplete for paid PasarGuard routing.');
  if (!bindingStore || typeof bindingStore.listForUser !== 'function' || typeof bindingStore.find !== 'function'
    || typeof bindingStore.upsert !== 'function' || typeof bindingStore.bindDeviceAndReserve !== 'function') {
    throw new Error('PasarGuard binding store is incomplete.');
  }
  if (!connectorRegistry || typeof connectorRegistry.resolve !== 'function') throw new Error('PasarGuard connector registry is required.');
  if (typeof internalToken !== 'string' || internalToken.length < BINDING_TOKEN_MINIMUM) {
    throw new Error('GANJ_BOT_SYNC_INTERNAL_TOKEN must contain at least 32 characters.');
  }
  const internalTokenDigest = digest(internalToken);

  async function liveServices(userId) {
    const services = await repository.listServices(userId);
    const bindings = await bindingStore.listForUser(userId);
    const byService = new Map(bindings.map((binding) => [binding.serviceId, binding]));
    const projected = [];
    for (const service of services) {
      const binding = byService.get(service.id);
      if (!binding) {
        projected.push(service);
        continue;
      }
      const { connector } = connectorRegistry.resolve(binding.connectorRef);
      try {
        const live = await liveAdapter.readLiveService({ connector, serviceUsername: binding.serviceUsername });
        projected.push(projectService(service, live, clock()));
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          projected.push({ ...service, status: 'disabled', traffic_used_bytes: 0, traffic_limit_bytes: null, expires_at: null });
          continue;
        }
        throw error;
      }
    }
    return { services: projected, bindings: byService };
  }

  async function paidVirtualServers(service, binding) {
    if (!usable(service, clock())) return [];
    const { connector, meta } = connectorRegistry.resolve(binding.connectorRef);
    const inventory = await liveAdapter.listSafeNodes({ connector, serviceUsername: binding.serviceUsername });
    const projected = projectService(service, inventory.service, clock());
    if (!usable(projected, clock())) return [];
    return inventory.nodes
      .filter((node) => service.allowed_protocols.includes(node.protocol))
      .map((node) => virtualServer({ service: projected, node, connectorMeta: meta }));
  }

  async function handlePaidProfile(request, url, principal, serviceId, binding, requestId) {
    const body = await parseBody(request);
    rejectUnknown(body, ['device_id', 'server_id', 'client_nonce', 'device_proof']);
    const deviceId = requireUuid(body.device_id, 'device_id');
    const serverId = requireUuid(body.server_id, 'server_id');
    const clientNonce = requireString(body.client_nonce, 'client_nonce', { min: 32, max: 256 });
    const proof = requireString(body.device_proof, 'device_proof', { min: 32, max: 1024 });
    if (deviceId !== principal.deviceId) {
      throw new ApiError(403, 'device_mismatch', 'Profile can only be issued to the authenticated device.');
    }
    const service = await repository.findOwnedService(principal.userId, serviceId);
    if (!service || service.tier === 'free' || service.status !== 'active') {
      throw new ApiError(403, 'service_inactive', 'Paid service ownership is not active.');
    }
    const { connector } = connectorRegistry.resolve(binding.connectorRef);
    const safe = await liveAdapter.listSafeNodes({ connector, serviceUsername: binding.serviceUsername });
    const liveService = projectService(service, safe.service, clock());
    if (!usable(liveService, clock())) {
      throw new ApiError(403, 'upstream_service_inactive', 'PasarGuard service is inactive, expired, or out of traffic.');
    }
    const selected = safe.nodes.find((node) => stableVirtualServerId(service.id, node.id) === serverId);
    if (!selected) throw new ApiError(404, 'subscription_node_not_found', 'Selected PasarGuard node is no longer available.');
    if (!service.allowed_protocols.includes(selected.protocol)) {
      throw new ApiError(403, 'protocol_not_entitled', 'Service is not entitled to the selected PasarGuard protocol.');
    }
    // Refetch and resolve connection material at issuance time. If the node disappeared or changed,
    // resolveConnection fails closed instead of using the stale safe inventory entry above.
    const resolved = await liveAdapter.resolveConnection({
      connector,
      serviceUsername: binding.serviceUsername,
      nodeId: selected.id,
    });
    const currentLive = projectService(service, resolved.service, clock());
    if (!usable(currentLive, clock()) || !service.allowed_protocols.includes(resolved.connection.protocol)) {
      throw new ApiError(403, 'entitlement_changed', 'PasarGuard entitlement changed during profile issuance.');
    }
    const unsignedBody = {
      client_nonce: clientNonce,
      device_id: deviceId,
      server_id: serverId,
    };
    if (!await auth.verifyDeviceProof({
      principal,
      payload: { serviceId, deviceId, serverId, clientNonce },
      method: request.method,
      pathAndQuery: `${url.pathname}${url.search}`,
      unsignedBody,
      proof,
    })) {
      throw new ApiError(403, 'invalid_device_proof', 'Device proof is invalid.');
    }
    const profileId = randomUUID();
    const now = clock();
    const expiresAt = new Date(now.getTime() + 5 * 60_000).toISOString();
    const associatedData = { profileId, userId: principal.userId, serviceId, deviceId, serverId, expiresAt };
    const sealed = await auth.sealConnectionProfile({
      principal,
      associatedData,
      plaintext: {
        ...resolved.connection,
        profile_id: profileId,
        service_id: serviceId,
        server_id: serverId,
        device_id: deviceId,
        expires_at: expiresAt,
      },
    });
    await bindingStore.bindDeviceAndReserve({
      binding,
      profileId,
      deviceId,
      virtualServerId: serverId,
      nodeId: selected.id,
      clientNonceDigest: digest(clientNonce),
      expiresAt,
      now,
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
  }

  return async function handle(request) {
    const url = new URL(request.url);
    const { pathname } = url;
    const relevant = pathname === '/v1/services'
      || pathname === '/v1/servers'
      || pathname === '/v1/internal/bot/service-upstream'
      || matchService(pathname)
      || matchConnectionProfile(pathname);
    if (!relevant) return baseApplication(request);
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      if (request.method === 'POST' && pathname === '/v1/internal/bot/service-upstream') {
        const supplied = bearer(request);
        if (!supplied || !safeSame(digest(supplied), internalTokenDigest)) {
          throw new ApiError(401, 'bot_sync_unauthorized', 'Ganj Bot sync authentication failed.');
        }
        const body = await parseBody(request, 8_192);
        rejectUnknown(body, ['service_id', 'source_key', 'external_service_id', 'external_service_username', 'connector_ref']);
        const binding = await bindingStore.upsert({
          serviceId: requireUuid(body.service_id, 'service_id'),
          sourceKey: requireString(body.source_key, 'source_key', { min: 1, max: 128 }),
          externalServiceId: requireString(body.external_service_id, 'external_service_id', { min: 1, max: 256 }),
          serviceUsername: requireString(body.external_service_username, 'external_service_username', { min: 1, max: 128 }),
          connectorRef: requireString(body.connector_ref, 'connector_ref', { min: 1, max: 128 }),
        });
        // Return only locator metadata. Connector credentials and subscription URL can never leave server-side runtime.
        return { status: 200, body: success({
          service_id: binding.serviceId,
          provider_type: binding.providerType,
          source_key: binding.sourceKey,
          external_service_id: binding.externalServiceId,
          connector_ref: binding.connectorRef,
        }, requestId, clock) };
      }

      const principal = await auth.authenticate(request);
      requireUuid(principal.userId, 'authenticated user id');
      requireUuid(principal.deviceId, 'authenticated device id');

      if (request.method === 'GET' && pathname === '/v1/services') {
        const projected = await liveServices(principal.userId);
        return { status: 200, body: success(projected.services.map(asService), requestId, clock) };
      }

      const serviceId = matchService(pathname);
      if (request.method === 'GET' && serviceId) {
        requireUuid(serviceId, 'service_id');
        const service = await repository.findOwnedService(principal.userId, serviceId);
        if (!service) throw new ApiError(404, 'service_not_found', 'Service was not found.');
        const binding = await bindingStore.find(principal.userId, serviceId);
        if (!binding) return baseApplication(request);
        const { connector } = connectorRegistry.resolve(binding.connectorRef);
        const live = await liveAdapter.readLiveService({ connector, serviceUsername: binding.serviceUsername });
        return { status: 200, body: success(asService(projectService(service, live, clock())), requestId, clock) };
      }

      if (request.method === 'GET' && pathname === '/v1/servers') {
        const filters = validateServerFilters(url);
        const projected = await liveServices(principal.userId);
        const staticEntitlements = projected.services.filter((service) =>
          !projected.bindings.has(service.id) && usable(service, clock()));
        const staticServers = (await repository.listServers())
          .filter((server) => staticEntitlements.some((service) =>
            TIER_RANK[server.tier] <= TIER_RANK[service.tier]
            && (!service.country_code || service.country_code === server.country_code)
            && server.protocols.some((item) => service.allowed_protocols.includes(item))))
          .map(asStaticServer)
          .filter((server) => filteredServer(server, filters));
        const virtual = [];
        for (const service of projected.services) {
          const binding = projected.bindings.get(service.id);
          if (!binding) continue;
          virtual.push(...(await paidVirtualServers(service, binding)));
        }
        const safeVirtual = virtual
          .map(({ _nodeId, ...server }) => server)
          .filter((server) => filteredServer(server, filters));
        return { status: 200, body: success([...staticServers, ...safeVirtual], requestId, clock) };
      }

      const profileServiceId = matchConnectionProfile(pathname);
      if (request.method === 'POST' && profileServiceId) {
        requireUuid(profileServiceId, 'service_id');
        const binding = await bindingStore.find(principal.userId, profileServiceId);
        if (!binding) return baseApplication(request);
        return handlePaidProfile(request, url, principal, profileServiceId, binding, requestId);
      }

      return baseApplication(request);
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}

export async function createPasarGuardPaidRuntime({ environment = process.env } = {}) {
  for (const required of ['DATABASE_URL', 'PASARGUARD_CONNECTORS_JSON', 'GANJ_BOT_SYNC_INTERNAL_TOKEN']) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.PASARGUARD_DATABASE_POOL_MAX ?? 4),
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-pasarguard-paid-runtime',
  });
  await pool.query('SELECT 1');
  return {
    bindingStore: new PostgresPasarGuardBindingStore(pool),
    connectorRegistry: new PasarGuardConnectorRegistry(environment.PASARGUARD_CONNECTORS_JSON),
    liveAdapter: new PasarGuardLiveServiceAdapter(),
    internalToken: environment.GANJ_BOT_SYNC_INTERNAL_TOKEN,
    async close() { await pool.end(); },
  };
}

export { stableVirtualServerId };
