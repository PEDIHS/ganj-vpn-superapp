import { AsyncLocalStorage } from 'node:async_hooks';
import { ApiError } from './errors.js';

export const DEFAULT_FREE_POLICY = Object.freeze({
  enabled: true,
  maintenance: false,
  emergencyDisabled: false,
  issueWindowSeconds: 3600,
  maxIssuesPerUser: 60,
  maxIssuesPerDevice: 30,
  maxActiveGrantsPerUser: 4,
  maxActiveGrantsPerDevice: 2,
  maxActiveGrantsPerServer: 500,
});

const POLICY_KEYS = new Set(Object.keys(DEFAULT_FREE_POLICY));
const INTEGER_LIMITS = Object.freeze({
  issueWindowSeconds: [60, 86_400],
  maxIssuesPerUser: [1, 10_000],
  maxIssuesPerDevice: [1, 10_000],
  maxActiveGrantsPerUser: [1, 1_000],
  maxActiveGrantsPerDevice: [1, 1_000],
  maxActiveGrantsPerServer: [1, 100_000],
});

function validatePolicy(value, base = DEFAULT_FREE_POLICY) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(400, 'invalid_free_policy', 'Free access policy must be an object.');
  }
  const unknown = Object.keys(value).filter((key) => !POLICY_KEYS.has(key));
  if (unknown.length) {
    throw new ApiError(400, 'invalid_free_policy', 'Free access policy contains unsupported fields.', {
      details: { fields: unknown },
    });
  }
  const policy = { ...base, ...value };
  for (const key of ['enabled', 'maintenance', 'emergencyDisabled']) {
    if (typeof policy[key] !== 'boolean') {
      throw new ApiError(400, 'invalid_free_policy', `${key} must be boolean.`);
    }
  }
  for (const [key, [minimum, maximum]] of Object.entries(INTEGER_LIMITS)) {
    if (!Number.isSafeInteger(policy[key]) || policy[key] < minimum || policy[key] > maximum) {
      throw new ApiError(400, 'invalid_free_policy', `${key} is outside the supported range.`);
    }
  }
  return policy;
}

function unavailable(policy) {
  if (policy.emergencyDisabled) {
    return new ApiError(503, 'free_access_emergency_disabled', 'Free access is temporarily disabled by emergency policy.', { retryable: true });
  }
  if (policy.maintenance) {
    return new ApiError(503, 'free_access_maintenance', 'Free access is temporarily under maintenance.', { retryable: true });
  }
  if (!policy.enabled) {
    return new ApiError(403, 'free_access_disabled', 'Free access is disabled.');
  }
  return null;
}

function capacityError(code, dimension, retryable = true) {
  return new ApiError(code === 'free_server_capacity_reached' ? 503 : 429, code,
    code === 'free_rate_limit_exceeded'
      ? 'Free profile issuance rate limit was reached.'
      : code === 'free_server_capacity_reached'
        ? 'Free server admission capacity was reached.'
        : 'Free profile admission capacity was reached.', {
      retryable,
      details: { dimension },
    });
}

function assertCounts(policy, counts) {
  if (counts.issuesUser >= policy.maxIssuesPerUser) throw capacityError('free_rate_limit_exceeded', 'user');
  if (counts.issuesDevice >= policy.maxIssuesPerDevice) throw capacityError('free_rate_limit_exceeded', 'device');
  if (counts.activeUser >= policy.maxActiveGrantsPerUser) throw capacityError('free_profile_capacity_reached', 'user');
  if (counts.activeDevice >= policy.maxActiveGrantsPerDevice) throw capacityError('free_profile_capacity_reached', 'device');
  if (counts.activeServer >= policy.maxActiveGrantsPerServer) throw capacityError('free_server_capacity_reached', 'server');
}

function policyFromRow(row) {
  return {
    enabled: row.enabled,
    maintenance: row.maintenance,
    emergencyDisabled: row.emergency_disabled,
    issueWindowSeconds: Number(row.issue_window_seconds),
    maxIssuesPerUser: Number(row.max_issues_per_user),
    maxIssuesPerDevice: Number(row.max_issues_per_device),
    maxActiveGrantsPerUser: Number(row.max_active_grants_per_user),
    maxActiveGrantsPerDevice: Number(row.max_active_grants_per_device),
    maxActiveGrantsPerServer: Number(row.max_active_grants_per_server),
    updatedBy: row.updated_by ?? null,
    updatedAt: row.updated_at?.toISOString?.() ?? row.updated_at ?? null,
  };
}

