import { randomUUID } from 'node:crypto';
import { ApiError } from './errors.js';

export const FIXTURES = Object.freeze({
  users: {
    primary: '10000000-0000-4000-8000-000000000001',
    secondary: '10000000-0000-4000-8000-000000000002',
  },
  devices: {
    primary: '20000000-0000-4000-8000-000000000001',
    second: '20000000-0000-4000-8000-000000000002',
    secondary: '20000000-0000-4000-8000-000000000003',
  },
  plans: {
    free: '30000000-0000-4000-8000-000000000001',
    premium: '30000000-0000-4000-8000-000000000002',
    vip: '30000000-0000-4000-8000-000000000003',
  },
  services: {
    premium: '40000000-0000-4000-8000-000000000001',
    expired: '40000000-0000-4000-8000-000000000002',
    exhausted: '40000000-0000-4000-8000-000000000003',
  },
  servers: {
    free: '50000000-0000-4000-8000-000000000001',
    premium: '50000000-0000-4000-8000-000000000002',
    vip: '50000000-0000-4000-8000-000000000003',
    maintenance: '50000000-0000-4000-8000-000000000004',
  },
});

function copy(value) {
  return structuredClone(value);
}

export function createSeed(now = new Date()) {
  const future = new Date(now.getTime() + 30 * 86_400_000).toISOString();
  const past = new Date(now.getTime() - 86_400_000).toISOString();
  const gib = 1024 ** 3;
  return {
    plans: [
      {
        id: FIXTURES.plans.free, code: 'free-guest', name: 'Free', tier: 'free',
        duration_days: null, traffic_limit_bytes: 2 * gib, device_limit: 1,
        features: ['free_servers'], price: { amount_minor: 0, currency: 'IRR' },
        channels: ['direct'], play_product_id: null, active: true,
      },
      {
        id: FIXTURES.plans.premium, code: 'premium-30d', name: 'Premium 30 Days', tier: 'premium',
        duration_days: 30, traffic_limit_bytes: 100 * gib, device_limit: 2,
        features: ['smart_connect', 'premium_servers'], price: { amount_minor: 2_990_000, currency: 'IRR' },
        channels: ['play', 'direct', 'wallet'], play_product_id: 'ganj.premium.30d', active: true,
      },
      {
        id: FIXTURES.plans.vip, code: 'vip-90d', name: 'VIP 90 Days', tier: 'vip',
        duration_days: 90, traffic_limit_bytes: null, device_limit: 5,
        features: ['smart_connect', 'all_servers', 'priority_support'], price: { amount_minor: 7_490_000, currency: 'IRR' },
        channels: ['play', 'direct', 'wallet'], play_product_id: 'ganj.vip.90d', active: true,
      },
    ],
    services: [
      {
        id: FIXTURES.services.premium, userId: FIXTURES.users.primary, planId: FIXTURES.plans.premium,
        name: 'Germany Premium', status: 'active', tier: 'premium', country_code: null,
        traffic_limit_bytes: 100 * gib, traffic_used_bytes: 3 * gib, expires_at: future,
        device_limit: 2, allowed_protocols: ['vless', 'trojan'], deviceIds: [FIXTURES.devices.primary],
      },
      {
        id: FIXTURES.services.expired, userId: FIXTURES.users.secondary, planId: FIXTURES.plans.premium,
        name: 'Expired Premium', status: 'active', tier: 'premium', country_code: null,
        traffic_limit_bytes: 100 * gib, traffic_used_bytes: 0, expires_at: past,
        device_limit: 1, allowed_protocols: ['vless'], deviceIds: [FIXTURES.devices.secondary],
      },
      {
        id: FIXTURES.services.exhausted, userId: FIXTURES.users.primary, planId: FIXTURES.plans.free,
        name: 'Free Allowance', status: 'active', tier: 'free', country_code: null,
        traffic_limit_bytes: 2 * gib, traffic_used_bytes: 2 * gib, expires_at: future,
        device_limit: 1, allowed_protocols: ['vless'], deviceIds: [FIXTURES.devices.primary],
      },
    ],
    servers: [
      {
        id: FIXTURES.servers.free, code: 'nl-free-01', name: 'Netherlands Free 01', country_code: 'NL', city: 'Amsterdam',
        tier: 'free', status: 'active', load_ratio: 0.31, latency_hint_ms: 85, protocols: ['vless'],
        connection: { endpoint: 'nl-free.internal.invalid', port: 443, protocol: 'vless', credential: 'server-side-free-secret' },
      },
      {
        id: FIXTURES.servers.premium, code: 'de-premium-01', name: 'Germany Premium 01', country_code: 'DE', city: 'Frankfurt',
        tier: 'premium', status: 'active', load_ratio: 0.22, latency_hint_ms: 61, protocols: ['vless', 'trojan'],
        connection: { endpoint: 'de-premium.internal.invalid', port: 443, protocol: 'vless', credential: 'server-side-premium-secret' },
      },
      {
        id: FIXTURES.servers.vip, code: 'ch-vip-01', name: 'Switzerland VIP 01', country_code: 'CH', city: 'Zurich',
        tier: 'vip', status: 'active', load_ratio: 0.12, latency_hint_ms: 73, protocols: ['vless', 'trojan'],
        connection: { endpoint: 'ch-vip.internal.invalid', port: 443, protocol: 'trojan', credential: 'server-side-vip-secret' },
      },
      {
        id: FIXTURES.servers.maintenance, code: 'de-premium-02', name: 'Germany Premium 02', country_code: 'DE', city: 'Frankfurt',
        tier: 'premium', status: 'maintenance', load_ratio: 0, latency_hint_ms: null, protocols: ['vless'],
        connection: { endpoint: 'offline.internal.invalid', port: 443, protocol: 'vless', credential: 'offline-secret' },
      },
    ],
  };
}

