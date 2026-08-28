import { ApiError } from './errors.js';

function instant(value) { return value?.toISOString?.() ?? value ?? null; }
function safeInteger(value) {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) throw new Error('Admin read-model integer exceeds JSON safe range.');
  return parsed;
}

function serviceView(row) {
  const upstream = row.provider_type ? {
    provider_type: row.provider_type,
    source_key: row.source_key,
    external_service_id: row.external_service_id,
    external_service_username: row.external_service_username,
    connector_ref: row.connector_ref,
  } : null;
  return {
    id: row.id,
    plan_id: row.plan_id,
    plan_code: row.plan_code ?? null,
    name: row.name,
    status: row.status,
    tier: row.tier,
    country_code: row.country_code?.trim?.() ?? row.country_code ?? null,
    traffic_limit_bytes: safeInteger(row.traffic_limit_bytes),
    traffic_used_bytes: safeInteger(row.traffic_used_bytes) ?? 0,
    expires_at: instant(row.expires_at),
    device_limit: row.device_limit,
    allowed_protocols: row.allowed_protocols ?? [],
    upstream,
    created_at: instant(row.created_at),
    updated_at: instant(row.updated_at),
  };
}

function orderView(row) {
  return {
    id: row.id,
    plan_id: row.plan_id,
    service_id: row.service_id ?? null,
    channel: row.channel,
    status: row.status,
    total: { amount_minor: safeInteger(row.total_amount_minor), currency: row.total_currency?.trim?.() ?? row.total_currency },
    entitlement_service_id: row.entitlement_service_id ?? null,
    fulfilled_at: instant(row.fulfilled_at),
    created_at: instant(row.created_at),
    updated_at: instant(row.updated_at),
  };
}

export class InMemoryAdminReadModel {
  constructor(repository) {
    if (!(repository?.services instanceof Map) || !(repository?.orders instanceof Map)) {
      throw new Error('In-memory admin read model requires test service/order stores.');
    }
    this.repository = repository;
  }

  async sharedAccount(userId) {
    const services = [...this.repository.services.values()].filter((item) => item.userId === userId);
    const orders = [...this.repository.orders.values()].filter((item) => item.userId === userId);
    if (services.length === 0 && orders.length === 0) return null;
    const deviceIds = [...new Set(services.flatMap((item) => item.deviceIds ?? []))];
    return {
      user: {
        id: userId,
        status: 'active',
        display_name: null,
        locale: 'fa-IR',
        telegram_linked: false,
        telegram_subject: null,
        created_at: null,
        updated_at: null,
      },
      active_session_count: 0,
      devices: deviceIds.map((id) => ({ id, status: 'active', key_version: null, attestation_status: null, last_seen_at: null, revoked_at: null, created_at: null })),
      services: services.map((item) => serviceView({ ...item, plan_id: item.planId, country_code: item.country_code })),
      orders: orders.map((item) => orderView({
        ...item,
        plan_id: item.planId,
        service_id: item.serviceId,
        total_amount_minor: item.total?.amount_minor,
        total_currency: item.total?.currency,
        entitlement_service_id: item.entitlementServiceId,
        fulfilled_at: item.fulfilledAt,
        created_at: item.createdAt,
        updated_at: item.updatedAt,
      })),
    };
  }

  async findUpstreamBinding() { return null; }
}

export class PostgresAdminReadModel {
  constructor(repository) {
    if (!repository || typeof repository.database !== 'function') throw new Error('PostgreSQL admin read model requires database().');
    this.repository = repository;
  }

  async sharedAccount(userId) {
    const db = this.repository.database();
    const userResult = await db.query('SELECT * FROM control_users WHERE id = $1', [userId]);
    if (userResult.rowCount !== 1) return null;
    const [deviceResult, serviceResult, orderResult, sessionResult] = await Promise.all([
      db.query(
        `SELECT id, status, key_version, attestation_status, last_seen_at, revoked_at, created_at
           FROM control_devices WHERE user_id = $1 ORDER BY created_at DESC`,
        [userId],
      ),
      db.query(
        `SELECT s.*, p.code AS plan_code,
                b.provider_type, b.source_key, b.external_service_id,
                b.external_service_username, b.connector_ref
           FROM control_services s
           JOIN control_plans p ON p.id = s.plan_id
           LEFT JOIN control_service_upstream_bindings b
             ON b.service_id = s.id AND b.user_id = s.user_id
          WHERE s.user_id = $1
          ORDER BY s.created_at DESC`,
        [userId],
      ),
      db.query(
        `SELECT id, plan_id, service_id, channel, status, total_amount_minor, total_currency,
                entitlement_service_id, fulfilled_at, created_at, updated_at
           FROM control_orders WHERE user_id = $1 ORDER BY created_at DESC LIMIT 200`,
        [userId],
      ),
      db.query(
        `SELECT count(*) AS active_count FROM control_auth_sessions
          WHERE user_id = $1 AND revoked_at IS NULL AND expires_at > now()`,
        [userId],
      ),
    ]);
    const row = userResult.rows[0];
    return {
      user: {
        id: row.id,
        status: row.status,
        display_name: row.display_name ?? null,
        locale: row.locale,
        telegram_linked: Boolean(row.telegram_subject),
        telegram_subject: row.telegram_subject ?? null,
        created_at: instant(row.created_at),
        updated_at: instant(row.updated_at),
      },
      active_session_count: Number(sessionResult.rows[0]?.active_count ?? 0),
      devices: deviceResult.rows.map((device) => ({
        id: device.id,
        status: device.status,
        key_version: device.key_version,
        attestation_status: device.attestation_status,
        last_seen_at: instant(device.last_seen_at),
        revoked_at: instant(device.revoked_at),
        created_at: instant(device.created_at),
      })),
      services: serviceResult.rows.map(serviceView),
      orders: orderResult.rows.map(orderView),
    };
  }

  async findUpstreamBinding(serviceId) {
    const result = await this.repository.database().query(
      `SELECT b.*, s.status AS service_status, s.tier AS service_tier
         FROM control_service_upstream_bindings b
         JOIN control_services s ON s.id = b.service_id AND s.user_id = b.user_id
        WHERE b.service_id = $1`,
      [serviceId],
    );
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    return {
      serviceId: row.service_id,
      userId: row.user_id,
      providerType: row.provider_type,
      sourceKey: row.source_key,
      externalServiceId: row.external_service_id,
      serviceUsername: row.external_service_username,
      connectorRef: row.connector_ref,
      serviceStatus: row.service_status,
      serviceTier: row.service_tier,
    };
  }
}

export function createAdminReadModel(repository) {
  if (repository?.kind === 'test-only') return new InMemoryAdminReadModel(repository);
  if (repository?.kind === 'postgres-v1') return new PostgresAdminReadModel(repository);
  throw new ApiError(503, 'admin_read_model_unavailable', 'Admin read model is not configured for the current data adapter.', { retryable: true });
}
