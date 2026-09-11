const RETRYABLE_CONFLICTS = new Set([
  'legacy_customer_unlinked',
  'legacy_plan_mapping_missing',
]);

function key(sourceKey, eventId) {
  return `${sourceKey}\u0000${eventId}`;
}

/**
 * Previously-seen ownership events that failed only because an account or plan was not linked yet
 * must be eligible for deterministic replay after the missing mapping is created. Other conflicts
 * remain immutable replays and still require explicit admin review.
 */
export function withRetryableLegacyConflicts(repository) {
  const retrying = new Set();
  return new Proxy(repository, {
    get(target, property, receiver) {
      if (property === 'findLegacyReconciliationEvent') {
        return async (sourceKey, eventId) => {
          const prior = await target.findLegacyReconciliationEvent(sourceKey, eventId);
          if (prior?.processingStatus === 'conflict' && RETRYABLE_CONFLICTS.has(prior.errorCode)) {
            retrying.add(key(sourceKey, eventId));
            return null;
          }
          return prior;
        };
      }
      if (property === 'recordLegacyReconciliationEvent') {
        return async (value) => {
          const retryKey = key(value.sourceKey, value.eventId);
          if (!retrying.delete(retryKey)) return target.recordLegacyReconciliationEvent(value);
          await target.base.database().query(
            `UPDATE control_legacy_reconciliation_events
                SET payload_fingerprint=$3, processing_status='received', external_service_id=$4,
                    received_at=$5, processed_at=NULL, error_code=NULL
              WHERE source_key=$1 AND event_id=$2`,
            [value.sourceKey, value.eventId, value.payloadFingerprint, value.externalServiceId, value.receivedAt],
          );
        };
      }
      const value = Reflect.get(target, property, receiver);
      return typeof value === 'function' ? value.bind(target) : value;
    },
  });
}