export class InMemoryRepository {
  constructor(seed = createSeed()) {
    this.kind = 'test-only';
    this.plans = new Map(seed.plans.map((item) => [item.id, copy(item)]));
    this.services = new Map(seed.services.map((item) => [item.id, copy(item)]));
    this.servers = new Map(seed.servers.map((item) => [item.id, copy(item)]));
    this.orders = new Map();
    this.idempotency = new Map();
    this.purchaseTokens = new Map();
    this.profileGrants = new Map();
    this.webhookEvents = new Map();
    this.transactionTail = Promise.resolve();
  }

  async transaction(work) {
    let release;
    const previous = this.transactionTail;
    this.transactionTail = new Promise((resolve) => { release = resolve; });
    await previous;
    const snapshot = {
      services: new Map([...this.services].map(([key, value]) => [key, copy(value)])),
      orders: new Map([...this.orders].map(([key, value]) => [key, copy(value)])),
      idempotency: new Map([...this.idempotency].map(([key, value]) => [key, copy(value)])),
      purchaseTokens: new Map(this.purchaseTokens),
      profileGrants: new Map([...this.profileGrants].map(([key, value]) => [key, copy(value)])),
      webhookEvents: new Map([...this.webhookEvents].map(([key, value]) => [key, copy(value)])),
    };
    try {
      return await work();
    } catch (error) {
      this.services = snapshot.services;
      this.orders = snapshot.orders;
      this.idempotency = snapshot.idempotency;
      this.purchaseTokens = snapshot.purchaseTokens;
      this.profileGrants = snapshot.profileGrants;
      this.webhookEvents = snapshot.webhookEvents;
      throw error;
    } finally {
      release();
    }
  }

  listPlans(channel) {
    return [...this.plans.values()].filter((plan) => plan.active && plan.channels.includes(channel)).map(copy);
  }

  findPlan(id) { return this.plans.has(id) ? copy(this.plans.get(id)) : null; }

  listServices(userId) {
    return [...this.services.values()].filter((service) => service.userId === userId).map(copy);
  }

  findOwnedService(userId, id) {
    const service = this.services.get(id);
    return service?.userId === userId ? copy(service) : null;
  }

  saveService(service) {
    this.services.set(service.id, copy(service));
    return copy(service);
  }

  createService(service) {
    return this.saveService({ ...service, id: service.id ?? randomUUID() });
  }

  listServers() { return [...this.servers.values()].map(copy); }
  findServer(id) { return this.servers.has(id) ? copy(this.servers.get(id)) : null; }

  createOrder(order) {
    const stored = { ...copy(order), id: order.id ?? randomUUID() };
    this.orders.set(stored.id, stored);
    return copy(stored);
  }

  findOwnedOrder(userId, id) {
    const order = this.orders.get(id);
    return order?.userId === userId ? copy(order) : null;
  }

  saveOrder(order) {
    this.orders.set(order.id, copy(order));
    return copy(order);
  }

  findIdempotency(scope, userId, key) {
    return copy(this.idempotency.get(`${scope}:${userId}:${key}`) ?? null);
  }

  saveIdempotency(scope, userId, key, value) {
    this.idempotency.set(`${scope}:${userId}:${key}`, copy(value));
  }

  findPurchaseTokenOwner(digest) { return this.purchaseTokens.get(digest) ?? null; }
  bindPurchaseToken(digest, orderId) { this.purchaseTokens.set(digest, orderId); }

  reserveConnectionProfile(value) {
    const nonceKey = `${value.deviceId}:${value.clientNonceDigest}`;
    if ([...this.profileGrants.values()].some((grant) => grant.nonceKey === nonceKey)) return false;
    this.profileGrants.set(value.profileId, { ...copy(value), nonceKey, consumedAt: null });
    return true;
  }

  consumeConnectionProfile({ profileId, deviceId, now = new Date() }) {
    const grant = this.profileGrants.get(profileId);
    if (!grant || grant.deviceId !== deviceId || grant.consumedAt || Date.parse(grant.expiresAt) <= now.getTime()) return false;
    this.profileGrants.set(profileId, { ...grant, consumedAt: now.toISOString() });
    return true;
  }

  findOrderByPurchaseTokenDigest(tokenDigest) {
    return copy([...this.orders.values()].find((item) => item.purchaseTokenDigest === tokenDigest) ?? null);
  }

  recordWebhookEvent({ provider, eventId, payloadDigest, eventType, receivedAt }) {
    const key = `${provider}:${eventId}`;
    const existing = this.webhookEvents.get(key);
    if (existing) {
      if (existing.payloadDigest !== payloadDigest) {
        throw new ApiError(409, 'webhook_replay_conflict', 'Webhook event ID was reused with different content.');
      }
      return { replay: true, status: existing.status };
    }
    this.webhookEvents.set(key, { payloadDigest, eventType, receivedAt, status: 'received' });
    return { replay: false, status: 'received' };
  }

  completeWebhookEvent({ provider, eventId, status }) {
    const key = `${provider}:${eventId}`;
    const existing = this.webhookEvents.get(key);
    if (existing) this.webhookEvents.set(key, { ...existing, status });
  }
}
