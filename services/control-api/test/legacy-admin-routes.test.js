import assert from 'node:assert/strict';
import test from 'node:test';
import { LegacyAdminReadModel, createLegacyAdminApplication } from '../src/legacy-admin-routes.js';

function rowResult(rows) { return { rows, rowCount: rows.length }; }

function repository() {
  const calls = [];
  return {
    calls,
    database() {
      return {
        async query(sql, params = []) {
          calls.push({ sql, params });
          if (sql.includes('FROM control_legacy_sources s')) {
            return rowResult([{
              source_key: 'ganj-bot-primary', enabled: true, checkpoint_present: true,
              last_started_at: new Date('2026-08-28T12:00:00Z'), last_completed_at: new Date('2026-08-28T12:00:03Z'),
              last_error_code: null, open_conflicts: 2, failed_events: 1, pending_events: 0,
            }]);
          }
          return rowResult([{
            id: '70000000-0000-4000-8000-000000000001', source_key: 'ganj-bot-primary', entity_type: 'plan',
            external_entity_id: 'legacy-product-9', conflict_code: 'legacy_plan_mapping_missing',
            detail: { control_service_id: null, password: 'must-not-leak' }, status: 'open',
            first_seen_at: new Date('2026-08-28T11:00:00Z'), last_seen_at: new Date('2026-08-28T12:00:00Z'),
            resolved_at: null, resolved_by: null, resolution_note: null,
          }]);
        },
      };
    },
  };
}

test('legacy admin source status hides opaque checkpoint and returns operational counts', async () => {
  const model = new LegacyAdminReadModel(repository());
  const values = await model.listSources();
  assert.deepEqual(values, [{
    source_key: 'ganj-bot-primary', enabled: true, checkpoint_present: true,
    last_started_at: '2026-08-28T12:00:00.000Z', last_completed_at: '2026-08-28T12:00:03.000Z',
    last_error_code: null, open_conflicts: 2, failed_events: 1, pending_events: 0,
  }]);
  assert.equal(JSON.stringify(values).includes('checkpoint_cursor'), false);
});

test('legacy admin conflicts preserve operator identity key but sanitize detail fields', async () => {
  const model = new LegacyAdminReadModel(repository());
  const values = await model.listConflicts({ status: 'open', sourceKey: 'ganj-bot-primary', limit: 50 });
  assert.equal(values.length, 1);
  assert.equal(values[0].external_entity_id, 'legacy-product-9');
  assert.equal(values[0].external_entity_digest.length, 64);
  assert.deepEqual(values[0].detail, { control_service_id: null });
  assert.equal(JSON.stringify(values).includes('must-not-leak'), false);
});

test('legacy admin HTTP routes require dedicated reconciliation scope', async () => {
  const model = new LegacyAdminReadModel(repository());
  const baseApplication = async () => ({ status: 418, body: {} });
  const auth = {
    async authenticate(request) {
      return { userId: '10000000-0000-4000-8000-000000000001', deviceId: '20000000-0000-4000-8000-000000000001', scopes: (request.headers.get('x-scopes') ?? '').split(' ').filter(Boolean) };
    },
  };
  const app = createLegacyAdminApplication({ baseApplication, auth, readModel: model, clock: () => new Date('2026-08-28T12:00:00Z') });
  const denied = await app(new Request('https://control.test/v1/admin/reconciliation/sources'));
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'admin_reconciliation_scope_required');
  const allowed = await app(new Request('https://control.test/v1/admin/reconciliation/sources', { headers: { 'x-scopes': 'admin:reconciliation:read' } }));
  assert.equal(allowed.status, 200);
  assert.equal(allowed.body.data[0].open_conflicts, 2);
  const delegated = await app(new Request('https://control.test/v1/services'));
  assert.equal(delegated.status, 418);
});

test('legacy admin conflict filters reject invalid status/source instead of interpolating SQL', async () => {
  const model = new LegacyAdminReadModel(repository());
  await assert.rejects(model.listConflicts({ status: 'anything' }), (error) => error.code === 'invalid_conflict_status');
  await assert.rejects(model.listConflicts({ sourceKey: "x' OR 1=1--" }), (error) => error.code === 'invalid_source_key');
});
