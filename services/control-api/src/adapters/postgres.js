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

function consent(row) {
  return {
    id: row.id, userId: row.user_id, purpose: row.purpose, status: row.status,
    policyVersion: row.policy_version,
    grantedAt: row.granted_at?.toISOString?.() ?? row.granted_at,
    revokedAt: row.revoked_at?.toISOString?.() ?? row.revoked_at ?? null,
  };
}

function configRelease(row, entries = []) {
  return {
    id: row.id, environment: row.environment, version: number(row.version), status: row.status,
    reason: row.reason, createdBy: row.created_by, publishedBy: row.published_by,
    contentDigest: row.content_digest,
    createdAt: row.created_at?.toISOString?.() ?? row.created_at,
    publishedAt: row.published_at?.toISOString?.() ?? row.published_at ?? null,
    entries,
  };
}

function featureFlag(row) {
  return {
    id: row.id, flagKey: row.flag_key, version: number(row.version), enabled: row.enabled,
    defaultVariant: row.default_variant, rolloutBasisPoints: row.rollout_basis_points,
    audience: row.audience ?? {}, experimentKey: row.experiment_key, reason: row.reason,
    createdBy: row.created_by, createdAt: row.created_at?.toISOString?.() ?? row.created_at,
  };
}

function bugReport(row) {
  return {
    id: row.id, userId: row.user_id, clientReportId: row.client_report_id, payloadDigest: row.payload_digest,
    publicCode: row.public_code, title: row.title, description: row.description, category: row.category,
    severity: row.severity, status: row.status, deviceId: row.device_id,
    occurredAt: row.occurred_at?.toISOString?.() ?? row.occurred_at, context: row.context,
    createdAt: row.created_at?.toISOString?.() ?? row.created_at,
    updatedAt: row.updated_at?.toISOString?.() ?? row.updated_at,
  };
}

