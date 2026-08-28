import { createHash, randomUUID } from 'node:crypto';
import { ApiError, rejectUnknown, requireObject, requireString, requireUuid, success } from './errors.js';

const ADMIN_SCOPE = 'admin:control-plane';
const TIERS = new Set(['free', 'premium', 'vip']);
const STATUSES = new Set(['active', 'busy', 'maintenance', 'disabled']);
const PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);
const SECRET_REFERENCE = /^(?:vault:[A-Za-z0-9][A-Za-z0-9._/-]{2,240}|file:[A-Za-z0-9][A-Za-z0-9._/-]{2,200})$/;

function match(pathname, pattern) {
  const actual = pathname.split('/').filter(Boolean);
  const expected = pattern.split('/').filter(Boolean);
  if (actual.length !== expected.length) return null;
  const params = {};
  for (let index = 0; index < expected.length; index += 1) {
    if (expected[index].startsWith(':')) params[expected[index].slice(1)] = actual[index];
    else if (expected[index] !== actual[index]) return null;
  }
  return params;
}

function digest(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

function requireAdmin(principal) {
  if (!Array.isArray(principal?.scopes) || !principal.scopes.includes(ADMIN_SCOPE)) {
    throw new ApiError(403, 'admin_scope_required', 'Control-plane administrator scope is required.');
  }
}

function requireCountry(value) {
  if (typeof value !== 'string' || !/^[A-Z]{2}$/.test(value)) {
    throw new ApiError(400, 'invalid_country', 'country_code must be ISO 3166-1 alpha-2 uppercase.');
  }
  return value;
}

function requireTier(value) {
  if (typeof value !== 'string' || !TIERS.has(value)) throw new ApiError(400, 'invalid_tier', 'tier is invalid.');
  return value;
}

function requireStatus(value) {
  if (typeof value !== 'string' || !STATUSES.has(value)) throw new ApiError(400, 'invalid_server_status', 'status is invalid.');
  return value;
}

function requireProtocols(value) {
  if (!Array.isArray(value) || value.length === 0 || value.length > 4) {
    throw new ApiError(400, 'invalid_protocols', 'protocols must contain one to four supported protocols.');
  }
  const unique = [...new Set(value)];
  if (unique.length !== value.length || unique.some((item) => typeof item !== 'string' || !PROTOCOLS.has(item))) {
    throw new ApiError(400, 'invalid_protocols', 'protocols contains an unsupported or duplicate protocol.');
  }
  return unique;
}

function requireRatio(value) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) {
    throw new ApiError(400, 'invalid_load_ratio', 'load_ratio must be between 0 and 1.');
  }
  return value;
}

function requireLatency(value) {
  if (value === null) return null;
  if (!Number.isInteger(value) || value < 0 || value > 60_000) {
    throw new ApiError(400, 'invalid_latency_hint', 'latency_hint_ms must be null or an integer from 0 to 60000.');
  }
  return value;
}

function requireSecretReference(value) {
  if (
    typeof value !== 'string' ||
    !SECRET_REFERENCE.test(value) ||
    value.includes('..') ||
    value.includes('//')
  ) {
    throw new ApiError(
      400,
      'invalid_secret_reference',
      'secret_reference must use an approved vault: or file: locator.',
    );
  }
  return value;
}

function present(server) {
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
    protocols: [...server.protocols],
    secret_configured: Boolean(server.secret_ref ?? server.secretRef),
    updated_at: server.updated_at ?? null,
  };
}

function createPayload(body) {
  rejectUnknown(body, [
    'code', 'name', 'country_code', 'city', 'tier', 'status', 'load_ratio',
    'latency_hint_ms', 'protocols', 'secret_reference',
  ]);
  return {
    id: randomUUID(),
    code: requireString(body.code, 'code', { min: 3, max: 64 }),
    name: requireString(body.name, 'name', { min: 2, max: 120 }),
    country_code: requireCountry(body.country_code),
    city: body.city == null ? null : requireString(body.city, 'city', { min: 1, max: 120 }),
    tier: requireTier(body.tier),
    status: requireStatus(body.status ?? 'maintenance'),
    load_ratio: requireRatio(body.load_ratio ?? 0),
    latency_hint_ms: requireLatency(body.latency_hint_ms ?? null),
    protocols: requireProtocols(body.protocols),
    secret_ref: requireSecretReference(body.secret_reference),
  };
}

