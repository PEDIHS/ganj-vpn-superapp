import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required for migrations.');
const { Pool } = await import('pg');
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 1,
  connectionTimeoutMillis: 5_000,
  ssl: process.env.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
  application_name: 'ganj-vpn-control-api-migrator',
});
const migrationsDirectory = join(dirname(fileURLToPath(import.meta.url)), '..', 'migrations');
const includeTestSeed = process.env.MIGRATIONS_INCLUDE_TEST_SEED === 'true';
const files = (await readdir(migrationsDirectory))
  .filter((name) => /^\d{3}_[a-z0-9_]+\.sql$/.test(name))
  .filter((name) => includeTestSeed || !name.includes('_test'))
  .sort();

const client = await pool.connect();
try {
  await client.query("SELECT pg_advisory_lock(hashtextextended('ganj-vpn-control-api-migrations', 0))");
  await client.query(`CREATE TABLE IF NOT EXISTS control_api_migrations (
    name text PRIMARY KEY,
    checksum text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
  )`);
  for (const name of files) {
    const sql = await readFile(join(migrationsDirectory, name), 'utf8');
    const checksum = createHash('sha256').update(sql).digest('hex');
    const existing = await client.query('SELECT checksum FROM control_api_migrations WHERE name = $1', [name]);
    if (existing.rowCount === 1) {
      if (existing.rows[0].checksum !== checksum) throw new Error(`Applied migration checksum changed: ${name}`);
      continue;
    }
    await client.query('BEGIN');
    try {
      await client.query(sql);
      await client.query('INSERT INTO control_api_migrations (name, checksum) VALUES ($1, $2)', [name, checksum]);
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }
  }
} finally {
  try { await client.query("SELECT pg_advisory_unlock(hashtextextended('ganj-vpn-control-api-migrations', 0))"); } catch {}
  client.release();
  await pool.end();
}
