import { randomUUID } from 'node:crypto';
import { ApiError } from './errors.js';

const FREE_PROTOCOLS = Object.freeze(['vless']);

function freeExpiry(plan, now) {
  if (plan.duration_days == null) return null;
  return new Date(now.getTime() + plan.duration_days * 86_400_000).toISOString();
}

/**
 * Adds the product-level Free entitlement invariant without mixing it into auth/session storage.
 *
 * The caller still needs a valid Ganj access token (normally created silently by /auth/guest).
 * No Telegram login is required. Connection profile issuance remains device-proof-bound and uses
 * the existing one-time encrypted profile path.
 */
export class FreeAccessRepository {
  constructor(delegate, { clock = () => new Date() } = {}) {
    if (!delegate || typeof delegate.transaction !== 'function' || typeof delegate.listPlans !== 'function'
      || typeof delegate.listServices !== 'function' || typeof delegate.createService !== 'function') {
      throw new Error('Free access repository delegate is incomplete.');
    }
    this.delegate = delegate;
    this.clock = clock;

    return new Proxy(this, {
      get: (target, property, receiver) => {
        if (Reflect.has(target, property)) {
          const value = Reflect.get(target, property, receiver);
          return typeof value === 'function' ? value.bind(target) : value;
        }
        const value = delegate[property];
        return typeof value === 'function' ? value.bind(delegate) : value;
      },
    });
  }

  get kind() { return this.delegate.kind; }

  async ensureFreeAccess(userId) {
    return this.delegate.transaction(async () => {
      const current = await this.delegate.listServices(userId);
      const existing = current.find((service) => service.tier === 'free');
      if (existing) return existing;

      const plans = await this.delegate.listPlans('direct');
      const plan = plans.find((candidate) => candidate.active !== false && candidate.tier === 'free');
      if (!plan) {
        throw new ApiError(503, 'free_plan_unavailable', 'Free access is temporarily unavailable.', { retryable: true });
      }

      const now = this.clock();
      return this.delegate.createService({
        id: randomUUID(),
        userId,
        planId: plan.id,
        name: plan.name,
        status: 'active',
        tier: 'free',
        country_code: null,
        traffic_limit_bytes: plan.traffic_limit_bytes,
        traffic_used_bytes: 0,
        expires_at: freeExpiry(plan, now),
        device_limit: 1,
        allowed_protocols: [...FREE_PROTOCOLS],
        deviceIds: [],
      });
    });
  }

  async listServices(userId) {
    await this.ensureFreeAccess(userId);
    return this.delegate.listServices(userId);
  }
}

export function withFreeAccess(repository, options) {
  return repository instanceof FreeAccessRepository ? repository : new FreeAccessRepository(repository, options);
}
