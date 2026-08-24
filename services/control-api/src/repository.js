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
  consents: {
    analytics: '60000000-0000-4000-8000-000000000001',
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
        connection: {
          endpoint: 'nl-free.internal.invalid', port: 443, protocol: 'vless',
          credential: '60000000-0000-4000-8000-000000000001', flow: 'xtls-rprx-vision',
          transport: { type: 'tcp' }, security: { type: 'tls', server_name: 'nl-free.internal.invalid', fingerprint: 'chrome' },
        },
      },
      {
        id: FIXTURES.servers.premium, code: 'de-premium-01', name: 'Germany Premium 01', country_code: 'DE', city: 'Frankfurt',
        tier: 'premium', status: 'active', load_ratio: 0.22, latency_hint_ms: 61, protocols: ['vless', 'trojan'],
        connection: {
          endpoint: 'de-premium.internal.invalid', port: 443, protocol: 'vless',
          credential: '60000000-0000-4000-8000-000000000002', flow: 'xtls-rprx-vision',
          transport: { type: 'tcp' }, security: { type: 'tls', server_name: 'de-premium.internal.invalid', fingerprint: 'chrome' },
        },
      },
      {
        id: FIXTURES.servers.vip, code: 'ch-vip-01', name: 'Switzerland VIP 01', country_code: 'CH', city: 'Zurich',
        tier: 'vip', status: 'active', load_ratio: 0.12, latency_hint_ms: 73, protocols: ['vless', 'trojan'],
        connection: {
          endpoint: 'ch-vip.internal.invalid', port: 443, protocol: 'trojan', credential: 'server-side-vip-secret',
          transport: { type: 'tcp' }, security: { type: 'tls', server_name: 'ch-vip.internal.invalid', fingerprint: 'chrome' },
        },
      },
      {
        id: FIXTURES.servers.maintenance, code: 'de-premium-02', name: 'Germany Premium 02', country_code: 'DE', city: 'Frankfurt',
        tier: 'premium', status: 'maintenance', load_ratio: 0, latency_hint_ms: null, protocols: ['vless'],
        connection: {
          endpoint: 'offline.internal.invalid', port: 443, protocol: 'vless',
          credential: '60000000-0000-4000-8000-000000000003', flow: 'xtls-rprx-vision',
          transport: { type: 'tcp' }, security: { type: 'tls', server_name: 'offline.internal.invalid', fingerprint: 'chrome' },
        },
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
    this.consents = new Map([[FIXTURES.consents.analytics, {
      id: FIXTURES.consents.analytics,
      userId: FIXTURES.users.primary,
      purpose: 'product_analytics',
      status: 'granted',
      policyVersion: '2026-08',
      grantedAt: new Date().toISOString(),
      revokedAt: null,
    }]]);
    this.remoteConfigReleases = new Map();
    this.featureFlags = new Map();
    this.analyticsBatches = new Map();
    this.analyticsEvents = new Map();
    this.bugReports = new Map();
    this.supportTickets = new Map();
    this.diagnosticReports = new Map();
    this.adminAuditLog = [];
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
      consents: new Map([...this.consents].map(([key, value]) => [key, copy(value)])),
      remoteConfigReleases: new Map([...this.remoteConfigReleases].map(([key, value]) => [key, copy(value)])),
      featureFlags: new Map([...this.featureFlags].map(([key, value]) => [key, copy(value)])),
      analyticsBatches: new Map([...this.analyticsBatches].map(([key, value]) => [key, copy(value)])),
      analyticsEvents: new Map([...this.analyticsEvents].map(([key, value]) => [key, copy(value)])),
      bugReports: new Map([...this.bugReports].map(([key, value]) => [key, copy(value)])),
      supportTickets: new Map([...this.supportTickets].map(([key, value]) => [key, copy(value)])),
      diagnosticReports: new Map([...this.diagnosticReports].map(([key, value]) => [key, copy(value)])),
      adminAuditLog: copy(this.adminAuditLog),
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
      this.consents = snapshot.consents;
      this.remoteConfigReleases = snapshot.remoteConfigReleases;
      this.featureFlags = snapshot.featureFlags;
      this.analyticsBatches = snapshot.analyticsBatches;
      this.analyticsEvents = snapshot.analyticsEvents;
      this.bugReports = snapshot.bugReports;
      this.supportTickets = snapshot.supportTickets;
      this.diagnosticReports = snapshot.diagnosticReports;
      this.adminAuditLog = snapshot.adminAuditLog;
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

  ownsDevice(userId, deviceId) {
    return [...this.services.values()].some((item) => item.userId === userId && item.deviceIds.includes(deviceId))
      || (userId === FIXTURES.users.primary && deviceId === FIXTURES.devices.second)
      || (userId === FIXTURES.users.secondary && deviceId === FIXTURES.devices.secondary);
  }

  findConsent(userId, receiptId, purpose) {
    const consent = this.consents.get(receiptId);
    return consent?.userId === userId && consent.purpose === purpose ? copy(consent) : null;
  }

  saveConsent(value) {
    if (value.status === 'revoked') {
      for (const [id, consent] of this.consents) {
        if (consent.userId === value.userId && consent.purpose === value.purpose && consent.status === 'granted') {
          this.consents.set(id, { ...consent, status: 'revoked', revokedAt: value.revokedAt });
        }
      }
    }
    const stored = { ...copy(value), id: value.id ?? randomUUID() };
    this.consents.set(stored.id, stored);
    return copy(stored);
  }

  listConsents(userId) {
    const latest = new Map();
    for (const item of this.consents.values()) if (item.userId === userId) latest.set(item.purpose, item);
    return [...latest.values()].map(copy);
  }

  getPublishedRuntimeConfiguration(environment) {
    const release = [...this.remoteConfigReleases.values()]
      .find((item) => item.environment === environment && item.status === 'published');
    return {
      release: release ? copy(release) : null,
      flags: [...this.featureFlags.values()].map(copy),
    };
  }

  createRemoteConfigRelease(value) {
    const duplicate = [...this.remoteConfigReleases.values()]
      .some((item) => item.environment === value.environment && item.version === value.version);
    if (duplicate) throw new ApiError(409, 'config_version_exists', 'Remote configuration version already exists.');
    const stored = { ...copy(value), id: value.id ?? randomUUID(), status: 'draft', publishedAt: null, publishedBy: null };
    this.remoteConfigReleases.set(stored.id, stored);
    return copy(stored);
  }

  publishRemoteConfigRelease({ releaseId, publisher, publishedAt }) {
    const release = this.remoteConfigReleases.get(releaseId);
    if (!release) return null;
    if (release.status !== 'draft') throw new ApiError(409, 'config_not_publishable', 'Only a draft release can be published.');
    if (release.environment === 'production' && release.createdBy === publisher) {
      throw new ApiError(409, 'dual_control_required', 'A different administrator must publish a production release.');
    }
    for (const [id, item] of this.remoteConfigReleases) {
      if (item.environment === release.environment && item.status === 'published') {
        this.remoteConfigReleases.set(id, { ...item, status: 'retired' });
      }
    }
    const published = { ...release, status: 'published', publishedBy: publisher, publishedAt };
    this.remoteConfigReleases.set(releaseId, published);
    return copy(published);
  }

  upsertFeatureFlag(value) {
    const versions = this.featureFlags.get(value.flagKey) ?? [];
    const stored = { ...copy(value), id: value.id ?? randomUUID(), version: (versions.at(-1)?.version ?? 0) + 1 };
    this.featureFlags.set(value.flagKey, [...versions, stored]);
    return copy(stored);
  }

  listCurrentFeatureFlags() {
    return [...this.featureFlags.values()].map((items) => copy(items.at(-1)));
  }

  ingestAnalyticsBatch(value) {
    const consent = this.consents.get(value.consentReceiptId);
    if (!consent || consent.userId !== value.userId || consent.purpose !== 'product_analytics'
      || consent.status !== 'granted' || consent.revokedAt) {
      throw new ApiError(403, 'analytics_consent_required', 'A current analytics consent receipt is required.');
    }
    const key = `${value.userId}:${value.batchId}`;
    const existing = this.analyticsBatches.get(key);
    if (existing) {
      if (existing.payloadDigest !== value.payloadDigest) {
        throw new ApiError(409, 'analytics_batch_conflict', 'Batch ID was already used with different content.');
      }
      return { replay: true, acceptedCount: 0, duplicateCount: value.events.length };
    }
    this.analyticsBatches.set(key, copy(value));
    let acceptedCount = 0;
    let duplicateCount = 0;
    for (const event of value.events) {
      const eventKey = `${value.userId}:${event.event_id}`;
      if (this.analyticsEvents.has(eventKey)) duplicateCount += 1;
      else {
        this.analyticsEvents.set(eventKey, copy({ ...event, userId: value.userId, batchId: value.batchId }));
        acceptedCount += 1;
      }
    }
    return { replay: false, acceptedCount, duplicateCount };
  }

  createBugReport(value) {
    const existing = [...this.bugReports.values()]
      .find((item) => item.userId === value.userId && item.clientReportId === value.clientReportId);
    if (existing) {
      if (existing.payloadDigest !== value.payloadDigest) throw new ApiError(409, 'bug_report_conflict', 'Client report ID was reused with different content.');
      return { replay: true, report: copy(existing) };
    }
    const report = { ...copy(value), id: value.id ?? randomUUID() };
    this.bugReports.set(report.id, report);
    return { replay: false, report: copy(report) };
  }

  listBugReports(userId) {
    return [...this.bugReports.values()].filter((item) => item.userId === userId).map(copy);
  }

  findOwnedBugReport(userId, id) {
    const item = this.bugReports.get(id);
    return item?.userId === userId ? copy(item) : null;
  }

  createSupportTicket(value) {
    const existing = [...this.supportTickets.values()]
      .find((item) => item.userId === value.userId && item.clientTicketId === value.clientTicketId);
    if (existing) {
      if (existing.payloadDigest !== value.payloadDigest) throw new ApiError(409, 'support_ticket_conflict', 'Client ticket ID was reused with different content.');
      return { replay: true, ticket: copy(existing) };
    }
    const ticket = { ...copy(value), id: value.id ?? randomUUID() };
    this.supportTickets.set(ticket.id, ticket);
    return { replay: false, ticket: copy(ticket) };
  }

  listSupportTickets(userId) {
    return [...this.supportTickets.values()].filter((item) => item.userId === userId).map(copy);
  }

  findOwnedSupportTicket(userId, id) {
    const item = this.supportTickets.get(id);
    return item?.userId === userId ? copy(item) : null;
  }

  createDiagnosticReport(value) {
    const existing = [...this.diagnosticReports.values()]
      .find((item) => item.userId === value.userId && item.clientReportId === value.clientReportId);
    if (existing) {
      if (existing.payloadDigest !== value.payloadDigest) throw new ApiError(409, 'diagnostic_report_conflict', 'Client report ID was reused with different content.');
      return { replay: true, report: copy(existing) };
    }
    const report = { ...copy(value), id: value.id ?? randomUUID() };
    this.diagnosticReports.set(report.id, report);
    return { replay: false, report: copy(report) };
  }

  appendAdminAudit(value) {
    const event = { ...copy(value), id: value.id ?? randomUUID() };
    this.adminAuditLog.push(event);
    return copy(event);
  }

  listAdminAudit({ limit = 100 } = {}) {
    return this.adminAuditLog.slice(-limit).reverse().map(copy);
  }
}
