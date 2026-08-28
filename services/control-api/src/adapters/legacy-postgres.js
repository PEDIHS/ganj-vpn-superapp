import { randomUUID } from 'node:crypto';

function iso(value) {
  return value?.toISOString?.() ?? value ?? null;
}

/**
 * Narrow decorator over the existing PostgresRepository. It keeps legacy reconciliation persistence
 * isolated from the primary commerce repository while sharing the same transaction/client context.
 */
export class LegacyPostgresRepository {
  constructor(base) {
    if (!base || typeof base.database !== 'function' || typeof base.transaction !== 'function') {
      throw new TypeError('A PostgresRepository-compatible base is required.');
    }
    this.base = base;
  }

  transaction(work) { return this.base.transaction(work); }
  findOwnedService(userId, id) { return this.base.findOwnedService(userId, id); }
  saveService(value) { return this.base.saveService(value); }
  createService(value) { return this.base.createService(value); }

  async findUserByTelegramSubject(subject) {
    const result = await this.base.database().query(
      "SELECT id FROM control_users WHERE telegram_subject = $1 AND status = 'active'",
      [subject],
    );
    return result.rowCount === 1 ? { id: result.rows[0].id } : null;
  }

  async findPlanByCode(code) {
    const result = await this.base.database().query(
      'SELECT id, tier FROM control_plans WHERE code = $1 AND active = true',
      [code],
    );
    return result.rowCount === 1 ? { id: result.rows[0].id, tier: result.rows[0].tier } : null;
  }

  async findLegacyReconciliationEvent(sourceKey, eventId) {
    await this.base.advisoryLock('legacy-event', `${sourceKey}:${eventId}`);
    const result = await this.base.database().query(
      `SELECT payload_fingerprint, processing_status, external_service_id, error_code,
              processed_at
         FROM control_legacy_reconciliation_events
        WHERE source_key = $1 AND event_id = $2`,
      [sourceKey, eventId],
    );
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    const projection = row.external_service_id
      ? await this.findLegacyServiceProjection(sourceKey, row.external_service_id)
      : null;
    return {
      payloadFingerprint: row.payload_fingerprint,
      processingStatus: row.processing_status,
      controlServiceId: projection?.controlServiceId ?? null,
      errorCode: row.error_code,
      processedAt: iso(row.processed_at),
    };
  }

  async recordLegacyReconciliationEvent(value) {
    await this.base.database().query(
      `INSERT INTO control_legacy_sources (source_key, source_kind)
       VALUES ($1, 'telegram_bot')
       ON CONFLICT (source_key) DO NOTHING`,
      [value.sourceKey],
    );
    await this.base.database().query(
      `INSERT INTO control_legacy_reconciliation_events
       (source_key, event_id, payload_fingerprint, processing_status, external_service_id, received_at)
       VALUES ($1, $2, $3, 'received', $4, $5)`,
      [value.sourceKey, value.eventId, value.payloadFingerprint, value.externalServiceId, value.receivedAt],
    );
  }

  async completeLegacyReconciliationEvent(value) {
    await this.base.database().query(
      `UPDATE control_legacy_reconciliation_events
          SET processing_status = $3, processed_at = $4, error_code = $5
        WHERE source_key = $1 AND event_id = $2`,
      [value.sourceKey, value.eventId, value.processingStatus, value.processedAt, value.errorCode ?? null],
    );
  }

  async findLegacyCustomerMapping(sourceKey, externalCustomerId) {
    await this.base.advisoryLock('legacy-customer', `${sourceKey}:${externalCustomerId}`);
    const result = await this.base.database().query(
      `SELECT user_id, mapping_basis, updated_at
         FROM control_legacy_customer_mappings
        WHERE source_key = $1 AND external_customer_id = $2`,
      [sourceKey, externalCustomerId],
    );
    return result.rowCount === 1 ? {
      userId: result.rows[0].user_id,
      mappingBasis: result.rows[0].mapping_basis,
      updatedAt: iso(result.rows[0].updated_at),
    } : null;
  }

