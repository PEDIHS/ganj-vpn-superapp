import assert from 'node:assert/strict';
import test from 'node:test';
import { classifyPlaySubscriptionLifecycle } from '../src/adapters/google-play.js';

const future = '2026-09-30T00:00:00Z';
const nowEpochMillis = Date.parse('2026-08-28T00:00:00Z');

test('Play lifecycle keeps active, grace, and cancelled-before-expiry subscriptions entitled', () => {
  for (const state of [
    'SUBSCRIPTION_STATE_ACTIVE',
    'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
    'SUBSCRIPTION_STATE_CANCELED',
  ]) {
    assert.deepEqual(
      classifyPlaySubscriptionLifecycle({ state, expiresAt: future, nowEpochMillis }),
      { entitled: true, serviceDisposition: 'active' },
    );
  }
});

test('Play lifecycle preserves current service for pending and cancelled pending purchases', () => {
  for (const state of [
    'SUBSCRIPTION_STATE_PENDING',
    'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED',
  ]) {
    assert.deepEqual(
      classifyPlaySubscriptionLifecycle({ state, expiresAt: null, nowEpochMillis }),
      { entitled: false, serviceDisposition: 'preserve' },
    );
  }
});

test('Play lifecycle disables account hold and pause, and expires terminal subscriptions', () => {
  for (const state of ['SUBSCRIPTION_STATE_ON_HOLD', 'SUBSCRIPTION_STATE_PAUSED']) {
    assert.equal(
      classifyPlaySubscriptionLifecycle({ state, expiresAt: future, nowEpochMillis }).serviceDisposition,
      'disabled',
    );
  }
  assert.deepEqual(
    classifyPlaySubscriptionLifecycle({
      state: 'SUBSCRIPTION_STATE_EXPIRED',
      expiresAt: '2026-08-01T00:00:00Z',
      nowEpochMillis,
    }),
    { entitled: false, serviceDisposition: 'expired' },
  );
});

test('Play lifecycle fails closed for unknown Publisher states', () => {
  assert.throws(
    () => classifyPlaySubscriptionLifecycle({ state: 'SUBSCRIPTION_STATE_FUTURE', expiresAt: future, nowEpochMillis }),
    /unknown subscription state/,
  );
});