function supportTicket(row) {
  return {
    id: row.id, userId: row.user_id, clientTicketId: row.client_ticket_id, payloadDigest: row.payload_digest,
    publicCode: row.public_code, category: row.category, priority: row.priority, subject: row.subject, body: row.body, status: row.status,
    createdAt: row.created_at?.toISOString?.() ?? row.created_at,
    updatedAt: row.updated_at?.toISOString?.() ?? row.updated_at,
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

  async ownsDevice(userId, deviceId) {
    const result = await this.database().query(
      "SELECT 1 FROM control_devices WHERE user_id = $1 AND id = $2 AND status = 'active'",
      [userId, deviceId],
    );
    return result.rowCount === 1;
  }

  async findConsent(userId, receiptId, purpose) {
    const result = await this.database().query(
      'SELECT * FROM control_consent_receipts WHERE user_id = $1 AND id = $2 AND purpose = $3',
      [userId, receiptId, purpose],
    );
    return result.rowCount === 1 ? consent(result.rows[0]) : null;
  }

  async saveConsent(value) {
    if (value.status === 'revoked') {
      await this.database().query(
        `UPDATE control_consent_receipts SET status = 'revoked', revoked_at = $3
          WHERE user_id = $1 AND purpose = $2 AND status = 'granted'`,
        [value.userId, value.purpose, value.revokedAt],
      );
    }
    const result = await this.database().query(
      `INSERT INTO control_consent_receipts
       (id, user_id, purpose, status, policy_version, granted_at, revoked_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.purpose, value.status, value.policyVersion, value.grantedAt, value.revokedAt],
    );
    return consent(result.rows[0]);
  }

  async listConsents(userId) {
    const result = await this.database().query(
      `SELECT DISTINCT ON (purpose) * FROM control_consent_receipts
        WHERE user_id = $1 ORDER BY purpose, created_at DESC`,
      [userId],
    );
    return result.rows.map(consent);
  }

  async getPublishedRuntimeConfiguration(environment) {
    const releaseResult = await this.database().query(
      `SELECT * FROM control_remote_config_releases
        WHERE environment = $1 AND status = 'published' LIMIT 1`,
      [environment],
    );
    if (releaseResult.rowCount === 0) return { release: null };
    const entryResult = await this.database().query(
      'SELECT config_key, config_value, sensitivity, target FROM control_remote_config_entries WHERE release_id = $1 ORDER BY config_key',
      [releaseResult.rows[0].id],
    );
    const entries = entryResult.rows.map((row) => ({ key: row.config_key, value: row.config_value, sensitivity: row.sensitivity, target: row.target ?? {} }));
    return { release: configRelease(releaseResult.rows[0], entries) };
  }

  async createRemoteConfigRelease(value) {
    const result = await this.database().query(
      `INSERT INTO control_remote_config_releases
       (id, environment, version, reason, created_by, content_digest, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
      [value.id ?? randomUUID(), value.environment, value.version, value.reason, value.createdBy, value.contentDigest, value.createdAt],
    ).catch((error) => {
      if (error.code === '23505') throw new ApiError(409, 'config_version_exists', 'Remote configuration version already exists.');
      throw error;
    });
    for (const entry of value.entries) {
      await this.database().query(
        `INSERT INTO control_remote_config_entries
         (release_id, config_key, config_value, sensitivity, target) VALUES ($1, $2, $3, $4, $5)`,
        [
          result.rows[0].id,
          entry.key,
          JSON.stringify(entry.value),
          entry.sensitivity,
          JSON.stringify(entry.target),
        ],
      );
    }
    return configRelease(result.rows[0], value.entries);
  }

  async publishRemoteConfigRelease({ releaseId, publisher, publishedAt }) {
    await this.advisoryLock('remote-config-release', releaseId);
    const current = await this.database().query(
      'SELECT * FROM control_remote_config_releases WHERE id = $1 FOR UPDATE', [releaseId],
    );
    if (current.rowCount === 0) return null;
    const release = current.rows[0];
    if (release.status !== 'draft') throw new ApiError(409, 'config_not_publishable', 'Only a draft release can be published.');
    if (release.environment === 'production' && release.created_by === publisher) {
      throw new ApiError(409, 'dual_control_required', 'A different administrator must publish a production release.');
    }
    await this.database().query(
      "UPDATE control_remote_config_releases SET status = 'retired' WHERE environment = $1 AND status = 'published'",
      [release.environment],
    );
    const result = await this.database().query(
      `UPDATE control_remote_config_releases SET status = 'published', published_by = $2, published_at = $3
        WHERE id = $1 RETURNING *`,
      [releaseId, publisher, publishedAt],
    );
    const entries = await this.database().query(
      'SELECT config_key, config_value, sensitivity, target FROM control_remote_config_entries WHERE release_id = $1 ORDER BY config_key',
      [releaseId],
    );
    return configRelease(result.rows[0], entries.rows.map((row) => ({ key: row.config_key, value: row.config_value, sensitivity: row.sensitivity, target: row.target ?? {} })));
  }

  async upsertFeatureFlag(value) {
    await this.advisoryLock('feature-flag', value.flagKey);
    const versionResult = await this.database().query(
      'SELECT COALESCE(MAX(version), 0) + 1 AS next_version FROM control_feature_flag_versions WHERE flag_key = $1',
      [value.flagKey],
    );
    const result = await this.database().query(
      `INSERT INTO control_feature_flag_versions
       (id, flag_key, version, enabled, default_variant, rollout_basis_points, audience, experiment_key, reason, created_by, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING *`,
      [value.id ?? randomUUID(), value.flagKey, number(versionResult.rows[0].next_version), value.enabled,
        value.defaultVariant, value.rolloutBasisPoints, value.audience, value.experimentKey, value.reason, value.createdBy, value.createdAt],
    );
    return featureFlag(result.rows[0]);
  }

  async listCurrentFeatureFlags() {
    const result = await this.database().query(
      'SELECT DISTINCT ON (flag_key) * FROM control_feature_flag_versions ORDER BY flag_key, version DESC',
    );
    return result.rows.map(featureFlag);
  }

  async ingestAnalyticsBatch(value) {
    await this.advisoryLock('analytics-batch', `${value.userId}:${value.batchId}`);
    const consentResult = await this.database().query(
      `SELECT status, revoked_at FROM control_consent_receipts
        WHERE id = $1 AND user_id = $2 AND purpose = 'product_analytics' FOR SHARE`,
      [value.consentReceiptId, value.userId],
    );
    if (consentResult.rowCount !== 1 || consentResult.rows[0].status !== 'granted' || consentResult.rows[0].revoked_at) {
      throw new ApiError(403, 'analytics_consent_required', 'A current analytics consent receipt is required.');
    }
    const existing = await this.database().query(
      'SELECT payload_digest, event_count FROM control_analytics_batches WHERE user_id = $1 AND batch_id = $2',
      [value.userId, value.batchId],
    );
    if (existing.rowCount === 1) {
      if (existing.rows[0].payload_digest !== value.payloadDigest) throw new ApiError(409, 'analytics_batch_conflict', 'Batch ID was already used with different content.');
      return { replay: true, acceptedCount: 0, duplicateCount: number(existing.rows[0].event_count) };
    }
    await this.database().query(
      `INSERT INTO control_analytics_batches
       (user_id, batch_id, consent_receipt_id, payload_digest, event_count, received_at)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [value.userId, value.batchId, value.consentReceiptId, value.payloadDigest, value.events.length, value.receivedAt],
    );
    let acceptedCount = 0;
    for (const event of value.events) {
      const result = await this.database().query(
        `INSERT INTO control_analytics_events
         (user_id, event_id, batch_id, event_name, occurred_at, schema_version, session_id, properties, received_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) ON CONFLICT (user_id, event_id) DO NOTHING`,
        [value.userId, event.event_id, value.batchId, event.name, event.occurred_at, event.schema_version,
          event.session_id, event.properties, value.receivedAt],
      );
      acceptedCount += result.rowCount;
    }
    return { replay: false, acceptedCount, duplicateCount: value.events.length - acceptedCount };
  }

  async createBugReport(value) {
    await this.advisoryLock('bug-report', `${value.userId}:${value.clientReportId}`);
    const existing = await this.database().query(
      'SELECT * FROM control_bug_reports WHERE user_id = $1 AND client_report_id = $2',
      [value.userId, value.clientReportId],
    );
    if (existing.rowCount === 1) {
      if (existing.rows[0].payload_digest !== value.payloadDigest) throw new ApiError(409, 'bug_report_conflict', 'Client report ID was reused with different content.');
      return { replay: true, report: bugReport(existing.rows[0]) };
    }
    const result = await this.database().query(
      `INSERT INTO control_bug_reports
       (id, user_id, client_report_id, payload_digest, public_code, title, description, category, severity, status,
        device_id, occurred_at, context, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.clientReportId, value.payloadDigest, value.publicCode,
        value.title, value.description, value.category, value.severity, value.status, value.deviceId,
        value.occurredAt, value.context, value.createdAt, value.updatedAt],
    );
    return { replay: false, report: bugReport(result.rows[0]) };
  }

  async listBugReports(userId) {
    const result = await this.database().query(
      'SELECT * FROM control_bug_reports WHERE user_id = $1 ORDER BY created_at DESC LIMIT 100', [userId],
    );
    return result.rows.map(bugReport);
  }

  async findOwnedBugReport(userId, id) {
    const result = await this.database().query('SELECT * FROM control_bug_reports WHERE user_id = $1 AND id = $2', [userId, id]);
    return result.rowCount === 1 ? bugReport(result.rows[0]) : null;
  }

  async createSupportTicket(value) {
    await this.advisoryLock('support-ticket', `${value.userId}:${value.clientTicketId}`);
    const existing = await this.database().query(
      'SELECT * FROM control_support_tickets WHERE user_id = $1 AND client_ticket_id = $2', [value.userId, value.clientTicketId],
    );
    if (existing.rowCount === 1) {
      if (existing.rows[0].payload_digest !== value.payloadDigest) throw new ApiError(409, 'support_ticket_conflict', 'Client ticket ID was reused with different content.');
      return { replay: true, ticket: supportTicket(existing.rows[0]) };
    }
    const result = await this.database().query(
      `INSERT INTO control_support_tickets
       (id,user_id,client_ticket_id,payload_digest,public_code,category,priority,subject,body,status,created_at,updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.clientTicketId, value.payloadDigest, value.publicCode, value.category,
        value.priority, value.subject, value.body, value.status, value.createdAt, value.updatedAt],
    );
    return { replay: false, ticket: supportTicket(result.rows[0]) };
  }

  async listSupportTickets(userId) {
    const result = await this.database().query(
      'SELECT * FROM control_support_tickets WHERE user_id = $1 ORDER BY created_at DESC LIMIT 100', [userId],
    );
    return result.rows.map(supportTicket);
  }

  async findOwnedSupportTicket(userId, id) {
    const result = await this.database().query('SELECT * FROM control_support_tickets WHERE user_id = $1 AND id = $2', [userId, id]);
    return result.rowCount === 1 ? supportTicket(result.rows[0]) : null;
  }

  async createDiagnosticReport(value) {
    await this.advisoryLock('diagnostic-report', `${value.userId}:${value.clientReportId}`);
    const existing = await this.database().query(
      'SELECT * FROM control_diagnostic_reports WHERE user_id = $1 AND client_report_id = $2', [value.userId, value.clientReportId],
    );
    if (existing.rowCount === 1) {
      if (existing.rows[0].payload_digest !== value.payloadDigest) throw new ApiError(409, 'diagnostic_report_conflict', 'Client report ID was reused with different content.');
      const row = existing.rows[0];
      return { replay: true, report: { id: row.id, redactionVersion: row.redaction_version, expiresAt: row.expires_at?.toISOString?.() ?? row.expires_at } };
    }
    const result = await this.database().query(
      `INSERT INTO control_diagnostic_reports
       (id,user_id,client_report_id,payload_digest,device_id,bug_report_id,support_ticket_id,started_at,finished_at,tests,redaction_version,expires_at,created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) RETURNING *`,
      [value.id ?? randomUUID(), value.userId, value.clientReportId, value.payloadDigest, value.deviceId,
        value.bugReportId, value.supportTicketId, value.startedAt, value.finishedAt, value.tests,
        value.redactionVersion, value.expiresAt, value.createdAt],
    );
    const row = result.rows[0];
    return { replay: false, report: { id: row.id, redactionVersion: row.redaction_version, expiresAt: row.expires_at?.toISOString?.() ?? row.expires_at } };
  }

  async appendAdminAudit(value) {
    const result = await this.database().query(
      `INSERT INTO control_admin_audit_log
       (id,actor_subject,action,resource_type,resource_id,reason,request_id,before_digest,after_digest,outcome,created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
      [value.id ?? randomUUID(), value.actorSubject, value.action, value.resourceType, value.resourceId,
        value.reason, value.requestId, value.beforeDigest ?? null, value.afterDigest ?? null, value.outcome, value.createdAt],
    );
    return result.rows[0];
  }

  async listAdminAudit({ limit = 100 } = {}) {
    const result = await this.database().query(
      `SELECT id, actor_subject, action, resource_type, resource_id, reason, request_id, outcome, created_at
         FROM control_admin_audit_log ORDER BY created_at DESC LIMIT $1`, [Math.min(limit, 100)],
    );
    return result.rows.map((row) => ({
      id: row.id, actorSubject: row.actor_subject, action: row.action, resourceType: row.resource_type,
      resourceId: row.resource_id, reason: row.reason, requestId: row.request_id, outcome: row.outcome,
      createdAt: row.created_at?.toISOString?.() ?? row.created_at,
    }));
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
