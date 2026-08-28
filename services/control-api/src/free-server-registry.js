import { AsyncLocalStorage } from 'node:async_hooks';
import { randomUUID } from 'node:crypto';
import { ApiError } from './errors.js';

function copy(value) { return structuredClone(value); }

function defaultControl(server) {
  return {
    id: server.id,
    code: server.code,
    name: server.name,
    countryCode: server.country_code,
    city: server.city ?? null,
    status: server.status,
    loadRatio: server.load_ratio,
    latencyHintMs: server.latency_hint_ms ?? null,
    protocols: [...server.protocols],
    priority: server.priority ?? 100,
    providerRef: server.providerRef ?? server.provider_ref ?? null,
    upstreamRef: server.upstreamRef ?? server.upstream_ref ?? null,
    maxLoadRatio: server.maxLoadRatio ?? server.max_load_ratio ?? 1,
    maxActiveProfileGrants: server.maxActiveProfileGrants ?? server.max_active_profile_grants ?? null,
    emergencyDisabled: server.emergencyDisabled ?? server.emergency_disabled ?? false,
    rolloutBasisPoints: server.rolloutBasisPoints ?? server.rollout_basis_points ?? 10_000,
    minAppVersion: server.minAppVersion ?? server.min_app_version ?? null,
    secretRef: server.secretRef ?? server.secret_ref ?? null,
    updatedAt: server.updatedAt ?? server.updated_at ?? null,
  };
}

function fromRow(row) {
  return defaultControl({
    ...row,
    country_code: row.country_code.trim(),
    load_ratio: Number(row.load_ratio),
    max_load_ratio: Number(row.max_load_ratio),
    max_active_profile_grants: row.max_active_profile_grants == null ? null : Number(row.max_active_profile_grants),
    priority: Number(row.priority),
    rollout_basis_points: Number(row.rollout_basis_points),
    updated_at: row.updated_at?.toISOString?.() ?? row.updated_at ?? null,
  });
}

function unavailable(control) {
  if (control.emergencyDisabled) {
    return new ApiError(503, 'free_server_emergency_disabled', 'Free server is disabled by emergency policy.', { retryable: true });
  }
  if (control.status !== 'active') {
    return new ApiError(403, 'free_server_unavailable', 'Free server is not accepting connections.');
  }
  if (control.loadRatio > control.maxLoadRatio) {
    return new ApiError(503, 'free_server_overloaded', 'Free server is above its admission load threshold.', { retryable: true });
  }
  return null;
}

export class InMemoryFreeServerRegistryStore {
  constructor(repository) {
    if (!(repository?.servers instanceof Map) || !(repository?.profileGrants instanceof Map)) {
      throw new Error('In-memory FreeServerRegistry requires test server and profile-grant stores.');
    }
    this.repository = repository;
  }

  async list() {
    return [...this.repository.servers.values()].filter((server) => server.tier === 'free').map(defaultControl).map(copy);
  }

  async find(id) {
    const server = this.repository.servers.get(id);
    return server?.tier === 'free' ? copy(defaultControl(server)) : null;
  }

  async create(value) {
    if (this.repository.servers.has(value.id) || [...this.repository.servers.values()].some((server) => server.code === value.code)) {
      throw new ApiError(409, 'free_server_conflict', 'Free server ID or code already exists.');
    }
    this.repository.servers.set(value.id, {
      id: value.id,
      code: value.code,
      name: value.name,
      country_code: value.countryCode,
      city: value.city,
      tier: 'free',
      status: value.status,
      load_ratio: value.loadRatio,
      latency_hint_ms: value.latencyHintMs,
      protocols: [...value.protocols],
      secretRef: value.secretRef,
      priority: value.priority,
      providerRef: value.providerRef,
      upstreamRef: value.upstreamRef,
      maxLoadRatio: value.maxLoadRatio,
      maxActiveProfileGrants: value.maxActiveProfileGrants,
      emergencyDisabled: value.emergencyDisabled,
      rolloutBasisPoints: value.rolloutBasisPoints,
      minAppVersion: value.minAppVersion,
      updatedAt: new Date().toISOString(),
    });
    return this.find(value.id);
  }

