import { createHash, randomUUID } from 'node:crypto';
import { ApiError, failure, rejectUnknown, requireObject, requireString, requireUuid, success } from './errors.js';
import { parseBody } from './application.js';

const SERVER_STATUSES = new Set(['active', 'busy', 'maintenance', 'disabled']);
const SERVER_PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);
const REFERENCE = /^[A-Za-z0-9._:/@-]{1,256}$/;

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, stable(item)]));
  }
  return value;
}
function digest(value) { return createHash('sha256').update(JSON.stringify(stable(value))).digest('hex'); }
function requireScope(principal, scope) {
  if (!principal.scopes?.includes(scope)) throw new ApiError(403, 'insufficient_scope', `The ${scope} scope is required.`);
}
function bool(value, name) {
  if (typeof value !== 'boolean') throw new ApiError(400, 'invalid_request', `${name} must be boolean.`);
  return value;
}
function integer(value, name, minimum, maximum) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) throw new ApiError(400, 'invalid_request', `${name} is invalid.`);
  return value;
}
function ratio(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) throw new ApiError(400, 'invalid_request', `${name} is invalid.`);
  return value;
}
function optionalText(value, name, maximum = 200) {
  if (value === undefined) return undefined;
  if (value === null) return null;
  return requireString(value, name, { min: 1, max: maximum }).trim();
}
function reference(value, name) {
  const parsed = requireString(value, name, { min: 1, max: 256 });
  if (!REFERENCE.test(parsed) || /[\r\n]/.test(parsed)) throw new ApiError(400, 'invalid_request', `${name} is invalid.`);
  return parsed;
}
function semver(value, name) {
  if (value === null) return null;
  const parsed = requireString(value, name, { min: 5, max: 32 });
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(parsed)) throw new ApiError(400, 'invalid_request', `${name} must be semantic version.`);
  return parsed;
}
function protocols(value) {
  if (!Array.isArray(value) || value.length < 1 || value.length > 4) throw new ApiError(400, 'invalid_request', 'protocols is invalid.');
  const result = [...new Set(value.map((item) => requireString(item, 'protocol', { min: 3, max: 16 })) )];
  if (result.some((item) => !SERVER_PROTOCOLS.has(item)) || !result.includes('vless')) {
    throw new ApiError(400, 'invalid_request', 'Free server protocols must include vless and contain only supported protocols.');
  }
  return result;
}
function status(value) {
  const parsed = requireString(value, 'status', { min: 4, max: 16 });
  if (!SERVER_STATUSES.has(parsed)) throw new ApiError(400, 'invalid_request', 'status is invalid.');
  return parsed;
}
function safeServer(control) {
  return {
    id: control.id,
    code: control.code,
    name: control.name,
    country_code: control.countryCode,
    city: control.city,
    status: control.status,
    load_ratio: control.loadRatio,
    latency_hint_ms: control.latencyHintMs,
    protocols: [...control.protocols],
    priority: control.priority,
    provider_ref: control.providerRef,
    upstream_ref: control.upstreamRef,
    max_load_ratio: control.maxLoadRatio,
    max_active_profile_grants: control.maxActiveProfileGrants,
    emergency_disabled: control.emergencyDisabled,
    rollout_percent: control.rolloutBasisPoints / 100,
    min_app_version: control.minAppVersion,
    secret_ref_configured: Boolean(control.secretRef),
    updated_at: control.updatedAt,
  };
}
function safePolicy(policy) {
  return {
    enabled: policy.enabled,
    maintenance: policy.maintenance,
    emergency_disabled: policy.emergencyDisabled,
    issue_window_seconds: policy.issueWindowSeconds,
    max_issues_per_user: policy.maxIssuesPerUser,
    max_issues_per_device: policy.maxIssuesPerDevice,
    max_active_grants_per_user: policy.maxActiveGrantsPerUser,
    max_active_grants_per_device: policy.maxActiveGrantsPerDevice,
    max_active_grants_per_server: policy.maxActiveGrantsPerServer,
    updated_by: policy.updatedBy ?? null,
    updated_at: policy.updatedAt ?? null,
  };
}
function reason(body) { return requireString(body.reason, 'reason', { min: 8, max: 500 }).trim(); }
function policyPatch(body) {
  const patch = {};
  if (body.enabled !== undefined) patch.enabled = bool(body.enabled, 'enabled');
  if (body.maintenance !== undefined) patch.maintenance = bool(body.maintenance, 'maintenance');
  if (body.emergency_disabled !== undefined) patch.emergencyDisabled = bool(body.emergency_disabled, 'emergency_disabled');
  if (body.issue_window_seconds !== undefined) patch.issueWindowSeconds = integer(body.issue_window_seconds, 'issue_window_seconds', 60, 86_400);
  if (body.max_issues_per_user !== undefined) patch.maxIssuesPerUser = integer(body.max_issues_per_user, 'max_issues_per_user', 1, 10_000);
  if (body.max_issues_per_device !== undefined) patch.maxIssuesPerDevice = integer(body.max_issues_per_device, 'max_issues_per_device', 1, 10_000);
  if (body.max_active_grants_per_user !== undefined) patch.maxActiveGrantsPerUser = integer(body.max_active_grants_per_user, 'max_active_grants_per_user', 1, 1_000);
  if (body.max_active_grants_per_device !== undefined) patch.maxActiveGrantsPerDevice = integer(body.max_active_grants_per_device, 'max_active_grants_per_device', 1, 1_000);
  if (body.max_active_grants_per_server !== undefined) patch.maxActiveGrantsPerServer = integer(body.max_active_grants_per_server, 'max_active_grants_per_server', 1, 100_000);
  if (Object.keys(patch).length === 0) throw new ApiError(400, 'invalid_request', 'At least one Free policy field must be changed.');
  return patch;
}
function createServer(body) {
  const code = requireString(body.code, 'code', { min: 2, max: 128 });
  if (!/^[a-z0-9][a-z0-9._-]{1,127}$/.test(code)) throw new ApiError(400, 'invalid_request', 'code is invalid.');
  const countryCode = requireString(body.country_code, 'country_code', { min: 2, max: 2 });
  if (!/^[A-Z]{2}$/.test(countryCode)) throw new ApiError(400, 'invalid_request', 'country_code is invalid.');
  return {
    id: body.id === undefined ? randomUUID() : requireUuid(body.id, 'id'),
    code,
    name: requireString(body.name, 'name', { min: 2, max: 200 }).trim(),
    countryCode,
    city: optionalText(body.city, 'city'),
    status: status(body.status ?? 'active'),
    loadRatio: body.load_ratio === undefined ? 0 : ratio(body.load_ratio, 'load_ratio'),
    latencyHintMs: body.latency_hint_ms == null ? null : integer(body.latency_hint_ms, 'latency_hint_ms', 0, 120_000),
    protocols: protocols(body.protocols),
    secretRef: reference(body.secret_ref, 'secret_ref'),
    priority: body.priority === undefined ? 100 : integer(body.priority, 'priority', 0, 100_000),
    providerRef: body.provider_ref == null ? null : reference(body.provider_ref, 'provider_ref'),
    upstreamRef: body.upstream_ref == null ? null : reference(body.upstream_ref, 'upstream_ref'),
    maxLoadRatio: body.max_load_ratio === undefined ? 1 : ratio(body.max_load_ratio, 'max_load_ratio'),
    maxActiveProfileGrants: body.max_active_profile_grants == null ? null : integer(body.max_active_profile_grants, 'max_active_profile_grants', 1, 100_000),
    emergencyDisabled: body.emergency_disabled === undefined ? false : bool(body.emergency_disabled, 'emergency_disabled'),
    rolloutBasisPoints: body.rollout_percent === undefined ? 10_000 : Math.round(ratio(body.rollout_percent / 100, 'rollout_percent') * 10_000),
    minAppVersion: body.min_app_version == null ? null : semver(body.min_app_version, 'min_app_version'),
  };
}
function serverPatch(body) {
  const patch = {};
  if (body.code !== undefined) {
    const code = requireString(body.code, 'code', { min: 2, max: 128 });
    if (!/^[a-z0-9][a-z0-9._-]{1,127}$/.test(code)) throw new ApiError(400, 'invalid_request', 'code is invalid.');
    patch.code = code;
  }
  if (body.name !== undefined) patch.name = requireString(body.name, 'name', { min: 2, max: 200 }).trim();
  if (body.country_code !== undefined) {
    const country = requireString(body.country_code, 'country_code', { min: 2, max: 2 });
    if (!/^[A-Z]{2}$/.test(country)) throw new ApiError(400, 'invalid_request', 'country_code is invalid.');
    patch.countryCode = country;
  }
  if (body.city !== undefined) patch.city = optionalText(body.city, 'city');
  if (body.status !== undefined) patch.status = status(body.status);
  if (body.load_ratio !== undefined) patch.loadRatio = ratio(body.load_ratio, 'load_ratio');
  if (body.latency_hint_ms !== undefined) patch.latencyHintMs = body.latency_hint_ms == null ? null : integer(body.latency_hint_ms, 'latency_hint_ms', 0, 120_000);
  if (body.protocols !== undefined) patch.protocols = protocols(body.protocols);
  if (body.secret_ref !== undefined) patch.secretRef = reference(body.secret_ref, 'secret_ref');
  if (body.priority !== undefined) patch.priority = integer(body.priority, 'priority', 0, 100_000);
  if (body.provider_ref !== undefined) patch.providerRef = body.provider_ref == null ? null : reference(body.provider_ref, 'provider_ref');
  if (body.upstream_ref !== undefined) patch.upstreamRef = body.upstream_ref == null ? null : reference(body.upstream_ref, 'upstream_ref');
  if (body.max_load_ratio !== undefined) patch.maxLoadRatio = ratio(body.max_load_ratio, 'max_load_ratio');
  if (body.max_active_profile_grants !== undefined) patch.maxActiveProfileGrants = body.max_active_profile_grants == null ? null : integer(body.max_active_profile_grants, 'max_active_profile_grants', 1, 100_000);
  if (body.emergency_disabled !== undefined) patch.emergencyDisabled = bool(body.emergency_disabled, 'emergency_disabled');
  if (body.rollout_percent !== undefined) patch.rolloutBasisPoints = Math.round(ratio(body.rollout_percent / 100, 'rollout_percent') * 10_000);
  if (body.min_app_version !== undefined) patch.minAppVersion = body.min_app_version == null ? null : semver(body.min_app_version, 'min_app_version');
  if (Object.keys(patch).length === 0) throw new ApiError(400, 'invalid_request', 'At least one Free server field must be changed.');
  return patch;
}

