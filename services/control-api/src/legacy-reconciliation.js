import { createHash, randomUUID } from 'node:crypto';
import { ApiError } from './errors.js';

const ALLOWED_STATUSES = new Set(['pending', 'active', 'disabled', 'expired', 'revoked']);
const ALLOWED_PROTOCOLS = new Set(['vless', 'vmess', 'trojan', 'shadowsocks']);

function requiredString(value, field, max = 512) {
  if (typeof value !== 'string' || value.length < 1 || value.length > max) {
    throw new ApiError(400, 'legacy_payload_invalid', `${field} is invalid.`);
  }
  return value;
}

function optionalIso(value, field) {
  if (value == null) return null;
  if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
    throw new ApiError(400, 'legacy_payload_invalid', `${field} must be an ISO timestamp.`);
  }
  return new Date(value).toISOString();
}

function nonNegativeInteger(value, field, { nullable = false } = {}) {
  if (nullable && value == null) return null;
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new ApiError(400, 'legacy_payload_invalid', `${field} must be a non-negative integer.`);
  }
  return value;
}

function positiveInteger(value, field) {
  if (!Number.isSafeInteger(value) || value < 1 || value > 1000) {
    throw new ApiError(400, 'legacy_payload_invalid', `${field} must be a positive integer.`);
  }
  return value;
}

function normalizeProtocols(value) {
  if (!Array.isArray(value) || value.length < 1 || value.length > 8) {
    throw new ApiError(400, 'legacy_payload_invalid', 'allowed_protocols is invalid.');
  }
  const protocols = [...new Set(value.map((item) => requiredString(item, 'allowed_protocols', 32).toLowerCase()))];
  if (protocols.some((item) => !ALLOWED_PROTOCOLS.has(item))) {
    throw new ApiError(400, 'legacy_payload_invalid', 'allowed_protocols contains an unsupported protocol.');
  }
  return protocols.sort();
}

function normalizeStatus(status, expiresAt, now) {
  const normalized = requiredString(status, 'status', 32).toLowerCase();
  if (!ALLOWED_STATUSES.has(normalized)) {
    throw new ApiError(400, 'legacy_payload_invalid', 'status is unsupported.');
  }
  if (normalized === 'active' && expiresAt && Date.parse(expiresAt) <= now.getTime()) return 'expired';
  return normalized;
}

function stableFingerprint(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

export function normalizeLegacySubscriptionEvent(input, now = new Date()) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    throw new ApiError(400, 'legacy_payload_invalid', 'Legacy event must be an object.');
  }

  const sourceKey = requiredString(input.source_key, 'source_key', 128);
  const eventId = requiredString(input.event_id, 'event_id', 256);
  const externalCustomerId = requiredString(input.external_customer_id, 'external_customer_id', 256);
  const telegramSubject = requiredString(input.telegram_subject, 'telegram_subject', 256);
  const externalServiceId = requiredString(input.external_service_id, 'external_service_id', 256);
  const planCode = requiredString(input.plan_code, 'plan_code', 128);
  const expiresAt = optionalIso(input.expires_at, 'expires_at');
  const sourceUpdatedAt = optionalIso(input.source_updated_at, 'source_updated_at');
  const normalized = {
    sourceKey,
    eventId,
    externalCustomerId,
    telegramSubject,
    externalServiceId,
    planCode,
    displayName: requiredString(input.display_name, 'display_name', 160),
    status: normalizeStatus(input.status, expiresAt, now),
    expiresAt,
    trafficLimitBytes: nonNegativeInteger(input.traffic_limit_bytes, 'traffic_limit_bytes', { nullable: true }),
    trafficUsedBytes: nonNegativeInteger(input.traffic_used_bytes ?? 0, 'traffic_used_bytes'),
    deviceLimit: positiveInteger(input.device_limit ?? 1, 'device_limit'),
    allowedProtocols: normalizeProtocols(input.allowed_protocols),
    sourceUpdatedAt,
  };

  if (normalized.trafficLimitBytes != null && normalized.trafficUsedBytes > normalized.trafficLimitBytes) {
    normalized.trafficUsedBytes = normalized.trafficLimitBytes;
  }

  const fingerprintPayload = {
    externalCustomerId: normalized.externalCustomerId,
    telegramSubject: normalized.telegramSubject,
    externalServiceId: normalized.externalServiceId,
    planCode: normalized.planCode,
    displayName: normalized.displayName,
    status: normalized.status,
    expiresAt: normalized.expiresAt,
    trafficLimitBytes: normalized.trafficLimitBytes,
    trafficUsedBytes: normalized.trafficUsedBytes,
    deviceLimit: normalized.deviceLimit,
    allowedProtocols: normalized.allowedProtocols,
    sourceUpdatedAt: normalized.sourceUpdatedAt,
  };

  return Object.freeze({
    ...normalized,
    fingerprint: stableFingerprint(fingerprintPayload),
    payloadFingerprint: stableFingerprint({ sourceKey, eventId, ...fingerprintPayload }),
  });
}

export class LegacySubscriptionReconciler {
  constructor({ repository, now = () => new Date(), ids = () => randomUUID() }) {
    if (!repository) throw new TypeError('repository is required');
    this.repository = repository;
    this.now = now;
    this.ids = ids;
  }