  async update(id, patch) {
    const current = this.repository.servers.get(id);
    if (!current || current.tier !== 'free') return null;
    if (patch.code && [...this.repository.servers.values()].some((server) => server.id !== id && server.code === patch.code)) {
      throw new ApiError(409, 'free_server_conflict', 'Free server code already exists.');
    }
    const control = { ...defaultControl(current), ...patch, id };
    this.repository.servers.set(id, {
      ...current,
      code: control.code,
      name: control.name,
      country_code: control.countryCode,
      city: control.city,
      status: control.status,
      load_ratio: control.loadRatio,
      latency_hint_ms: control.latencyHintMs,
      protocols: [...control.protocols],
      secretRef: control.secretRef,
      priority: control.priority,
      providerRef: control.providerRef,
      upstreamRef: control.upstreamRef,
      maxLoadRatio: control.maxLoadRatio,
      maxActiveProfileGrants: control.maxActiveProfileGrants,
      emergencyDisabled: control.emergencyDisabled,
      rolloutBasisPoints: control.rolloutBasisPoints,
      minAppVersion: control.minAppVersion,
      updatedAt: new Date().toISOString(),
    });
    return this.find(id);
  }

  async enforceServerAdmission({ serverId, now, control }) {
    if (control.maxActiveProfileGrants == null) return;
    const active = [...this.repository.profileGrants.values()].filter((grant) =>
      grant.serverId === serverId
      && !grant.consumedAt && !grant.consumed_at
      && Date.parse(grant.expiresAt ?? grant.expires_at ?? '') > now.getTime()).length;
    if (active >= control.maxActiveProfileGrants) {
      throw new ApiError(503, 'free_server_capacity_reached', 'Free server admission capacity was reached.', {
        retryable: true, details: { dimension: 'server' },
      });
    }
  }
}

export class PostgresFreeServerRegistryStore {
  constructor(repository) {
    if (!repository || typeof repository.database !== 'function' || typeof repository.advisoryLock !== 'function') {
      throw new Error('PostgreSQL FreeServerRegistry requires database() and advisoryLock().');
    }
    this.repository = repository;
  }

  async list() {
    const result = await this.repository.database().query(
      "SELECT * FROM control_servers WHERE tier = 'free' ORDER BY priority, country_code, code",
    );
    return result.rows.map(fromRow);
  }

  async find(id) {
    const result = await this.repository.database().query(
      "SELECT * FROM control_servers WHERE id = $1 AND tier = 'free'",
      [id],
    );
    return result.rowCount === 1 ? fromRow(result.rows[0]) : null;
  }

  async create(value) {
    try {
      const result = await this.repository.database().query(
        `INSERT INTO control_servers (
           id, code, name, country_code, city, tier, status, load_ratio, latency_hint_ms,
           protocols, secret_ref, priority, provider_ref, upstream_ref, max_load_ratio,
           max_active_profile_grants, emergency_disabled, rollout_basis_points, min_app_version
         ) VALUES ($1, $2, $3, $4, $5, 'free', $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18)
         RETURNING *`,
        [value.id, value.code, value.name, value.countryCode, value.city, value.status, value.loadRatio,
          value.latencyHintMs, value.protocols, value.secretRef, value.priority, value.providerRef,
          value.upstreamRef, value.maxLoadRatio, value.maxActiveProfileGrants, value.emergencyDisabled,
          value.rolloutBasisPoints, value.minAppVersion],
      );
      return fromRow(result.rows[0]);
    } catch (error) {
      if (error?.code === '23505') throw new ApiError(409, 'free_server_conflict', 'Free server ID or code already exists.');
      throw error;
    }
  }

  async update(id, patch) {
    const current = await this.find(id);
    if (!current) return null;
    const value = { ...current, ...patch, id };
    try {
      const result = await this.repository.database().query(
        `UPDATE control_servers SET
           code = $2, name = $3, country_code = $4, city = $5, status = $6, load_ratio = $7,
           latency_hint_ms = $8, protocols = $9, secret_ref = $10, priority = $11,
           provider_ref = $12, upstream_ref = $13, max_load_ratio = $14,
           max_active_profile_grants = $15, emergency_disabled = $16,
           rollout_basis_points = $17, min_app_version = $18, updated_at = now()
         WHERE id = $1 AND tier = 'free' RETURNING *`,
        [id, value.code, value.name, value.countryCode, value.city, value.status, value.loadRatio,
          value.latencyHintMs, value.protocols, value.secretRef, value.priority, value.providerRef,
          value.upstreamRef, value.maxLoadRatio, value.maxActiveProfileGrants, value.emergencyDisabled,
          value.rolloutBasisPoints, value.minAppVersion],
      );
      return result.rowCount === 1 ? fromRow(result.rows[0]) : null;
    } catch (error) {
      if (error?.code === '23505') throw new ApiError(409, 'free_server_conflict', 'Free server code already exists.');
      throw error;
    }
  }

