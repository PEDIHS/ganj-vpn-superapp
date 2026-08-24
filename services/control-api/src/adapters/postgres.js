import { AsyncLocalStorage } from 'node:async_hooks';
import { createHash, randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { ApiError } from '../errors.js';

function number(value) {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) throw new Error('Database integer exceeds the JSON safe-integer range.');
  return parsed;
}

function plan(row) {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    tier: row.tier,
    duration_days: row.duration_days,
    traffic_limit_bytes: number(row.traffic_limit_bytes),
    device_limit: row.device_limit,
    features: row.features ?? [],
    price: { amount_minor: number(row.price_amount_minor), currency: row.price_currency.trim() },
    channels: row.channels ?? [],
    play_product_id: row.play_product_id,
    active: row.active,
  };
}

function service(row) {
  return {
    id: row.id,
    userId: row.user_id,
    planId: row.plan_id,
    name: row.name,
    status: row.status,
    tier: row.tier,
    country_code: row.country_code?.trim() ?? null,
    traffic_limit_bytes: number(row.traffic_limit_bytes),
    traffic_used_bytes: number(row.traffic_used_bytes),
    expires_at: row.expires_at?.toISOString?.() ?? row.expires_at ?? null,
    device_limit: row.device_limit,
    allowed_protocols: row.allowed_protocols ?? [],
    deviceIds: row.device_ids ?? [],
    version: number(row.version),
  };
}

function server(row, connection) {
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
    secretRef: row.secret_ref,
    ...(connection ? { connection } : {}),
  };
}

function order(row) {
  return {
    id: row.id,
    userId: row.user_id,
    planId: row.plan_id,
    serviceId: row.service_id,
    channel: row.channel,
    status: row.status,
    total: { amount_minor: number(row.total_amount_minor), currency: row.total_currency.trim() },
    entitlementServiceId: row.entitlement_service_id,
    externalTransactionId: row.external_transaction_id,
    purchaseTokenDigest: row.purchase_token_digest,
    fulfilledAt: row.fulfilled_at?.toISOString?.() ?? row.fulfilled_at ?? null,
    createdAt: row.created_at?.toISOString?.() ?? row.created_at,
  };
}

async function loadFactory(specifier, exportName, environment) {
  if (!specifier) throw new Error(`${exportName} module is required.`);
  const target = specifier.startsWith('.') || specifier.startsWith('/')
    ? pathToFileURL(resolve(specifier)).href
    : specifier;
  const loaded = await import(target);
  if (typeof loaded[exportName] !== 'function') throw new Error(`${specifier} must export ${exportName}().`);
  return loaded[exportName]({ environment });
}

export class PostgresRepository {
  constructor({ pool, serverSecretResolver }) {
    if (!pool || typeof pool.query !== 'function' || typeof pool.connect !== 'function') throw new Error('PostgreSQL pool is required.');
    if (!serverSecretResolver || typeof serverSecretResolver.resolveServerConnection !== 'function') {
      throw new Error('Server secret resolver is required.');
    }
    this.pool = pool;
    this.kind = 'postgres-v1';
    this.serverSecretResolver = serverSecretResolver;
    this.context = new AsyncLocalStorage();
  }

  database() { return this.context.getStore() ?? this.pool; }

