import { lstat, readFile } from 'node:fs/promises';
import { setTimeout as sleep } from 'node:timers/promises';
import { createDataAdapter } from './adapters/postgres.js';
import { LegacyPostgresRepository } from './adapters/legacy-postgres.js';
import { createGanjBotProjectionSource } from './adapters/ganj-bot-projection-source.js';
import { LegacySubscriptionReconciler } from './legacy-reconciliation.js';
import { LegacyReconciliationWorker } from './legacy-worker.js';
import { ControlApiUpstreamBindingSink, LegacyProjectionPipeline } from './legacy-upstream-binding.js';

async function privateToken(path, name) {
  if (!path) throw new Error(`${name} is required.`);
  const info = await lstat(path);
  if (!info.isFile() || info.isSymbolicLink() || (info.mode & 0o077) !== 0) {
    throw new Error(`${name} must reference a private regular file.`);
  }
  const token = (await readFile(path, 'utf8')).trim();
  if (Buffer.byteLength(token, 'utf8') < 32 || token.length > 4096) throw new Error(`${name} is invalid.`);
  return token;
}

function positiveInteger(value, fallback, maximum) {
  const parsed = Number(value ?? fallback);
  return Number.isSafeInteger(parsed) && parsed >= 1 && parsed <= maximum ? parsed : fallback;
}

async function createBindingSink(environment) {
  if (!environment.LEGACY_UPSTREAM_BINDING_URL && !environment.LEGACY_UPSTREAM_BINDING_TOKEN_FILE) return null;
  if (!environment.LEGACY_UPSTREAM_BINDING_URL || !environment.LEGACY_UPSTREAM_BINDING_TOKEN_FILE) {
    throw new Error('Both LEGACY_UPSTREAM_BINDING_URL and LEGACY_UPSTREAM_BINDING_TOKEN_FILE are required together.');
  }
  return new ControlApiUpstreamBindingSink({
    endpoint: environment.LEGACY_UPSTREAM_BINDING_URL,
    bearerToken: await privateToken(environment.LEGACY_UPSTREAM_BINDING_TOKEN_FILE, 'LEGACY_UPSTREAM_BINDING_TOKEN_FILE'),
    timeoutMillis: positiveInteger(environment.LEGACY_UPSTREAM_BINDING_TIMEOUT_MS, 4_000, 15_000),
  });
}

export async function createLegacyRunner(environment = process.env) {
  const sourceKey = environment.LEGACY_SOURCE_KEY ?? 'ganj-bot-primary';
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(sourceKey)) throw new Error('LEGACY_SOURCE_KEY is invalid.');
  const base = await createDataAdapter({ environment });
  try {
    const repository = new LegacyPostgresRepository(base);
    const source = await createGanjBotProjectionSource({ environment });
    const core = new LegacySubscriptionReconciler({ repository });
    const reconciler = new LegacyProjectionPipeline({
      reconciler: core,
      bindingSink: await createBindingSink(environment),
    });
    const worker = new LegacyReconciliationWorker({
      sourceKey,
      source,
      reconciler,
      repository,
      batchSize: positiveInteger(environment.LEGACY_SYNC_BATCH_SIZE, 100, 500),
      maxAttempts: positiveInteger(environment.LEGACY_SYNC_MAX_ATTEMPTS, 3, 8),
      baseRetryMillis: positiveInteger(environment.LEGACY_SYNC_RETRY_BASE_MS, 250, 30_000),
    });
    return {
      sourceKey,
      worker,
      async close() { await base.close?.(); },
    };
  } catch (error) {
    await base.close?.();
    throw error;
  }
}

function safeSummary(pages) {
  return pages.reduce((total, page) => ({
    pages: total.pages + 1,
    received: total.received + page.received,
    applied: total.applied + page.applied,
    unchanged: total.unchanged + page.unchanged,
    conflict: total.conflict + page.conflict,
    replay: total.replay + page.replay,
    failed: total.failed + page.failed,
    caught_up: !page.hasMore,
  }), { pages: 0, received: 0, applied: 0, unchanged: 0, conflict: 0, replay: 0, failed: 0, caught_up: true });
}

export async function runLegacyReconciliation(environment = process.env) {
  const runtime = await createLegacyRunner(environment);
  const maximumPages = positiveInteger(environment.LEGACY_SYNC_MAX_PAGES, 20, 1_000);
  try {
    const pages = await runtime.worker.runUntilCaughtUp({ maxPages: maximumPages });
    const summary = safeSummary(pages);
    console.info(JSON.stringify({ event: 'legacy_reconciliation_completed', source: runtime.sourceKey, ...summary }));
    return summary;
  } finally {
    await runtime.close();
  }
}

async function main(environment) {
  const mode = environment.LEGACY_SYNC_MODE ?? 'once';
  if (!['once', 'loop'].includes(mode)) throw new Error('LEGACY_SYNC_MODE must be once or loop.');
  if (mode === 'once') {
    await runLegacyReconciliation(environment);
    return;
  }
  const intervalSeconds = positiveInteger(environment.LEGACY_SYNC_INTERVAL_SECONDS, 300, 86_400);
  if (intervalSeconds < 60) throw new Error('LEGACY_SYNC_INTERVAL_SECONDS must be at least 60 seconds.');
  let stopping = false;
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { stopping = true; });
  while (!stopping) {
    try {
      await runLegacyReconciliation(environment);
    } catch (error) {
      console.error(JSON.stringify({
        event: 'legacy_reconciliation_failed',
        code: String(error?.code ?? error?.name ?? 'legacy_runner_error').replace(/[^A-Za-z0-9_.-]/g, '_').slice(0, 128),
      }));
    }
    if (!stopping) await sleep(intervalSeconds * 1_000);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main(process.env).catch((error) => {
    console.error(JSON.stringify({
      event: 'legacy_reconciliation_fatal',
      code: String(error?.code ?? error?.name ?? 'legacy_runner_error').replace(/[^A-Za-z0-9_.-]/g, '_').slice(0, 128),
    }));
    process.exitCode = 1;
  });
}
