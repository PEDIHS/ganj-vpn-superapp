import { setTimeout as sleep } from 'node:timers/promises';

function positiveInt(value, fallback, max) {
  const parsed = Number(value ?? fallback);
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > max) return fallback;
  return parsed;
}

/**
 * A source adapter returns normalized legacy entitlement records only. Raw VPN configuration,
 * credentials or subscription URLs are outside this interface by design.
 *
 * pullPage({ cursor, limit }) => { items: object[], nextCursor: string|null, hasMore: boolean }
 */
export class LegacyReconciliationWorker {
  constructor({
    sourceKey,
    source,
    reconciler,
    repository,
    batchSize = 100,
    maxAttempts = 3,
    baseRetryMillis = 250,
    sleeper = (millis) => sleep(millis),
    now = () => new Date(),
  }) {
    if (typeof sourceKey !== 'string' || sourceKey.length < 1 || sourceKey.length > 128) {
      throw new TypeError('sourceKey is invalid');
    }
    if (!source || typeof source.pullPage !== 'function') throw new TypeError('legacy source adapter is required');
    if (!reconciler || typeof reconciler.reconcile !== 'function') throw new TypeError('reconciler is required');
    if (!repository) throw new TypeError('legacy repository is required');
    this.sourceKey = sourceKey;
    this.source = source;
    this.reconciler = reconciler;
    this.repository = repository;
    this.batchSize = positiveInt(batchSize, 100, 500);
    this.maxAttempts = positiveInt(maxAttempts, 3, 8);
    this.baseRetryMillis = positiveInt(baseRetryMillis, 250, 30_000);
    this.sleeper = sleeper;
    this.now = now;
  }

  async runOnce() {
    const startedAt = this.now().toISOString();
    const state = await this.repository.beginLegacySourceRun({
      sourceKey: this.sourceKey,
      startedAt,
    });
    const cursor = state?.checkpointCursor ?? null;

    let page;
    try {
      page = await this.#retry(() => this.source.pullPage({ cursor, limit: this.batchSize }));
      this.#validatePage(page);
    } catch (error) {
      await this.repository.failLegacySourceRun({
        sourceKey: this.sourceKey,
        errorCode: this.#safeErrorCode(error),
        completedAt: this.now().toISOString(),
      });
      throw error;
    }

    const summary = {
      sourceKey: this.sourceKey,
      cursor,
      nextCursor: page.nextCursor ?? cursor,
      received: page.items.length,
      applied: 0,
      unchanged: 0,
      conflict: 0,
      replay: 0,
      failed: 0,
      hasMore: page.hasMore,
    };

    for (const item of page.items) {
      try {
        const result = await this.#retry(() => this.reconciler.reconcile({
          ...item,
          source_key: this.sourceKey,
        }));
        if (result.replay) summary.replay += 1;
        else if (result.outcome === 'applied') summary.applied += 1;
        else if (result.outcome === 'unchanged') summary.unchanged += 1;
        else if (result.outcome === 'conflict') summary.conflict += 1;
      } catch (error) {
        summary.failed += 1;
        await this.repository.recordLegacyWorkerFailure({
          sourceKey: this.sourceKey,
          eventId: typeof item?.event_id === 'string' ? item.event_id : null,
          errorCode: this.#safeErrorCode(error),
          failedAt: this.now().toISOString(),
        });
      }
    }

    // Advance the checkpoint only after the entire page has been attempted. Individual failures are
    // durable in the event/failure ledger and can be replayed independently without duplicating grants.
    await this.repository.completeLegacySourceRun({
      sourceKey: this.sourceKey,
      checkpointCursor: page.nextCursor ?? cursor,
      completedAt: this.now().toISOString(),
      clearError: summary.failed === 0,
    });
    return summary;
  }

  async runUntilCaughtUp({ maxPages = 20 } = {}) {
    const limit = positiveInt(maxPages, 20, 1000);
    const pages = [];
    for (let index = 0; index < limit; index += 1) {
      const summary = await this.runOnce();
      pages.push(summary);
      if (!summary.hasMore) break;
      if (index === limit - 1) throw new Error('legacy_reconciliation_page_limit_reached');
    }
    return pages;
  }

  async #retry(operation) {
    let lastError;
    for (let attempt = 1; attempt <= this.maxAttempts; attempt += 1) {
      try {
        return await operation();
      } catch (error) {
        lastError = error;
        if (!this.#retryable(error) || attempt === this.maxAttempts) throw error;
        const delay = Math.min(this.baseRetryMillis * (2 ** (attempt - 1)), 30_000);
        await this.sleeper(delay);
      }
    }
    throw lastError;
  }

  #retryable(error) {
    if (error?.retryable === true) return true;
    if (typeof error?.status === 'number') return error.status === 429 || error.status >= 500;
    return error?.code === 'ETIMEDOUT' || error?.code === 'ECONNRESET' || error?.code === 'ECONNREFUSED';
  }

  #safeErrorCode(error) {
    const value = error?.code ?? error?.name ?? 'legacy_worker_error';
    return String(value).replace(/[^a-zA-Z0-9_.-]/g, '_').slice(0, 128);
  }

  #validatePage(page) {
    if (!page || typeof page !== 'object' || !Array.isArray(page.items)) {
      throw new TypeError('Legacy source returned an invalid page.');
    }
    if (page.items.length > this.batchSize) throw new TypeError('Legacy source exceeded requested page size.');
    if (typeof page.hasMore !== 'boolean') throw new TypeError('Legacy source page hasMore is invalid.');
    if (page.nextCursor != null && (typeof page.nextCursor !== 'string' || page.nextCursor.length > 4096)) {
      throw new TypeError('Legacy source nextCursor is invalid.');
    }
    if (page.hasMore && (!page.nextCursor || page.nextCursor === '')) {
      throw new TypeError('Legacy source must return nextCursor when hasMore=true.');
    }
  }
}
