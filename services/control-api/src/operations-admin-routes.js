import { randomUUID } from 'node:crypto';
import { createAdminReadModel } from './admin-read-model.js';
import { ApiError, failure, requireUuid, success } from './errors.js';

function requireScope(principal, scope) {
  if (!principal.scopes?.includes(scope)) throw new ApiError(403, 'insufficient_scope', `The ${scope} scope is required.`);
}
function connectorRef(value) {
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(value)) throw new ApiError(400, 'invalid_connector_ref', 'connector_ref is invalid.');
  return value;
}
function safeConnectorList(runtime) {
  if (!(runtime?.connectorRegistry?.connectors instanceof Map)) return [];
  return [...runtime.connectorRegistry.connectors.entries()].map(([ref, entry]) => {
    const url = new URL(entry.connector.baseUrl);
    return {
      connector_ref: ref,
      endpoint_origin: url.origin,
      base_path: url.pathname || '/',
      country_code: entry.meta?.countryCode ?? null,
      city: entry.meta?.city ?? null,
      credential_configured: Boolean(entry.connector.adminUsername && entry.connector.adminPassword),
      subscription_origin_count: Array.isArray(entry.connector.subscriptionOrigins) ? entry.connector.subscriptionOrigins.length : 0,
    };
  }).sort((left, right) => left.connector_ref.localeCompare(right.connector_ref));
}
function protocolSummary(nodes) {
  const counts = {};
  for (const node of nodes) counts[node.protocol] = (counts[node.protocol] ?? 0) + 1;
  return counts;
}

export function createOperationsAdminApplication({
  baseApplication,
  repository,
  auth,
  paidRuntime = null,
  readModel = null,
  clock = () => new Date(),
}) {
  if (typeof baseApplication !== 'function' || !repository || !auth || typeof auth.authenticate !== 'function') {
    throw new Error('Operations Admin application dependencies are incomplete.');
  }
  const adminReadModel = readModel ?? createAdminReadModel(repository);
  if (!adminReadModel || typeof adminReadModel.sharedAccount !== 'function' || typeof adminReadModel.findUpstreamBinding !== 'function') {
    throw new Error('Operations Admin read model is incomplete.');
  }
  return async function handle(request) {
    const url = new URL(request.url);
    const accountMatch = url.pathname.match(/^\/v1\/admin\/shared-account\/users\/([0-9a-f-]{36})$/i);
    const healthMatch = url.pathname.match(/^\/v1\/admin\/pasarguard\/connectors\/([A-Za-z0-9._:-]{1,128})\/health$/);
    const diagnosticMatch = url.pathname.match(/^\/v1\/admin\/pasarguard\/services\/([0-9a-f-]{36})\/diagnostic$/i);
    const relevant = accountMatch || healthMatch || diagnosticMatch || url.pathname === '/v1/admin/pasarguard/connectors';
    if (!relevant) return baseApplication(request);
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      if (request.method !== 'GET') throw new ApiError(405, 'method_not_allowed', 'Operations Admin endpoints are read-only.');
      const principal = await auth.authenticate(request);
      requireUuid(principal.userId, 'authenticated user id');
      requireUuid(principal.deviceId, 'authenticated device id');

      if (accountMatch) {
        requireScope(principal, 'admin:account:read');
        const userId = requireUuid(accountMatch[1], 'user_id');
        const account = await adminReadModel.sharedAccount(userId);
        if (!account) throw new ApiError(404, 'account_not_found', 'Ganj account was not found.');
        return { status: 200, body: success(account, requestId, clock) };
      }

      requireScope(principal, 'admin:pasarguard:read');
      if (!paidRuntime?.connectorRegistry || !paidRuntime?.liveAdapter) {
        throw new ApiError(503, 'pasarguard_admin_unavailable', 'PasarGuard diagnostics are not configured.', { retryable: true });
      }
      if (url.pathname === '/v1/admin/pasarguard/connectors') {
        return { status: 200, body: success(safeConnectorList(paidRuntime), requestId, clock) };
      }
      if (healthMatch) {
        const ref = connectorRef(healthMatch[1]);
        const { connector } = paidRuntime.connectorRegistry.resolve(ref);
        const started = Date.now();
        const token = await paidRuntime.liveAdapter.authenticate(connector);
        if (typeof token !== 'string' || token.length < 1) throw new ApiError(502, 'pasarguard_protocol_error', 'PasarGuard health probe returned invalid authentication state.');
        return { status: 200, body: success({ connector_ref: ref, status: 'healthy', latency_ms: Math.max(0, Date.now() - started) }, requestId, clock) };
      }
      if (diagnosticMatch) {
        const serviceId = requireUuid(diagnosticMatch[1], 'service_id');
        const binding = await adminReadModel.findUpstreamBinding(serviceId);
        if (!binding || binding.providerType !== 'pasarguard') {
          throw new ApiError(404, 'upstream_binding_not_found', 'PasarGuard service binding was not found.');
        }
        const { connector } = paidRuntime.connectorRegistry.resolve(binding.connectorRef);
        const live = await paidRuntime.liveAdapter.readLiveService({ connector, serviceUsername: binding.serviceUsername });
        let inventory = { status: 'not_available', node_count: 0, protocols: {} };
        if (['active', 'on_hold'].includes(live.status)) {
          const safe = await paidRuntime.liveAdapter.listSafeNodes({ connector, serviceUsername: binding.serviceUsername });
          inventory = { status: 'available', node_count: safe.nodes.length, protocols: protocolSummary(safe.nodes) };
        }
        return {
          status: 200,
          body: success({
            service_id: binding.serviceId,
            user_id: binding.userId,
            source_key: binding.sourceKey,
            external_service_id: binding.externalServiceId,
            external_service_username: binding.serviceUsername,
            connector_ref: binding.connectorRef,
            commercial_status: binding.serviceStatus,
            commercial_tier: binding.serviceTier,
            live: {
              status: live.status,
              expires_at: live.expiresAt,
              data_limit_bytes: live.dataLimitBytes,
              used_traffic_bytes: live.usedTrafficBytes,
              subscription_locator_present: Boolean(live.subscriptionUrl),
            },
            inventory,
          }, requestId, clock),
        };
      }
      throw new ApiError(404, 'admin_route_not_found', 'Operations Admin route was not found.');
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