  async transaction(work) {
    if (this.context.getStore()) return work();
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query("SET LOCAL statement_timeout = '10s'");
      await client.query("SET LOCAL lock_timeout = '3s'");
      const result = await this.context.run(client, work);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async advisoryLock(namespace, value) {
    if (!this.context.getStore()) return;
    await this.database().query('SELECT pg_advisory_xact_lock(hashtextextended($1, 0))', [`${namespace}:${value}`]);
  }

  async listPlans(channel) {
    const result = await this.database().query(
      'SELECT * FROM control_plans WHERE active = true AND $1 = ANY(channels) ORDER BY price_amount_minor, code',
      [channel],
    );
    return result.rows.map(plan);
  }

  async findPlan(id) {
    const result = await this.database().query('SELECT * FROM control_plans WHERE id = $1', [id]);
    return result.rowCount === 1 ? plan(result.rows[0]) : null;
  }

  async serviceQuery(where, parameters) {
    const result = await this.database().query(
      `SELECT s.*, COALESCE(array_agg(sd.device_id::text) FILTER (WHERE sd.device_id IS NOT NULL), '{}') AS device_ids
         FROM control_services s
         LEFT JOIN control_service_devices sd ON sd.service_id = s.id
        WHERE ${where}
        GROUP BY s.id
        ORDER BY s.created_at DESC`,
      parameters,
    );
    return result.rows.map(service);
  }

  async listServices(userId) { return this.serviceQuery('s.user_id = $1', [userId]); }

  async findOwnedService(userId, id) {
    if (this.context.getStore()) {
      const lock = await this.database().query('SELECT id FROM control_services WHERE user_id = $1 AND id = $2 FOR UPDATE', [userId, id]);
      if (lock.rowCount === 0) return null;
    }
    const rows = await this.serviceQuery('s.user_id = $1 AND s.id = $2', [userId, id]);
    return rows[0] ?? null;
  }

  async replaceServiceDevices(value) {
    await this.database().query('DELETE FROM control_service_devices WHERE service_id = $1', [value.id]);
    for (const deviceId of value.deviceIds ?? []) {
      const inserted = await this.database().query(
        `INSERT INTO control_service_devices (service_id, device_id, user_id)
         SELECT $1, d.id, d.user_id FROM control_devices d
          WHERE d.id = $2 AND d.user_id = $3 AND d.status = 'active'
         ON CONFLICT DO NOTHING RETURNING device_id`,
        [value.id, deviceId, value.userId],
      );
      if (inserted.rowCount !== 1) throw new ApiError(403, 'device_not_trusted', 'A service device is not active and owned.');
    }
  }

  async saveService(value) {
    const result = await this.database().query(
      `UPDATE control_services SET
         plan_id = $3, name = $4, status = $5, tier = $6, country_code = $7,
         traffic_limit_bytes = $8, traffic_used_bytes = $9, expires_at = $10,
         device_limit = $11, allowed_protocols = $12, version = version + 1, updated_at = now()
       WHERE id = $1 AND user_id = $2 RETURNING *`,
      [value.id, value.userId, value.planId, value.name, value.status, value.tier, value.country_code,
        value.traffic_limit_bytes, value.traffic_used_bytes, value.expires_at, value.device_limit, value.allowed_protocols],
    );
    if (result.rowCount !== 1) throw new ApiError(404, 'service_not_found', 'Service was not found.');
    await this.replaceServiceDevices(value);
    return service({ ...result.rows[0], device_ids: value.deviceIds ?? [] });
  }

  async createService(value) {
    const result = await this.database().query(
      `INSERT INTO control_services (
         id, user_id, plan_id, name, status, tier, country_code, traffic_limit_bytes,
         traffic_used_bytes, expires_at, device_limit, allowed_protocols
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.planId, value.name, value.status, value.tier, value.country_code,
        value.traffic_limit_bytes, value.traffic_used_bytes, value.expires_at, value.device_limit, value.allowed_protocols],
    );
    const created = { ...result.rows[0], device_ids: value.deviceIds ?? [] };
    await this.replaceServiceDevices({ ...value, id: created.id });
    return service(created);
  }

  async listServers() {
    const result = await this.database().query('SELECT * FROM control_servers ORDER BY country_code, code');
    return result.rows.map((row) => server(row));
  }

  async findServer(id) {
    const result = await this.database().query('SELECT * FROM control_servers WHERE id = $1', [id]);
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    const connection = await this.serverSecretResolver.resolveServerConnection({
      secretRef: row.secret_ref,
      serverId: row.id,
      protocols: row.protocols,
    });
    if (!connection || typeof connection.endpoint !== 'string' || typeof connection.credential !== 'string') {
      throw new Error('Server secret resolver returned an invalid connection payload.');
    }
    return server(row, connection);
  }

  async createOrder(value) {
    const result = await this.database().query(
      `INSERT INTO control_orders (
         id, user_id, plan_id, service_id, channel, status, total_amount_minor, total_currency,
         entitlement_service_id, external_transaction_id, purchase_token_digest, fulfilled_at, created_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, COALESCE($13, now()))
       RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.planId, value.serviceId, value.channel, value.status,
        value.total.amount_minor, value.total.currency, value.entitlementServiceId,
        value.externalTransactionId ?? null, value.purchaseTokenDigest ?? null, value.fulfilledAt ?? null, value.createdAt ?? null],
    );
    return order(result.rows[0]);
  }