  async enforceServerAdmission({ serverId, now, control }) {
    if (control.maxActiveProfileGrants == null) return;
    await this.repository.advisoryLock('free-server-admission', serverId);
    const result = await this.repository.database().query(
      `SELECT count(*) AS active_count FROM control_connection_profile_grants
        WHERE server_id = $1 AND consumed_at IS NULL AND expires_at > $2`,
      [serverId, now],
    );
    if (Number(result.rows[0].active_count) >= control.maxActiveProfileGrants) {
      throw new ApiError(503, 'free_server_capacity_reached', 'Free server admission capacity was reached.', {
        retryable: true, details: { dimension: 'server' },
      });
    }
  }
}

function createStore(repository, options) {
  if (options.store) return options.store;
  if (repository.kind === 'test-only') return new InMemoryFreeServerRegistryStore(repository);
  if (repository.kind === 'postgres-v1') return new PostgresFreeServerRegistryStore(repository);
  throw new Error('A production FreeServerRegistry store is required for this data adapter.');
}

export class FreeServerRegistryRepository {
  constructor(delegate, options = {}) {
    if (!delegate || typeof delegate.transaction !== 'function' || typeof delegate.listServers !== 'function'
      || typeof delegate.findServer !== 'function' || typeof delegate.findOwnedService !== 'function'
      || typeof delegate.reserveConnectionProfile !== 'function') {
      throw new Error('Free server registry delegate is incomplete.');
    }
    this.delegate = delegate;
    this.clock = options.clock ?? (() => new Date());
    this.store = createStore(delegate, options);
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

  async controls() {
    try {
      return await this.store.list();
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(503, 'free_server_registry_unavailable', 'Free server registry is temporarily unavailable.', { retryable: true });
    }
  }

  async listServers() {
    const servers = await this.delegate.listServers();
    let controls;
    try {
      controls = new Map((await this.controls()).map((control) => [control.id, control]));
    } catch (error) {
      if (error?.code === 'free_server_registry_unavailable') return servers.filter((server) => server.tier !== 'free');
      throw error;
    }
    return servers.filter((server) => {
      if (server.tier !== 'free') return true;
      const control = controls.get(server.id) ?? defaultControl(server);
      return !unavailable(control);
    }).sort((left, right) => {
      if (left.tier !== 'free' || right.tier !== 'free') return 0;
      return (controls.get(left.id)?.priority ?? 100) - (controls.get(right.id)?.priority ?? 100);
    });
  }

  async findServer(id) {
    const metadata = (await this.delegate.listServers()).find((server) => server.id === id);
    if (metadata?.tier === 'free') {
      let control;
      try { control = await this.store.find(id); } catch {
        throw new ApiError(503, 'free_server_registry_unavailable', 'Free server registry is temporarily unavailable.', { retryable: true });
      }
      const blocked = unavailable(control ?? defaultControl(metadata));
      if (blocked) throw blocked;
    }
    return this.delegate.findServer(id);
  }

  async listManagedFreeServers() { return this.controls(); }

  async findManagedFreeServer(id) {
    try { return await this.store.find(id); } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(503, 'free_server_registry_unavailable', 'Free server registry is temporarily unavailable.', { retryable: true });
    }
  }

  async createManagedFreeServer(value) {
    const operation = () => this.store.create({ ...value, id: value.id ?? randomUUID() });
    if (this.transactionContext.getStore()) return operation();
    return this.transaction(operation);
  }

  async updateManagedFreeServer(id, patch) {
    const operation = () => this.store.update(id, patch);
    if (this.transactionContext.getStore()) return operation();
    return this.transaction(operation);
  }

  async reserveConnectionProfile(value) {
    const operation = async () => {
      const service = await this.delegate.findOwnedService(value.userId, value.serviceId);
      if (service?.tier !== 'free') return this.delegate.reserveConnectionProfile(value);
      let control;
      try { control = await this.store.find(value.serverId); } catch {
        throw new ApiError(503, 'free_server_registry_unavailable', 'Free server registry is temporarily unavailable.', { retryable: true });
      }
      if (!control) throw new ApiError(403, 'free_server_unavailable', 'Free server is not managed by the Free Server Registry.');
      const blocked = unavailable(control);
      if (blocked) throw blocked;
      await this.store.enforceServerAdmission({ serverId: value.serverId, now: this.clock(), control });
      return this.delegate.reserveConnectionProfile(value);
    };
    if (this.transactionContext.getStore()) return operation();
    return this.transaction(operation);
  }
}

export function withFreeServerRegistry(repository, options = {}) {
  return repository instanceof FreeServerRegistryRepository ? repository : new FreeServerRegistryRepository(repository, options);
}