  async reconcile(input) {
    const now = this.now();
    const event = normalizeLegacySubscriptionEvent(input, now);

    return this.repository.transaction(async () => {
      const priorEvent = await this.repository.findLegacyReconciliationEvent(event.sourceKey, event.eventId);
      if (priorEvent) {
        if (priorEvent.payloadFingerprint !== event.payloadFingerprint) {
          throw new ApiError(409, 'legacy_event_replay_conflict', 'Legacy event ID was reused with different content.');
        }
        return { outcome: priorEvent.processingStatus, replay: true, serviceId: priorEvent.controlServiceId ?? null };
      }

      await this.repository.recordLegacyReconciliationEvent({
        sourceKey: event.sourceKey,
        eventId: event.eventId,
        payloadFingerprint: event.payloadFingerprint,
        externalServiceId: event.externalServiceId,
        receivedAt: now.toISOString(),
      });

      const user = await this.repository.findUserByTelegramSubject(event.telegramSubject);
      if (!user) {
        return this.#conflict(event, 'customer', event.externalCustomerId, 'legacy_customer_unlinked', {
          telegram_subject_digest: stableFingerprint(event.telegramSubject),
        }, now);
      }

      const customerMapping = await this.repository.findLegacyCustomerMapping(event.sourceKey, event.externalCustomerId);
      if (customerMapping && customerMapping.userId !== user.id) {
        return this.#conflict(event, 'customer', event.externalCustomerId, 'legacy_customer_owner_conflict', {
          mapped_user_id: customerMapping.userId,
          resolved_user_id: user.id,
        }, now);
      }
      if (!customerMapping) {
        await this.repository.saveLegacyCustomerMapping({
          sourceKey: event.sourceKey,
          externalCustomerId: event.externalCustomerId,
          userId: user.id,
          mappingBasis: 'telegram_subject',
          updatedAt: now.toISOString(),
        });
      }

      const plan = await this.repository.findPlanByCode(event.planCode);
      if (!plan) {
        return this.#conflict(event, 'plan', event.planCode, 'legacy_plan_mapping_missing', {}, now);
      }

      const projection = await this.repository.findLegacyServiceProjection(event.sourceKey, event.externalServiceId);
      if (projection && projection.userId !== user.id) {
        return this.#conflict(event, 'service', event.externalServiceId, 'legacy_service_owner_conflict', {
          mapped_user_id: projection.userId,
          resolved_user_id: user.id,
        }, now);
      }

      if (projection?.sourceFingerprint === event.fingerprint) {
        await this.repository.touchLegacyServiceProjection({
          sourceKey: event.sourceKey,
          externalServiceId: event.externalServiceId,
          lastSeenAt: now.toISOString(),
        });
        await this.repository.completeLegacyReconciliationEvent({
          sourceKey: event.sourceKey,
          eventId: event.eventId,
          processingStatus: 'unchanged',
          controlServiceId: projection.controlServiceId,
          processedAt: now.toISOString(),
        });
        return { outcome: 'unchanged', replay: false, serviceId: projection.controlServiceId };
      }

      let service;
      if (projection) {
        service = await this.repository.findOwnedService(user.id, projection.controlServiceId);
        if (!service) {
          return this.#conflict(event, 'service', event.externalServiceId, 'legacy_projection_orphaned', {
            control_service_id: projection.controlServiceId,
          }, now);
        }
      } else {
        service = { id: this.ids(), userId: user.id };
      }

      const nextService = {
        ...service,
        userId: user.id,
        planId: plan.id,
        name: event.displayName,
        status: event.status,
        tier: plan.tier,
        country_code: null,
        traffic_limit_bytes: event.trafficLimitBytes,
        traffic_used_bytes: event.trafficUsedBytes,
        expires_at: event.expiresAt,
        device_limit: event.deviceLimit,
        allowed_protocols: event.allowedProtocols,
        deviceIds: service.deviceIds ?? [],
      };

      const stored = projection
        ? await this.repository.saveService(nextService)
        : await this.repository.createService(nextService);

      await this.repository.saveLegacyServiceProjection({
        sourceKey: event.sourceKey,
        externalServiceId: event.externalServiceId,
        externalCustomerId: event.externalCustomerId,
        userId: user.id,
        controlServiceId: stored.id,
        sourceFingerprint: event.fingerprint,
        sourceUpdatedAt: event.sourceUpdatedAt,
        lastSeenAt: now.toISOString(),
        lastAppliedAt: now.toISOString(),
      });

      await this.repository.appendAdminAudit({
        actorType: 'system',
        actorId: 'legacy-reconciler',
        action: projection ? 'legacy_service_updated' : 'legacy_service_created',
        targetType: 'service',
        targetId: stored.id,
        metadata: {
          source_key: event.sourceKey,
          external_service_digest: stableFingerprint(event.externalServiceId),
        },
        occurredAt: now.toISOString(),
      });

      await this.repository.completeLegacyReconciliationEvent({
        sourceKey: event.sourceKey,
        eventId: event.eventId,
        processingStatus: 'applied',
        controlServiceId: stored.id,
        processedAt: now.toISOString(),
      });
      return { outcome: 'applied', replay: false, serviceId: stored.id };
    });
  }

  async #conflict(event, entityType, externalEntityId, conflictCode, detail, now) {
    await this.repository.upsertLegacyConflict({
      id: this.ids(),
      sourceKey: event.sourceKey,
      entityType,
      externalEntityId,
      conflictCode,
      detail,
      lastSeenAt: now.toISOString(),
    });
    await this.repository.completeLegacyReconciliationEvent({
      sourceKey: event.sourceKey,
      eventId: event.eventId,
      processingStatus: 'conflict',
      errorCode: conflictCode,
      processedAt: now.toISOString(),
    });
    return { outcome: 'conflict', replay: false, conflictCode, serviceId: null };
  }
}