export class InMemoryFreePolicyStore {
  constructor(repository, { policy = DEFAULT_FREE_POLICY, clock = () => new Date() } = {}) {
    if (!(repository?.profileGrants instanceof Map)) throw new Error('In-memory free policy requires the test profile grant store.');
    this.repository = repository;
    this.policy = validatePolicy(policy);
    this.clock = clock;
    this.updatedBy = null;
    this.updatedAt = this.clock().toISOString();
  }

  async read() {
    return { ...this.policy, updatedBy: this.updatedBy, updatedAt: this.updatedAt };
  }

  async update(patch, { actorId = null } = {}) {
    this.policy = validatePolicy(patch, this.policy);
    this.updatedBy = actorId;
    this.updatedAt = this.clock().toISOString();
    return this.read();
  }

  async enforceAdmission({ userId, deviceId, serverId, now, policy }) {
    const windowStart = now.getTime() - policy.issueWindowSeconds * 1_000;
    const grants = [...this.repository.profileGrants.values()];
    const isRecent = (grant) => {
      const created = Date.parse(grant.createdAt ?? grant.created_at ?? '');
      return Number.isFinite(created) && created >= windowStart;
    };
    const isActive = (grant) => !grant.consumedAt && !grant.consumed_at
      && Date.parse(grant.expiresAt ?? grant.expires_at ?? '') > now.getTime();
    assertCounts(policy, {
      issuesUser: grants.filter((grant) => grant.userId === userId && isRecent(grant)).length,
      issuesDevice: grants.filter((grant) => grant.deviceId === deviceId && isRecent(grant)).length,
      activeUser: grants.filter((grant) => grant.userId === userId && isActive(grant)).length,
      activeDevice: grants.filter((grant) => grant.deviceId === deviceId && isActive(grant)).length,
      activeServer: grants.filter((grant) => grant.serverId === serverId && isActive(grant)).length,
    });
  }
}

export class PostgresFreePolicyStore {
  constructor(repository) {
    if (!repository || typeof repository.database !== 'function' || typeof repository.advisoryLock !== 'function') {
      throw new Error('PostgreSQL free policy requires database() and advisoryLock().');
    }
    this.repository = repository;
  }

  async read() {
    const result = await this.repository.database().query(
      "SELECT * FROM control_free_access_policy WHERE policy_key = 'default'",
    );
    if (result.rowCount !== 1) throw new Error('Default free access policy is missing.');
    return policyFromRow(result.rows[0]);
  }

  async update(patch, { actorId = null } = {}) {
    const current = await this.read();
    const policy = validatePolicy(patch, current);
    const result = await this.repository.database().query(
      `UPDATE control_free_access_policy SET
         enabled = $1, maintenance = $2, emergency_disabled = $3,
         issue_window_seconds = $4, max_issues_per_user = $5, max_issues_per_device = $6,
         max_active_grants_per_user = $7, max_active_grants_per_device = $8,
         max_active_grants_per_server = $9, updated_by = $10, updated_at = now()
       WHERE policy_key = 'default' RETURNING *`,
      [policy.enabled, policy.maintenance, policy.emergencyDisabled, policy.issueWindowSeconds,
        policy.maxIssuesPerUser, policy.maxIssuesPerDevice, policy.maxActiveGrantsPerUser,
        policy.maxActiveGrantsPerDevice, policy.maxActiveGrantsPerServer, actorId],
    );
    return policyFromRow(result.rows[0]);
  }