function patchPayload(existing, body) {
  rejectUnknown(body, ['name', 'country_code', 'city', 'tier', 'status', 'load_ratio', 'latency_hint_ms', 'protocols']);
  if (Object.keys(body).length === 0) throw new ApiError(400, 'empty_patch', 'At least one mutable server field is required.');
  return {
    ...existing,
    ...(body.name === undefined ? {} : { name: requireString(body.name, 'name', { min: 2, max: 120 }) }),
    ...(body.country_code === undefined ? {} : { country_code: requireCountry(body.country_code) }),
    ...(body.city === undefined ? {} : { city: body.city === null ? null : requireString(body.city, 'city', { min: 1, max: 120 }) }),
    ...(body.tier === undefined ? {} : { tier: requireTier(body.tier) }),
    ...(body.status === undefined ? {} : { status: requireStatus(body.status) }),
    ...(body.load_ratio === undefined ? {} : { load_ratio: requireRatio(body.load_ratio) }),
    ...(body.latency_hint_ms === undefined ? {} : { latency_hint_ms: requireLatency(body.latency_hint_ms) }),
    ...(body.protocols === undefined ? {} : { protocols: requireProtocols(body.protocols) }),
  };
}

async function audit(repository, principal, requestId, action, targetId, before, after) {
  if (typeof repository.appendAdminAudit !== 'function') return;
  await repository.appendAdminAudit({
    id: randomUUID(),
    actorSubject: principal.subject ?? `user:${principal.userId}`,
    action,
    resourceType: 'vpn_server',
    resourceId: targetId,
    reason: 'admin_control_plane',
    requestId,
    beforeDigest: before ? digest(present(before)) : null,
    afterDigest: after ? digest(present(after)) : null,
    outcome: 'success',
    createdAt: new Date().toISOString(),
  });
}

export function createAdminControlPlaneRouter({ repository, clock = () => new Date(), parseBody }) {
  for (const method of ['listServers', 'findServer', 'createServer', 'saveServer']) {
    if (typeof repository?.[method] !== 'function') throw new Error(`Admin control-plane repository must implement ${method}().`);
  }
  if (typeof parseBody !== 'function') throw new Error('Admin control-plane parseBody boundary is required.');

  return async function route({ request, pathname, principal, requestId }) {
    if (!pathname.startsWith('/v1/admin/control-plane/')) return null;
    requireAdmin(principal);

    if (request.method === 'GET' && pathname === '/v1/admin/control-plane/servers') {
      const servers = await repository.listServers();
      return { status: 200, body: success(servers.map(present), requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/admin/control-plane/servers') {
      const value = createPayload(requireObject(await parseBody(request), 'body'));
      const created = await repository.transaction(async () => {
        const stored = await repository.createServer(value);
        await audit(repository, principal, requestId, 'server.create', stored.id, null, stored);
        return stored;
      });
      return { status: 201, body: success(present(created), requestId, clock) };
    }

    const params = match(pathname, '/v1/admin/control-plane/servers/:serverId');
    if (!params) return null;
    const serverId = requireUuid(params.serverId, 'server_id');
    const existing = await repository.findServer(serverId);
    if (!existing) throw new ApiError(404, 'server_not_found', 'Server was not found.');

    if (request.method === 'GET') {
      return { status: 200, body: success(present(existing), requestId, clock) };
    }

    if (request.method === 'PATCH') {
      const body = requireObject(await parseBody(request), 'body');
      const next = patchPayload(existing, body);
      const saved = await repository.transaction(async () => {
        const stored = await repository.saveServer(next);
        await audit(repository, principal, requestId, 'server.update', stored.id, existing, stored);
        return stored;
      });
      return { status: 200, body: success(present(saved), requestId, clock) };
    }

    throw new ApiError(405, 'method_not_allowed', 'Method is not allowed for this admin resource.');
  };
}
