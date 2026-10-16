import test from 'node:test';
import assert from 'node:assert/strict';
import { PostgresRepository } from '../src/adapters/postgres.js';

function scenario({ replaceable = true, bound = 'old', deviceActive = true } = {}) {
  const events = [];
  let binding = bound;
  const db = {
    async query(sql, values = []) {
      events.push(sql);
      if (sql.includes('FROM control_services WHERE id=$1 FOR UPDATE')) return { rowCount: 1, rows: [{ id: 'service' }] };
      if (sql.includes('FROM control_devices WHERE id=$1')) return { rowCount: deviceActive ? 1 : 0, rows: [] };
      if (sql.includes('FROM control_service_devices WHERE service_id=$1 AND device_id=$2 AND user_id=$3')) {
        return { rowCount: binding === values[1] ? 1 : 0, rows: [] };
      }
      if (sql.includes('SELECT 1 FROM control_service_devices WHERE service_id=$1 AND user_id=$2')) {
        return { rowCount: binding ? 1 : 0, rows: [] };
      }
      if (sql.includes('AS used')) return { rowCount: 1, rows: [{ used: 1 }] };
      if (sql.includes('JOIN control_legacy_service_projections')) return { rowCount: replaceable ? 1 : 0, rows: [] };
      if (sql.includes('DELETE FROM control_connection_profile_grants')) return { rowCount: 1, rows: [] };
      if (sql.includes('DELETE FROM control_service_devices')) { binding = null; return { rowCount: 1, rows: [] }; }
      if (sql.includes('INSERT INTO control_service_devices')) { binding = values[1]; return { rowCount: 1, rows: [] }; }
      throw new Error('Unexpected SQL: ' + sql);
    },
  };
  const pool = { query: db.query, connect: async () => db };
  const repo = new PostgresRepository({pool, serverSecretResolver:{resolveServerConnection: async()=>null}});
  const principal = { userId: 'user', deviceId: 'new' };
  const service = { id: 'service', userId: 'user', device_limit: 1, deviceIds: bound ? [bound] : [] };
  const bind = () => repo.context.run(db, () => repo.bindAccessibleDevice(principal, service));
  return { bind, events, get binding() {return binding;} };
}

test('linked owner safely hands over one existing legacy app slot', async () => {
  const fixture = scenario();
  await fixture.bind();
  assert.equal(fixture.binding, 'new');
  assert.ok(fixture.events.some(sql => sql.includes('DELETE FROM control_connection_profile_grants')));
  assert.ok(fixture.events.some(sql => sql.includes('DELETE FROM control_service_devices')));
  assert.ok(fixture.events.some(sql => sql.includes('FOR UPDATE')));
});

test('non-legacy, shared or unlinked owners never get a silent slot bypass', async () => {
  const fixture = scenario({replaceable:false});
  await assert.rejects(fixture.bind(), error => error.code === 'device_limit_reached');
  assert.equal(fixture.binding, 'old');
  assert.ok(!fixture.events.some(sql => sql.includes('DELETE FROM control_service_devices')));
});

test('already-bound devices are idempotent', async () => {
  const fixture = scenario({bound:'new'});
  await fixture.bind();
  assert.equal(fixture.binding, 'new');
  assert.ok(!fixture.events.some(sql => sql.includes('DELETE FROM control_service_devices')));
});

test('revoked or untrusted devices cannot take over a legacy slot', async () => {
  const fixture = scenario({deviceActive:false});
  await assert.rejects(fixture.bind(), error => error.code === 'device_not_trusted');
  assert.equal(fixture.binding, 'old');
});