  async findOwnedOrder(userId, id) {
    const suffix = this.context.getStore() ? ' FOR UPDATE' : '';
    const result = await this.database().query(`SELECT * FROM control_orders WHERE user_id = $1 AND id = $2${suffix}`, [userId, id]);
    return result.rowCount === 1 ? order(result.rows[0]) : null;
  }

  async saveOrder(value) {
    const result = await this.database().query(
      `UPDATE control_orders SET
         status = $3, service_id = $4, entitlement_service_id = $5, external_transaction_id = $6,
         purchase_token_digest = $7, fulfilled_at = $8, updated_at = now()
       WHERE id = $1 AND user_id = $2 RETURNING *`,
      [value.id, value.userId, value.status, value.serviceId, value.entitlementServiceId,
        value.externalTransactionId, value.purchaseTokenDigest, value.fulfilledAt],
    );
    if (result.rowCount !== 1) throw new ApiError(404, 'order_not_found', 'Order was not found.');
    return order(result.rows[0]);
  }

  async findIdempotency(scope, userId, key) {
    await this.advisoryLock('idempotency', `${scope}:${userId}:${key}`);
    const result = await this.database().query(
      `SELECT request_fingerprint, resource_id FROM control_idempotency_keys
        WHERE scope = $1 AND user_id = $2 AND idempotency_key = $3 AND expires_at > now()`,
      [scope, userId, key],
    );
    return result.rowCount === 1
      ? { fingerprint: result.rows[0].request_fingerprint, resourceId: result.rows[0].resource_id }
      : null;
  }

  async saveIdempotency(scope, userId, key, value) {
    await this.database().query(
      `INSERT INTO control_idempotency_keys (
         scope, user_id, idempotency_key, request_fingerprint, resource_id
       ) VALUES ($1, $2, $3, $4, $5)`,
      [scope, userId, key, value.fingerprint, value.resourceId],
    );
  }

  async findPurchaseTokenOwner(tokenDigest) {
    await this.advisoryLock('purchase-token', tokenDigest);
    const result = await this.database().query(
      'SELECT order_id FROM control_purchase_token_bindings WHERE token_digest = $1',
      [tokenDigest],
    );
    return result.rowCount === 1 ? result.rows[0].order_id : null;
  }

  async bindPurchaseToken(tokenDigest, orderId) {
    await this.database().query(
      'INSERT INTO control_purchase_token_bindings (token_digest, order_id) VALUES ($1, $2)',
      [tokenDigest, orderId],
    );
  }

