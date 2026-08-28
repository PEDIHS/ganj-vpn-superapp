import { ApiError } from './errors.js';

function copy(value) {
  return structuredClone(value);
}

function fromPostgresRow(row) {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    country_code: row.country_code.trim(),
    city: row.city,
    tier: row.tier,
    status: row.status,
    load_ratio: Number(row.load_ratio),
    latency_hint_ms: row.latency_hint_ms,
    protocols: row.protocols ?? [],
    secret_ref: row.secret_ref,
    updated_at: row.updated_at?.toISOString?.() ?? row.updated_at ?? null,
  };
}

async function postgresCreate(repository, value) {
  const result = await repository.database().query(
    `INSERT INTO control_servers (
       id, code, name, country_code, city, tier, status, load_ratio,
       latency_hint_ms, protocols, secret_ref
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
     RETURNING *`,
    [
      value.id, value.code, value.name, value.country_code, value.city, value.tier,
      value.status, value.load_ratio, value.latency_hint_ms, value.protocols, value.secret_ref,
    ],
  );
  return fromPostgresRow(result.rows[0]);
}

async function postgresSave(repository, value) {
  const result = await repository.database().query(
    `UPDATE control_servers SET
       name = $2, country_code = $3, city = $4, tier = $5, status = $6,
       load_ratio = $7, latency_hint_ms = $8, protocols = $9, updated_at = now()
     WHERE id = $1 RETURNING *`,
    [
      value.id, value.name, value.country_code, value.city, value.tier, value.status,
      value.load_ratio, value.latency_hint_ms, value.protocols,
    ],
  );
  if (result.rowCount !== 1) throw new ApiError(404, 'server_not_found', 'Server was not found.');
  return fromPostgresRow(result.rows[0]);
}

function memoryCreate(repository, value) {
  if ([...repository.servers.values()].some((server) => server.code === value.code)) {
    throw new ApiError(409, 'server_code_conflict', 'Server code already exists.');
  }
  repository.servers.set(value.id, copy(value));
  return copy(value);
}

function memorySave(repository, value) {
  if (!repository.servers.has(value.id)) throw new ApiError(404, 'server_not_found', 'Server was not found.');
  const current = repository.servers.get(value.id);
  const stored = { ...copy(value), secret_ref: value.secret_ref ?? current.secret_ref ?? current.secretRef ?? null };
  repository.servers.set(value.id, stored);
  return copy(stored);
}

/**
 * Adds the narrow Admin Control Plane mutation boundary without forking the main data adapter.
 * Existing repository methods stay bound to the original instance so transaction semantics and
 * AsyncLocalStorage-backed PostgreSQL clients remain intact.
 */
export function withAdminControlPlaneRepository(repository) {
  if (!repository || typeof repository.listServers !== 'function' || typeof repository.findServer !== 'function') {
    throw new Error('A compatible Control API repository is required.');
  }

  const createServer = async (value) => {
    if (repository.kind === 'postgres-v1' && typeof repository.database === 'function') {
      return postgresCreate(repository, value);
    }
    if (repository.servers instanceof Map) return memoryCreate(repository, value);
    throw new Error('Repository does not support Admin Control Plane server creation.');
  };

  const saveServer = async (value) => {
    if (repository.kind === 'postgres-v1' && typeof repository.database === 'function') {
      return postgresSave(repository, value);
    }
    if (repository.servers instanceof Map) return memorySave(repository, value);
    throw new Error('Repository does not support Admin Control Plane server updates.');
  };

  return new Proxy(repository, {
    get(target, property) {
      if (property === 'createServer') return createServer;
      if (property === 'saveServer') return saveServer;
      const value = Reflect.get(target, property, target);
      return typeof value === 'function' ? value.bind(target) : value;
    },
  });
}
