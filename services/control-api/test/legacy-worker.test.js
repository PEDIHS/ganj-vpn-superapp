import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../src/errors.js';
import { LegacyReconciliationWorker } from '../src/legacy-worker.js';

class WorkerRepository {
  constructor(cursor = null) {
    this.cursor = cursor;
    this.started = 0;
    this.completed = [];
    this.failedRuns = [];
    this.itemFailures = [];
  }

  async beginLegacySourceRun() {
    this.started += 1;
    return { checkpointCursor: this.cursor, readOwner: 'legacy' };
  }

  async completeLegacySourceRun(value) {
    this.cursor = value.checkpointCursor;
    this.completed.push(value);
  }

  async failLegacySourceRun(value) { this.failedRuns.push(value); }
  async recordLegacyWorkerFailure(value) { this.itemFailures.push(value); }
}

test('worker advances checkpoint only after successful page', async () => {
  const repository = new WorkerRepository('cursor-1');
  const received = [];
  const worker = new LegacyReconciliationWorker({
    sourceKey: 'primary-bot',
    repository,
    source: {
      async pullPage({ cursor, limit }) {
        assert.equal(cursor, 'cursor-1');
        assert.equal(limit, 2);
        return {
          items: [{ event_id: 'a' }, { event_id: 'b' }],
          nextCursor: 'cursor-2',
          hasMore: false,
        };
      },
    },
    reconciler: {
      async reconcile(value) {
        received.push(value);
        return { outcome: 'applied', replay: false };
      },
    },
    batchSize: 2,
    sleeper: async () => {},
  });

  const result = await worker.runOnce();

  assert.equal(repository.cursor, 'cursor-2');
  assert.equal(result.applied, 2);
  assert.equal(result.failed, 0);
  assert.equal(result.hasMore, false);
  assert.equal(received.every((item) => item.source_key === 'primary-bot'), true);
});

test('failed item holds checkpoint so restart replays the same page', async () => {
  const repository = new WorkerRepository('cursor-1');
  const attempts = new Map();
  const worker = new LegacyReconciliationWorker({
    sourceKey: 'primary-bot',
    repository,
    source: {
      async pullPage() {
        return {
          items: [{ event_id: 'ok' }, { event_id: 'fails' }],
          nextCursor: 'cursor-2',
          hasMore: false,
        };
      },
    },
    reconciler: {
      async reconcile(value) {
        attempts.set(value.event_id, (attempts.get(value.event_id) ?? 0) + 1);
        if (value.event_id === 'fails') throw new ApiError(400, 'fixture_failure', 'fail');
        return { outcome: attempts.get(value.event_id) === 1 ? 'applied' : 'applied', replay: attempts.get(value.event_id) > 1 };
      },
    },
    maxAttempts: 1,
    sleeper: async () => {},
  });

  const first = await worker.runOnce();
  const second = await worker.runOnce();

  assert.equal(first.failed, 1);
  assert.equal(first.nextCursor, 'cursor-1');
  assert.equal(second.nextCursor, 'cursor-1');
  assert.equal(repository.cursor, 'cursor-1');
  assert.equal(attempts.get('ok'), 2);
  assert.equal(attempts.get('fails'), 2);
  assert.equal(repository.itemFailures.length, 2);
});

test('retryable source failure uses bounded exponential retry', async () => {
  const repository = new WorkerRepository();
  const delays = [];
  let calls = 0;
  const worker = new LegacyReconciliationWorker({
    sourceKey: 'primary-bot',
    repository,
    source: {
      async pullPage() {
        calls += 1;
        if (calls < 3) throw new ApiError(503, 'source_unavailable', 'down', { retryable: true });
        return { items: [], nextCursor: null, hasMore: false };
      },
    },
    reconciler: { async reconcile() { throw new Error('not called'); } },
    maxAttempts: 3,
    baseRetryMillis: 10,
    sleeper: async (delay) => { delays.push(delay); },
  });

  const result = await worker.runOnce();

  assert.equal(calls, 3);
  assert.deepEqual(delays, [10, 20]);
  assert.equal(result.received, 0);
  assert.equal(repository.failedRuns.length, 0);
});

test('non-retryable source failure is persisted and rethrown', async () => {
  const repository = new WorkerRepository();
  const worker = new LegacyReconciliationWorker({
    sourceKey: 'primary-bot',
    repository,
    source: {
      async pullPage() { throw new ApiError(400, 'source_contract_invalid', 'bad'); },
    },
    reconciler: { async reconcile() { throw new Error('not called'); } },
    sleeper: async () => {},
  });

  await assert.rejects(() => worker.runOnce(), (error) => error.code === 'source_contract_invalid');
  assert.equal(repository.failedRuns.length, 1);
  assert.equal(repository.failedRuns[0].errorCode, 'source_contract_invalid');
});

test('invalid source page is rejected without checkpoint advancement', async () => {
  const repository = new WorkerRepository('cursor-1');
  const worker = new LegacyReconciliationWorker({
    sourceKey: 'primary-bot',
    repository,
    source: {
      async pullPage() { return { items: [], nextCursor: null, hasMore: true }; },
    },
    reconciler: { async reconcile() { throw new Error('not called'); } },
    sleeper: async () => {},
  });

  await assert.rejects(() => worker.runOnce(), /nextCursor/);
  assert.equal(repository.cursor, 'cursor-1');
  assert.equal(repository.failedRuns.length, 1);
});