  async reserveConnectionProfile(value) {
    await this.advisoryLock('profile-nonce', `${value.deviceId}:${value.clientNonceDigest}`);
    const existing = await this.database().query(
      'SELECT profile_id FROM control_connection_profile_grants WHERE device_id = $1 AND client_nonce_digest = $2',
      [value.deviceId, value.clientNonceDigest],
    );
    if (existing.rowCount > 0) return false;
    await this.database().query(
      `INSERT INTO control_connection_profile_grants (
         profile_id, user_id, service_id, device_id, server_id, client_nonce_digest, expires_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [value.profileId, value.userId, value.serviceId, value.deviceId, value.serverId, value.clientNonceDigest, value.expiresAt],
    );
    return true;
  }

  async consumeConnectionProfile({ profileId, deviceId, now = new Date() }) {
    const result = await this.database().query(
      `UPDATE control_connection_profile_grants SET consumed_at = $3
        WHERE profile_id = $1 AND device_id = $2 AND consumed_at IS NULL AND expires_at > $3
        RETURNING profile_id`,
      [profileId, deviceId, now],
    );
    return result.rowCount === 1;
  }

  async findOrderByPurchaseTokenDigest(tokenDigest) {
    const result = await this.database().query('SELECT * FROM control_orders WHERE purchase_token_digest = $1', [tokenDigest]);
    return result.rowCount === 1 ? order(result.rows[0]) : null;
  }

  async recordWebhookEvent({ provider, eventId, payloadDigest, eventType, receivedAt }) {
    await this.advisoryLock('webhook', `${provider}:${eventId}`);
    const existing = await this.database().query(
      'SELECT payload_digest, processing_status FROM control_webhook_events WHERE provider = $1 AND event_id = $2',
      [provider, eventId],
    );
    if (existing.rowCount === 1) {
      if (existing.rows[0].payload_digest !== payloadDigest) {
        throw new ApiError(409, 'webhook_replay_conflict', 'Webhook event ID was reused with different content.');
      }
      return { replay: true, status: existing.rows[0].processing_status };
    }
    await this.database().query(
      `INSERT INTO control_webhook_events (provider, event_id, payload_digest, event_type, received_at)
       VALUES ($1, $2, $3, $4, $5)`,
      [provider, eventId, payloadDigest, eventType ?? null, receivedAt],
    );
    return { replay: false, status: 'received' };
  }

  async completeWebhookEvent({ provider, eventId, status }) {
    await this.database().query(
      `UPDATE control_webhook_events SET processing_status = $3, processed_at = now()
        WHERE provider = $1 AND event_id = $2`,
      [provider, eventId, status],
    );
  }

  async validatePrincipal({ userId, deviceId, jti, now = new Date() }) {
    const jtiHash = createHash('sha256').update(jti).digest('hex');
    const result = await this.database().query(
      `SELECT u.status AS user_status, d.status AS device_status, d.attestation_status,
              d.signing_public_jwk, d.encryption_public_jwk, d.key_version,
              s.expires_at, s.revoked_at
         FROM control_users u
         JOIN control_devices d ON d.user_id = u.id AND d.id = $2
         JOIN control_auth_sessions s ON s.user_id = u.id AND s.device_id = d.id AND s.jti_hash = $3
        WHERE u.id = $1`,
      [userId, deviceId, jtiHash],
    );
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    if (row.user_status !== 'active' || row.device_status !== 'active' || row.attestation_status !== 'trusted'
      || row.revoked_at || new Date(row.expires_at).getTime() <= now.getTime()) return null;
    return {
      signingPublicJwk: row.signing_public_jwk,
      encryptionPublicJwk: row.encryption_public_jwk,
      keyVersion: row.key_version,
    };
  }

  async close() { await this.pool.end(); }
}

export async function createDataAdapter({ environment = process.env } = {}) {
  if (!environment.DATABASE_URL) throw new Error('DATABASE_URL is required.');
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.DATABASE_POOL_MAX ?? 10),
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-control-api',
  });
  const serverSecretResolver = await loadFactory(
    environment.CONTROL_API_SERVER_SECRET_ADAPTER_MODULE,
    'createServerSecretResolver',
    environment,
  );
  if (environment.NODE_ENV === 'production' && serverSecretResolver?.kind === 'test-only') {
    throw new Error('Test server secret resolver is forbidden in production.');
  }
  await pool.query('SELECT 1');
  return new PostgresRepository({ pool, serverSecretResolver });
}

export async function createPrincipalValidator({ environment = process.env } = {}) {
  if (!environment.DATABASE_URL) throw new Error('DATABASE_URL is required.');
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.AUTH_DATABASE_POOL_MAX ?? 5),
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-auth-validator',
  });
  await pool.query('SELECT 1');
  return {
    async validatePrincipal({ userId, deviceId, jti, now = new Date() }) {
      const jtiHash = createHash('sha256').update(jti).digest('hex');
      const result = await pool.query(
        `SELECT u.status AS user_status, d.status AS device_status, d.attestation_status,
                d.signing_public_jwk, d.encryption_public_jwk, d.key_version,
                s.expires_at, s.revoked_at
           FROM control_users u
           JOIN control_devices d ON d.user_id = u.id AND d.id = $2
           JOIN control_auth_sessions s ON s.user_id = u.id AND s.device_id = d.id AND s.jti_hash = $3
          WHERE u.id = $1`,
        [userId, deviceId, jtiHash],
      );
      if (result.rowCount !== 1) return null;
      const row = result.rows[0];
      if (row.user_status !== 'active' || row.device_status !== 'active' || row.attestation_status !== 'trusted'
        || row.revoked_at || new Date(row.expires_at).getTime() <= now.getTime()) return null;
      return {
        signingPublicJwk: row.signing_public_jwk,
        encryptionPublicJwk: row.encryption_public_jwk,
        keyVersion: row.key_version,
      };
    },
    async close() { await pool.end(); },
  };
}
