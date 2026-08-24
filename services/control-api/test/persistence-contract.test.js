import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { PostgresRepository } from '../src/adapters/postgres.js';

test('PostgreSQL migration encodes ownership, replay, and no-raw-secret invariants', async () => {
  const sql = await readFile(new URL('../migrations/001_control_api.sql', import.meta.url), 'utf8');
  assert.match(sql, /FOREIGN KEY \(service_id, user_id\) REFERENCES control_services\(id, user_id\)/);
  assert.match(sql, /FOREIGN KEY \(device_id, user_id\) REFERENCES control_devices\(id, user_id\)/);
  assert.match(sql, /PRIMARY KEY \(scope, user_id, idempotency_key\)/);
  assert.match(sql, /token_digest text PRIMARY KEY/);
  assert.match(sql, /UNIQUE \(device_id, client_nonce_digest\)/);
  assert.match(sql, /consumed_at timestamptz/);
  assert.match(sql, /PRIMARY KEY \(provider, event_id\)/);
  assert.match(sql, /secret_ref text NOT NULL/);
  assert.doesNotMatch(sql, /purchase_token\s+text/i);
  assert.doesNotMatch(sql, /server_credential\s+text/i);
});

test('repository transaction commits, rolls back, releases, and supports nesting', async () => {
  const statements = [];
  let releases = 0;
  const client = {
    async query(statement) { statements.push(statement); return { rowCount: 0, rows: [] }; },
    release() { releases += 1; },
  };
  const pool = {
    async connect() { return client; },
    async query() { throw new Error('transaction must use the checked-out client'); },
    async end() {},
  };
  const repository = new PostgresRepository({
    pool,
    serverSecretResolver: { async resolveServerConnection() { throw new Error('not used'); } },
  });
  const value = await repository.transaction(async () => repository.transaction(async () => 'committed'));
  assert.equal(value, 'committed');
  assert.equal(statements.filter((item) => item === 'BEGIN').length, 1);
  assert.equal(statements.at(-1), 'COMMIT');
  assert.equal(releases, 1);

  statements.length = 0;
  await assert.rejects(() => repository.transaction(async () => { throw new Error('rollback-test'); }), /rollback-test/);
  assert.equal(statements.at(-1), 'ROLLBACK');
  assert.equal(releases, 2);
});

test('integration compose uses an ephemeral PostgreSQL data directory', async () => {
  const compose = await readFile(new URL('../docker-compose.integration.yml', import.meta.url), 'utf8');
  assert.match(compose, /postgres:16-alpine/);
  assert.match(compose, /tmpfs:/);
  assert.match(compose, /test:postgres/);
});
