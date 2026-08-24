import { createHash, randomUUID } from 'node:crypto';
import {
  ApiError,
  failure,
  rejectUnknown,
  requireIdempotencyKey,
  requireObject,
  requireString,
  requireUuid,
  success,
} from './errors.js';

const TIER_RANK = Object.freeze({ free: 0, premium: 1, vip: 2 });
const CHANNELS = new Set(['play', 'direct', 'wallet']);
const SERVER_PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks', 'wireguard']);

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

function asPlan(plan) {
  return {
    id: plan.id,
    code: plan.code,
    name: plan.name,
    tier: plan.tier,
    duration_days: plan.duration_days,
    traffic_limit_bytes: plan.traffic_limit_bytes,
    device_limit: plan.device_limit,
    features: [...plan.features],
    price: { ...plan.price },
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

function asOrder(order) {
  return {
    id: order.id,
    status: order.status,
    channel: order.channel,
    total: { ...order.total },
    entitlement_service_id: order.entitlementServiceId ?? null,
  };
}

function isUsable(service, now) {
  if (service.status !== 'active') return false;
  if (service.expires_at && Date.parse(service.expires_at) <= now.getTime()) return false;
  if (service.traffic_limit_bytes !== null && service.traffic_used_bytes >= service.traffic_limit_bytes) return false;
  return true;
}

function assertPort(name, value, methods) {
  if (!value || methods.some((method) => typeof value[method] !== 'function')) {
    throw new Error(`${name} adapter does not implement the required boundary.`);
  }
}

async function parseBody(request) {
  const type = request.headers.get('content-type') ?? '';
  if (!type.toLowerCase().startsWith('application/json')) {
    throw new ApiError(400, 'invalid_content_type', 'Content-Type must be application/json.');
  }
  const declaredLength = Number(request.headers.get('content-length') ?? 0);
  if (declaredLength > 32_768) {
    throw new ApiError(413, 'request_too_large', 'Request body exceeds 32 KiB.');
  }
  const raw = await request.text();
  if (Buffer.byteLength(raw, 'utf8') > 32_768) {
    throw new ApiError(413, 'request_too_large', 'Request body exceeds 32 KiB.');
  }
  try {
    return requireObject(JSON.parse(raw), 'body');
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(400, 'invalid_json', 'Request body is not valid JSON.');
  }
}

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

function requireOwnedService(repository, userId, serviceId) {
  requireUuid(serviceId, 'service_id');
  const service = repository.findOwnedService(userId, serviceId);
  if (!service) throw new ApiError(404, 'service_not_found', 'Service was not found.');
  return service;
}

function requireOwnedOrder(repository, userId, orderId) {
  requireUuid(orderId, 'order_id');
  const order = repository.findOwnedOrder(userId, orderId);
  if (!order) throw new ApiError(404, 'order_not_found', 'Order was not found.');
  return order;
}

function assertIdempotency(repository, { scope, userId, key, fingerprint }) {
  const existing = repository.findIdempotency(scope, userId, key);
  if (!existing) return null;
  if (existing.fingerprint !== fingerprint) {
    throw new ApiError(409, 'idempotency_conflict', 'Idempotency-Key was already used with a different request.');
  }
  return existing;
}

function allowedProtocolsForTier(tier) {
  if (tier === 'vip') return ['vless', 'vmess', 'trojan', 'shadowsocks'];
  if (tier === 'premium') return ['vless', 'trojan'];
  return ['vless'];
}

function fulfillOrder(repository, order, plan, now) {
  let service = order.serviceId ? repository.findOwnedService(order.userId, order.serviceId) : null;
  if (order.serviceId && !service) {
    throw new ApiError(409, 'renewal_target_unavailable', 'The renewal target is no longer available.');
  }
  const duration = plan.duration_days === null ? null : plan.duration_days * 86_400_000;
  if (service) {
    const currentExpiry = service.expires_at ? Date.parse(service.expires_at) : now.getTime();
    const startsAt = Math.max(now.getTime(), Number.isFinite(currentExpiry) ? currentExpiry : now.getTime());
    service = repository.saveService({
      ...service,
      planId: plan.id,
      name: plan.name,
      status: 'active',
      tier: plan.tier,
      traffic_limit_bytes: plan.traffic_limit_bytes,
      traffic_used_bytes: 0,
      expires_at: duration === null ? null : new Date(startsAt + duration).toISOString(),
      device_limit: plan.device_limit,
      allowed_protocols: allowedProtocolsForTier(plan.tier),
    });
  } else {
    service = repository.createService({
      userId: order.userId,
      planId: plan.id,
      name: plan.name,
      status: 'active',
      tier: plan.tier,
      country_code: null,
      traffic_limit_bytes: plan.traffic_limit_bytes,
      traffic_used_bytes: 0,
      expires_at: duration === null ? null : new Date(now.getTime() + duration).toISOString(),
      device_limit: plan.device_limit,
      allowed_protocols: allowedProtocolsForTier(plan.tier),
      deviceIds: [],
    });
  }
  return repository.saveOrder({
    ...order,
    status: 'fulfilled',
    entitlementServiceId: service.id,
    fulfilledAt: now.toISOString(),
  });
}

export function createApplication({ repository, auth, purchaseVerifier, clock = () => new Date() }) {
  assertPort('repository', repository, [
    'transaction',
    'listPlans', 'findPlan', 'listServices', 'findOwnedService', 'saveService', 'createService',
    'listServers', 'findServer', 'createOrder', 'findOwnedOrder', 'saveOrder',
    'findIdempotency', 'saveIdempotency', 'findPurchaseTokenOwner', 'bindPurchaseToken',
  ]);
  assertPort('auth', auth, ['authenticate', 'verifyDeviceProof', 'sealConnectionProfile']);
  assertPort('purchase verifier', purchaseVerifier, ['verifyPlayPurchase']);

  return async function handle(request) {
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      const url = new URL(request.url);
      const { pathname } = url;

      if (request.method === 'GET' && pathname === '/healthz') {
        return { status: 200, body: { status: 'ok' } };
      }

      if (request.method === 'GET' && pathname === '/v1/store/plans') {
        const channel = url.searchParams.get('channel');
        if (!channel || !CHANNELS.has(channel) || channel === 'wallet') {
          throw new ApiError(400, 'invalid_channel', 'channel must be play or direct.');
        }
        return { status: 200, body: success(repository.listPlans(channel).map(asPlan), requestId, clock) };
      }

      const principal = await auth.authenticate(request);
      requireUuid(principal.userId, 'authenticated user id');
      requireUuid(principal.deviceId, 'authenticated device id');

      if (request.method === 'GET' && pathname === '/v1/services') {
        return { status: 200, body: success(repository.listServices(principal.userId).map(asService), requestId, clock) };
      }

      let params = match(pathname, '/v1/services/:serviceId');
      if (request.method === 'GET' && params) {
        return { status: 200, body: success(asService(requireOwnedService(repository, principal.userId, params.serviceId)), requestId, clock) };
      }

      if (request.method === 'GET' && pathname === '/v1/servers') {
        const now = clock();
        const entitlements = repository.listServices(principal.userId).filter((service) => isUsable(service, now));
        const tier = url.searchParams.get('tier');
        const country = url.searchParams.get('country');
        const protocol = url.searchParams.get('protocol');
        if (tier && !(tier in TIER_RANK)) throw new ApiError(400, 'invalid_tier', 'tier is invalid.');
        if (country && !/^[A-Z]{2}$/.test(country)) throw new ApiError(400, 'invalid_country', 'country must be ISO 3166-1 alpha-2 uppercase.');
        if (protocol && !SERVER_PROTOCOLS.has(protocol)) throw new ApiError(400, 'invalid_protocol', 'protocol is invalid.');
        const visible = repository.listServers().filter((server) => {
          if (server.status !== 'active') return false;
          if (tier && server.tier !== tier) return false;
          if (country && server.country_code !== country) return false;
          if (protocol && !server.protocols.includes(protocol)) return false;
          return entitlements.some((service) =>
            TIER_RANK[server.tier] <= TIER_RANK[service.tier]
            && (!service.country_code || service.country_code === server.country_code)
            && server.protocols.some((item) => service.allowed_protocols.includes(item)),
          );
        });
        return { status: 200, body: success(visible.map(asServer), requestId, clock) };
      }

      params = match(pathname, '/v1/services/:serviceId/connection-profile');
      if (request.method === 'POST' && params) {
        const body = await parseBody(request);
        rejectUnknown(body, ['device_id', 'server_id', 'client_nonce', 'device_proof']);
        const deviceId = requireUuid(body.device_id, 'device_id');
        const serverId = requireUuid(body.server_id, 'server_id');
        const clientNonce = requireString(body.client_nonce, 'client_nonce', { min: 32, max: 256 });
        const proof = requireString(body.device_proof, 'device_proof', { min: 32, max: 1024 });
        if (deviceId !== principal.deviceId) {
          throw new ApiError(403, 'device_mismatch', 'Profile can only be issued to the authenticated device.');
        }
        const service = requireOwnedService(repository, principal.userId, params.serviceId);
        const now = clock();
        if (service.status !== 'active') throw new ApiError(403, 'service_inactive', 'Service is not active.');
        if (service.expires_at && Date.parse(service.expires_at) <= now.getTime()) {
          throw new ApiError(403, 'service_expired', 'Service has expired.');
        }
        if (service.traffic_limit_bytes !== null && service.traffic_used_bytes >= service.traffic_limit_bytes) {
          throw new ApiError(403, 'traffic_exhausted', 'Service traffic allowance is exhausted.');
        }
        const server = repository.findServer(serverId);
        if (!server || server.status !== 'active') throw new ApiError(403, 'server_unavailable', 'Server is unavailable.');
        if (TIER_RANK[server.tier] > TIER_RANK[service.tier]) throw new ApiError(403, 'tier_not_entitled', 'Service tier cannot use this server.');
        if (service.country_code && service.country_code !== server.country_code) throw new ApiError(403, 'country_not_entitled', 'Service cannot use this country.');
        const protocol = server.protocols.find((item) => service.allowed_protocols.includes(item));
        if (!protocol) throw new ApiError(403, 'protocol_not_entitled', 'Service has no allowed protocol on this server.');
        const proofPayload = { serviceId: service.id, deviceId, serverId, clientNonce };
        if (!await auth.verifyDeviceProof({ principal, payload: proofPayload, proof })) {
          throw new ApiError(403, 'invalid_device_proof', 'Device proof is invalid.');
        }
        const profileId = randomUUID();
        const expiresAt = new Date(now.getTime() + 5 * 60_000).toISOString();
        const associatedData = { profileId, userId: principal.userId, serviceId: service.id, deviceId, serverId, expiresAt };
        const sealed = await auth.sealConnectionProfile({
          principal,
          associatedData,
          plaintext: {
            endpoint: server.connection.endpoint,
            port: server.connection.port,
            protocol,
            credential: server.connection.credential,
            service_id: service.id,
            server_id: server.id,
            device_id: deviceId,
            expires_at: expiresAt,
          },
        });
        await repository.transaction(() => {
          const current = requireOwnedService(repository, principal.userId, service.id);
          const currentServer = repository.findServer(serverId);
          const entitlementChanged = current.status !== 'active'
            || current.expires_at && Date.parse(current.expires_at) <= clock().getTime()
            || current.traffic_limit_bytes !== null && current.traffic_used_bytes >= current.traffic_limit_bytes
            || !currentServer
            || currentServer.status !== 'active'
            || TIER_RANK[currentServer.tier] > TIER_RANK[current.tier]
            || current.country_code && current.country_code !== currentServer.country_code
            || !currentServer.protocols.some((item) => current.allowed_protocols.includes(item));
          if (entitlementChanged) {
            throw new ApiError(403, 'entitlement_changed', 'Service entitlement changed during profile issuance.');
          }
          const alreadyBound = current.deviceIds.includes(deviceId);
          if (!alreadyBound && current.deviceIds.length >= current.device_limit) {
            throw new ApiError(403, 'device_limit_reached', 'Service device limit has been reached.');
          }
          if (!alreadyBound) repository.saveService({ ...current, deviceIds: [...current.deviceIds, deviceId] });
        });
        return {
          status: 201,
          body: success({
            profile_id: profileId,
            server_id: server.id,
            algorithm: sealed.algorithm,
            key_version: sealed.keyVersion,
            nonce: sealed.nonce,
            ciphertext: sealed.ciphertext,
            expires_at: expiresAt,
          }, requestId, clock),
        };
      }

      if (request.method === 'POST' && pathname === '/v1/orders') {
        const idempotencyKey = requireIdempotencyKey(request.headers);
        const body = await parseBody(request);
        rejectUnknown(body, ['plan_id', 'channel', 'service_id']);
        const planId = requireUuid(body.plan_id, 'plan_id');
        const channel = requireString(body.channel, 'channel', { min: 4, max: 16 });
        if (!CHANNELS.has(channel)) throw new ApiError(400, 'invalid_channel', 'channel is invalid.');
        const serviceId = body.service_id === undefined || body.service_id === null ? null : requireUuid(body.service_id, 'service_id');
        const fingerprint = digest({ planId, channel, serviceId });
        const order = await repository.transaction(() => {
          const replay = assertIdempotency(repository, { scope: 'order:create', userId: principal.userId, key: idempotencyKey, fingerprint });
          if (replay) return requireOwnedOrder(repository, principal.userId, replay.resourceId);
          const plan = repository.findPlan(planId);
          if (!plan || !plan.active || !plan.channels.includes(channel)) throw new ApiError(400, 'plan_unavailable', 'Plan is not available for this channel.');
          if (serviceId) requireOwnedService(repository, principal.userId, serviceId);
          const created = repository.createOrder({
            userId: principal.userId,
            planId,
            serviceId,
            channel,
            status: 'pending',
            total: plan.price,
            entitlementServiceId: null,
            createdAt: clock().toISOString(),
          });
          repository.saveIdempotency('order:create', principal.userId, idempotencyKey, { fingerprint, resourceId: created.id });
          return created;
        });
        return { status: 201, body: success(asOrder(order), requestId, clock) };
      }

      params = match(pathname, '/v1/orders/:orderId');
      if (request.method === 'GET' && params) {
        return { status: 200, body: success(asOrder(requireOwnedOrder(repository, principal.userId, params.orderId)), requestId, clock) };
      }

      if (request.method === 'POST' && pathname === '/v1/billing/play/verify') {
        const idempotencyKey = requireIdempotencyKey(request.headers);
        const body = await parseBody(request);
        rejectUnknown(body, ['order_id', 'product_id', 'purchase_token']);
        const orderId = requireUuid(body.order_id, 'order_id');
        const productId = requireString(body.product_id, 'product_id', { min: 1, max: 255 });
        const purchaseToken = requireString(body.purchase_token, 'purchase_token', { min: 16, max: 4096 });
        const fingerprint = digest({ orderId, productId, purchaseTokenDigest: digest(purchaseToken) });
        const replay = assertIdempotency(repository, { scope: 'play:verify', userId: principal.userId, key: idempotencyKey, fingerprint });
        if (replay) {
          return { status: 200, body: success(asOrder(requireOwnedOrder(repository, principal.userId, replay.resourceId)), requestId, clock) };
        }
        let order = requireOwnedOrder(repository, principal.userId, orderId);
        if (order.channel !== 'play') throw new ApiError(409, 'wrong_payment_channel', 'Order is not a Google Play order.');
        if (!['pending', 'authorized', 'paid', 'fulfilled'].includes(order.status)) {
          throw new ApiError(409, 'order_not_verifiable', 'Order state does not allow purchase verification.');
        }
        const plan = repository.findPlan(order.planId);
        if (!plan || plan.play_product_id !== productId) throw new ApiError(400, 'product_mismatch', 'Product does not match the order plan.');
        const tokenDigest = digest(purchaseToken);
        let verification = null;
        if (order.status !== 'fulfilled') {
          verification = await purchaseVerifier.verifyPlayPurchase({
            packageName: 'com.ganj.vpn',
            productId,
            purchaseToken,
            orderId: order.id,
            userId: principal.userId,
          });
          if (!verification?.valid
            || verification.productId && verification.productId !== productId
            || typeof verification.externalTransactionId !== 'string'
            || verification.externalTransactionId.length === 0) {
            throw new ApiError(400, 'purchase_not_verified', 'Google Play purchase could not be verified.');
          }
        }
        order = await repository.transaction(() => {
          const concurrentReplay = assertIdempotency(repository, { scope: 'play:verify', userId: principal.userId, key: idempotencyKey, fingerprint });
          if (concurrentReplay) return requireOwnedOrder(repository, principal.userId, concurrentReplay.resourceId);
          let current = requireOwnedOrder(repository, principal.userId, orderId);
          if (current.status === 'fulfilled') {
            if (current.purchaseTokenDigest !== tokenDigest) throw new ApiError(409, 'order_already_fulfilled', 'Order is already fulfilled by another purchase.');
          } else {
            if (!['pending', 'authorized', 'paid'].includes(current.status)) {
              throw new ApiError(409, 'order_not_verifiable', 'Order state changed during purchase verification.');
            }
            const tokenOwner = repository.findPurchaseTokenOwner(tokenDigest);
            if (tokenOwner && tokenOwner !== current.id) throw new ApiError(409, 'purchase_token_reused', 'Purchase token was already used for another order.');
            repository.bindPurchaseToken(tokenDigest, current.id);
            current = fulfillOrder(repository, {
              ...current,
              externalTransactionId: verification.externalTransactionId,
              purchaseTokenDigest: tokenDigest,
            }, plan, clock());
          }
          repository.saveIdempotency('play:verify', principal.userId, idempotencyKey, { fingerprint, resourceId: current.id });
          return current;
        });
        return { status: 200, body: success(asOrder(order), requestId, clock) };
      }

      throw new ApiError(404, 'route_not_found', 'Route was not found.');
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