  async enforceAdmission({ userId, deviceId, serverId, now, policy }) {
    await this.repository.advisoryLock('free-admission-user', userId);
    await this.repository.advisoryLock('free-admission-device', deviceId);
    await this.repository.advisoryLock('free-admission-server', serverId);
    const result = await this.repository.database().query(
      `SELECT
         count(*) FILTER (WHERE user_id = $1 AND created_at >= $4 - ($5::integer * interval '1 second')) AS issues_user,
         count(*) FILTER (WHERE device_id = $2 AND created_at >= $4 - ($5::integer * interval '1 second')) AS issues_device,
         count(*) FILTER (WHERE user_id = $1 AND consumed_at IS NULL AND expires_at > $4) AS active_user,
         count(*) FILTER (WHERE device_id = $2 AND consumed_at IS NULL AND expires_at > $4) AS active_device,
         count(*) FILTER (WHERE server_id = $3 AND consumed_at IS NULL AND expires_at > $4) AS active_server
       FROM control_connection_profile_grants
       WHERE user_id = $1 OR device_id = $2 OR server_id = $3`,
      [userId, deviceId, serverId, now, policy.issueWindowSeconds],
    );
    const row = result.rows[0];
    assertCounts(policy, {
      issuesUser: Number(row.issues_user),
      issuesDevice: Number(row.issues_device),
      activeUser: Number(row.active_user),
      activeDevice: Number(row.active_device),
      activeServer: Number(row.active_server),
    });
  }
}

function createStore(repository, options) {
  if (options.store) return options.store;
  if (repository.kind === 'test-only') return new InMemoryFreePolicyStore(repository, options);
  if (repository.kind === 'postgres-v1') return new PostgresFreePolicyStore(repository);
  throw new Error('A production FreeAccessPolicy store is required for this data adapter.');
}

/**
 * Central Free Tier admission policy. It intentionally counts short-lived profile grants, not live
 * VPN tunnels. Exact active-tunnel concurrency requires a separate authenticated runtime lifecycle
 * signal and must not be inferred from profile issuance alone.
 */
export class FreePolicyRepository {
  constructor(delegate, options = {}) {
    if (!delegate || typeof delegate.transaction !== 'function' || typeof delegate.listServers !== 'function'
      || typeof delegate.findServer !== 'function' || typeof delegate.findOwnedService !== 'function'
      || typeof delegate.reserveConnectionProfile !== 'function') {
      throw new Error('Free policy repository delegate is incomplete.');
    }
    this.delegate = delegate;
    this.clock = options.clock ?? (() => new Date());
    this.store = createStore(delegate, { ...options, clock: this.clock });
    this.transactionContext = new AsyncLocalStorage();
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

  async transaction(work) {
    if (this.transactionContext.getStore()) return work();
    return this.delegate.transaction(() => this.transactionContext.run(true, work));
  }

  async readFreePolicy() {
    try {
      return await this.store.read();
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(503, 'free_policy_unavailable', 'Free access policy is temporarily unavailable.', { retryable: true });
    }
  }

  async updateFreePolicy(patch, options = {}) {
    const update = async () => this.store.update(patch, options);
    if (this.transactionContext.getStore()) return update();
    return this.transaction(update);
  }

  async listServers() {
    const servers = await this.delegate.listServers();
    try {
      const policy = await this.readFreePolicy();
      return unavailable(policy) ? servers.filter((server) => server.tier !== 'free') : servers;
    } catch (error) {
      if (error?.code === 'free_policy_unavailable') return servers.filter((server) => server.tier !== 'free');
      throw error;
    }
  }

  async findServer(id) {
    const metadata = (await this.delegate.listServers()).find((server) => server.id === id);
    if (metadata?.tier === 'free') {
      const policy = await this.readFreePolicy();
      const blocked = unavailable(policy);
      if (blocked) throw blocked;
    }
    return this.delegate.findServer(id);
  }

  async reserveConnectionProfile(value) {
    const operation = async () => {
      const service = await this.delegate.findOwnedService(value.userId, value.serviceId);
      if (service?.tier !== 'free') return this.delegate.reserveConnectionProfile(value);
      const now = this.clock();
      const policy = await this.readFreePolicy();
      const blocked = unavailable(policy);
      if (blocked) throw blocked;
      await this.store.enforceAdmission({
        userId: value.userId,
        deviceId: value.deviceId,
        serverId: value.serverId,
        now,
        policy,
      });
      return this.delegate.reserveConnectionProfile({
        ...value,
        createdAt: value.createdAt ?? now.toISOString(),
      });
    };
    if (this.transactionContext.getStore()) return operation();
    return this.transaction(operation);
  }
}

export function withFreePolicy(repository, options = {}) {
  return repository instanceof FreePolicyRepository ? repository : new FreePolicyRepository(repository, options);
}
