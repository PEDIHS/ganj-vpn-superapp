import assert from 'node:assert/strict';
import { mkdtemp, writeFile, chmod, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { loadProductionEnvironment } from '../src/production-environment.js';

test('database secret files are private, unambiguous and do not mutate the source', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'ganj-db-secret-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const file = join(root, 'database-url');
  const value = 'postgresql://test:secret@127.0.0.1/test';
  await writeFile(file, value + '\n', { mode: 0o600 });
  const source = { DATABASE_URL_FILE: file };
  const loaded = await loadProductionEnvironment(source);
  assert.equal(loaded.DATABASE_URL, value);
  assert.equal(loaded.DATABASE_URL_FILE, undefined);
  assert.equal(source.DATABASE_URL, undefined);
  await assert.rejects(loadProductionEnvironment({ ...source, DATABASE_URL: value }), /cannot both/);
  await chmod(file, 0o644);
  await assert.rejects(loadProductionEnvironment(source), /permissions/);
});

test('production migrations refuse test seeds before opening a database', () => {
  const result = spawnSync(process.execPath, ['src/migrate.js'], {
    env: { PATH: process.env.PATH, NODE_ENV: 'production', DATABASE_URL: 'postgresql://invalid', MIGRATIONS_INCLUDE_TEST_SEED: 'true' },
    encoding: 'utf8',
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Test seed migrations are forbidden in production/);
});