export function createFreeAdminApplication({ baseApplication, repository, auth, clock = () => new Date() }) {
  if (typeof baseApplication !== 'function' || !repository || typeof repository.readFreePolicy !== 'function'
    || typeof repository.updateFreePolicy !== 'function' || typeof repository.listManagedFreeServers !== 'function'
    || typeof repository.createManagedFreeServer !== 'function' || typeof repository.updateManagedFreeServer !== 'function'
    || typeof repository.appendAdminAudit !== 'function' || !auth || typeof auth.authenticate !== 'function') {
    throw new Error('Free Admin application dependencies are incomplete.');
  }
  return async function handle(request) {
    const url = new URL(request.url);
    const { pathname } = url;
    const serverMatch = pathname.match(/^\/v1\/admin\/free\/servers\/([0-9a-f-]{36})$/i);
    const relevant = pathname === '/v1/admin/free/policy' || pathname === '/v1/admin/free/servers' || serverMatch;
    if (!relevant) return baseApplication(request);
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      const principal = await auth.authenticate(request);
      requireUuid(principal.userId, 'authenticated user id');
      requireUuid(principal.deviceId, 'authenticated device id');
      const actorSubject = principal.subject ?? principal.userId;

      if (request.method === 'GET' && pathname === '/v1/admin/free/policy') {
        requireScope(principal, 'admin:free:read');
        return { status: 200, body: success(safePolicy(await repository.readFreePolicy()), requestId, clock) };
      }
      if (request.method === 'PATCH' && pathname === '/v1/admin/free/policy') {
        requireScope(principal, 'admin:free:write');
        const body = await parseBody(request);
        rejectUnknown(body, ['enabled', 'maintenance', 'emergency_disabled', 'issue_window_seconds', 'max_issues_per_user',
          'max_issues_per_device', 'max_active_grants_per_user', 'max_active_grants_per_device', 'max_active_grants_per_server', 'reason']);
        const why = reason(body);
        const patch = policyPatch(body);
        const updated = await repository.transaction(async () => {
          const before = safePolicy(await repository.readFreePolicy());
          const next = await repository.updateFreePolicy(patch, { actorId: principal.userId });
          const after = safePolicy(next);
          await repository.appendAdminAudit({ actorSubject, action: 'free_policy.update', resourceType: 'free_access_policy', resourceId: 'default',
            reason: why, requestId, beforeDigest: digest(before), afterDigest: digest(after), outcome: 'success', createdAt: clock().toISOString() });
          return after;
        });
        return { status: 200, body: success(updated, requestId, clock) };
      }
      if (request.method === 'GET' && pathname === '/v1/admin/free/servers') {
        requireScope(principal, 'admin:free:read');
        return { status: 200, body: success((await repository.listManagedFreeServers()).map(safeServer), requestId, clock) };
      }
      if (request.method === 'POST' && pathname === '/v1/admin/free/servers') {
        requireScope(principal, 'admin:free:write');
        const body = requireObject(await parseBody(request));
        rejectUnknown(body, ['id', 'code', 'name', 'country_code', 'city', 'status', 'load_ratio', 'latency_hint_ms', 'protocols',
          'secret_ref', 'priority', 'provider_ref', 'upstream_ref', 'max_load_ratio', 'max_active_profile_grants',
          'emergency_disabled', 'rollout_percent', 'min_app_version', 'reason']);
        const why = reason(body);
        const value = createServer(body);
        const created = await repository.transaction(async () => {
          const server = await repository.createManagedFreeServer(value);
          const safe = safeServer(server);
          await repository.appendAdminAudit({ actorSubject, action: 'free_server.create', resourceType: 'free_server', resourceId: server.id,
            reason: why, requestId, afterDigest: digest(safe), outcome: 'success', createdAt: clock().toISOString() });
          return safe;
        });
        return { status: 201, body: success(created, requestId, clock) };
      }
      if (request.method === 'PATCH' && serverMatch) {
        requireScope(principal, 'admin:free:write');
        const body = await parseBody(request);
        rejectUnknown(body, ['code', 'name', 'country_code', 'city', 'status', 'load_ratio', 'latency_hint_ms', 'protocols',
          'secret_ref', 'priority', 'provider_ref', 'upstream_ref', 'max_load_ratio', 'max_active_profile_grants',
          'emergency_disabled', 'rollout_percent', 'min_app_version', 'reason']);
        const why = reason(body);
        const serverId = requireUuid(serverMatch[1], 'server_id');
        const patch = serverPatch(body);
        const updated = await repository.transaction(async () => {
          const beforeServer = await repository.findManagedFreeServer(serverId);
          if (!beforeServer) throw new ApiError(404, 'free_server_not_found', 'Free server was not found.');
          const server = await repository.updateManagedFreeServer(serverId, patch);
          const before = safeServer(beforeServer);
          const after = safeServer(server);
          await repository.appendAdminAudit({ actorSubject, action: 'free_server.update', resourceType: 'free_server', resourceId: serverId,
            reason: why, requestId, beforeDigest: digest(before), afterDigest: digest(after), outcome: 'success', createdAt: clock().toISOString() });
          return after;
        });
        return { status: 200, body: success(updated, requestId, clock) };
      }
      throw new ApiError(405, 'method_not_allowed', 'Method is not allowed for this Free Admin route.');
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