  async saveLegacyCustomerMapping(value) {
    await this.base.database().query(
      `INSERT INTO control_legacy_customer_mappings
       (source_key, external_customer_id, user_id, mapping_basis, updated_at)
       VALUES ($1, $2, $3, $4, $5)`,
      [value.sourceKey, value.externalCustomerId, value.userId, value.mappingBasis, value.updatedAt],
    );
  }

  async findLegacyServiceProjection(sourceKey, externalServiceId) {
    await this.base.advisoryLock('legacy-service', `${sourceKey}:${externalServiceId}`);
    const result = await this.base.database().query(
      `SELECT external_customer_id, user_id, control_service_id, source_fingerprint,
              source_updated_at, last_seen_at, last_applied_at
         FROM control_legacy_service_projections
        WHERE source_key = $1 AND external_service_id = $2`,
      [sourceKey, externalServiceId],
    );
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    return {
      externalCustomerId: row.external_customer_id,
      userId: row.user_id,
      controlServiceId: row.control_service_id,
      sourceFingerprint: row.source_fingerprint,
      sourceUpdatedAt: iso(row.source_updated_at),
      lastSeenAt: iso(row.last_seen_at),
      lastAppliedAt: iso(row.last_applied_at),
    };
  }

  async touchLegacyServiceProjection(value) {
    await this.base.database().query(
      `UPDATE control_legacy_service_projections
          SET last_seen_at = $3, updated_at = now()
        WHERE source_key = $1 AND external_service_id = $2`,
      [value.sourceKey, value.externalServiceId, value.lastSeenAt],
    );
  }

  async saveLegacyServiceProjection(value) {
    await this.base.database().query(
      `INSERT INTO control_legacy_service_projections
       (source_key, external_service_id, external_customer_id, user_id, control_service_id,
        source_fingerprint, source_updated_at, last_seen_at, last_applied_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,now())
       ON CONFLICT (source_key, external_service_id) DO UPDATE SET
         external_customer_id = EXCLUDED.external_customer_id,
         user_id = EXCLUDED.user_id,
         control_service_id = EXCLUDED.control_service_id,
         source_fingerprint = EXCLUDED.source_fingerprint,
         source_updated_at = EXCLUDED.source_updated_at,
         last_seen_at = EXCLUDED.last_seen_at,
         last_applied_at = EXCLUDED.last_applied_at,
         updated_at = now()`,
      [
        value.sourceKey, value.externalServiceId, value.externalCustomerId, value.userId,
        value.controlServiceId, value.sourceFingerprint, value.sourceUpdatedAt,
        value.lastSeenAt, value.lastAppliedAt,
      ],
    );
  }

  async upsertLegacyConflict(value) {
    await this.base.database().query(
      `INSERT INTO control_legacy_reconciliation_conflicts
       (id, source_key, entity_type, external_entity_id, conflict_code, detail, status, last_seen_at)
       VALUES ($1,$2,$3,$4,$5,$6,'open',$7)
       ON CONFLICT (source_key, entity_type, external_entity_id, conflict_code, status)
       DO UPDATE SET detail = EXCLUDED.detail, last_seen_at = EXCLUDED.last_seen_at`,
      [
        value.id ?? randomUUID(), value.sourceKey, value.entityType, value.externalEntityId,
        value.conflictCode, value.detail ?? {}, value.lastSeenAt,
      ],
    );
  }

  async appendAdminAudit(value) {
    const digest = value.metadata?.external_service_digest ?? null;
    return this.base.appendAdminAudit({
      actorSubject: `${value.actorType}:${value.actorId}`,
      action: value.action,
      resourceType: value.targetType,
      resourceId: value.targetId,
      reason: 'Legacy reconciliation projected normalized entitlement metadata.',
      requestId: randomUUID(),
      beforeDigest: null,
      afterDigest: digest,
      outcome: 'success',
      createdAt: value.occurredAt,
    });
  }
}
