import { createHash, randomUUID } from 'node:crypto';
import { ApiError, failure, success } from './errors.js';

const READ_SCOPE = 'admin:reconciliation:read';
const STATUS = new Set(['open', 'resolved', 'ignored']);

function digest(value) {
  return createHash('sha256').update(String(value ?? '')).digest('hex');
}

function requireReadScope(principal) {
  if (!Array.isArray(principal?.scopes) || !principal.scopes.includes(READ_SCOPE)) {
    throw new ApiError(403, 'admin_reconciliation_scope_required', 'Reconciliation administrator read scope is required.');
  }
}

function safeDetail(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  const allowed = new Set(['mapped_user_id', 'resolved_user_id', 'control_service_id', 'telegram_subject_digest']);
  return Object.fromEntries(Object.entries(value).filter(([key, item]) =>
    allowed.has(key) && (typeof item === 'string' || item == null)));
}

function iso(value) { return value?.toISOString?.() ?? value ?? null; }

export class LegacyAdminReadModel {
  constructor(repository) {
    if (!repository || typeof repository.database !== 'function') {
      throw new TypeError('PostgreSQL repository is required for legacy admin read model.');
    }
    this.repository = repository;
  }

  async listSources() {
    const result = await this.repository.database().query(
      `SELECT s.source_key, s.enabled, s.checkpoint_cursor IS NOT NULL AS checkpoint_present,
              s.last_started_at, s.last_completed_at, s.last_error_code,
              COALESCE(c.open_conflicts, 0)::int AS open_conflicts,
              COALESCE(e.failed_events, 0)::int AS failed_events,
              COALESCE(e.pending_events, 0)::int AS pending_events
         FROM control_legacy_sources s
         LEFT JOIN (
           SELECT source_key, count(*) FILTER (WHERE status = 'open') AS open_conflicts
             FROM control_legacy_reconciliation_conflicts GROUP BY source_key
         ) c ON c.source_key = s.source_key
         LEFT JOIN (
           SELECT source_key,
                  count(*) FILTER (WHERE processing_status = 'failed') AS failed_events,
                  count(*) FILTER (WHERE processing_status = 'received') AS pending_events
             FROM control_legacy_reconciliation_events GROUP BY source_key
         ) e ON e.source_key = s.source_key
        ORDER BY s.source_key`,
    );
    return result.rows.map((row) => ({
      source_key: row.source_key,
      enabled: row.enabled,
      checkpoint_present: row.checkpoint_present,
      last_started_at: iso(row.last_started_at),
      last_completed_at: iso(row.last_completed_at),
      last_error_code: row.last_error_code,
      open_conflicts: Number(row.open_conflicts ?? 0),
      failed_events: Number(row.failed_events ?? 0),
      pending_events: Number(row.pending_events ?? 0),
    }));
  }

  async listConflicts({ status = 'open', sourceKey = null, limit = 100 } = {}) {
    if (!STATUS.has(status)) throw new ApiError(400, 'invalid_conflict_status', 'Conflict status is invalid.');
    if (sourceKey != null && (typeof sourceKey !== 'string' || !/^[A-Za-z0-9._:-]{1,128}$/.test(sourceKey))) {
      throw new ApiError(400, 'invalid_source_key', 'Source key is invalid.');
    }
    const boundedLimit = Number.isSafeInteger(limit) && limit >= 1 && limit <= 200 ? limit : 100;
    const result = await this.repository.database().query(
      `SELECT id, source_key, entity_type, external_entity_id, conflict_code, detail,
              status, first_seen_at, last_seen_at, resolved_at, resolved_by, resolution_note
         FROM control_legacy_reconciliation_conflicts
        WHERE status = $1 AND ($2::text IS NULL OR source_key = $2)
        ORDER BY last_seen_at DESC LIMIT $3`,
      [status, sourceKey, boundedLimit],
    );
    return result.rows.map((row) => ({
      id: row.id,
      source_key: row.source_key,
      entity_type: row.entity_type,
      external_entity_id: row.external_entity_id,
      external_entity_digest: digest(row.external_entity_id),
      conflict_code: row.conflict_code,
      detail: safeDetail(row.detail),
      status: row.status,
      first_seen_at: iso(row.first_seen_at),
      last_seen_at: iso(row.last_seen_at),
      resolved_at: iso(row.resolved_at),
      resolved_by: row.resolved_by,
      resolution_note: row.resolution_note,
    }));
  }
}

export function createLegacyAdminApplication({ baseApplication, auth, readModel, clock = () => new Date() }) {
  if (typeof baseApplication !== 'function') throw new Error('Base application is required.');
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Auth adapter is required.');
  if (!readModel || typeof readModel.listSources !== 'function' || typeof readModel.listConflicts !== 'function') {
    throw new Error('Legacy reconciliation admin read model is required.');
  }

  return async function handle(request) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/v1/admin/reconciliation/')) return baseApplication(request);
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      const principal = await auth.authenticate(request);
      requireReadScope(principal);
      if (request.method === 'GET' && url.pathname === '/v1/admin/reconciliation/sources') {
        return { status: 200, body: success(await readModel.listSources(), requestId, clock) };
      }
      if (request.method === 'GET' && url.pathname === '/v1/admin/reconciliation/conflicts') {
        const rawLimit = url.searchParams.get('limit');
        const limit = rawLimit == null ? 100 : Number(rawLimit);
        const data = await readModel.listConflicts({
          status: url.searchParams.get('status') ?? 'open',
          sourceKey: url.searchParams.get('source'),
          limit,
        });
        return { status: 200, body: success(data, requestId, clock) };
      }
      throw new ApiError(404, 'admin_reconciliation_route_not_found', 'Reconciliation admin route was not found.');
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
