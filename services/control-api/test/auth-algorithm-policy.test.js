import assert from 'node:assert/strict';
import test from 'node:test';
import { authAllowedAlgorithms } from '../src/adapters/jwks-auth.js';

test('internal session verifier defaults to EdDSA to match the Ed25519 issuer', () => {
  assert.deepEqual(authAllowedAlgorithms({}), ['EdDSA']);
});

test('AUTH_ALLOWED_ALGORITHMS remains an explicit deployment override', () => {
  assert.deepEqual(
    authAllowedAlgorithms({ AUTH_ALLOWED_ALGORITHMS: 'EdDSA,RS256' }),
    ['EdDSA', 'RS256'],
  );
});

test('empty configured algorithm list is rejected instead of silently widening trust', () => {
  assert.throws(
    () => authAllowedAlgorithms({ AUTH_ALLOWED_ALGORITHMS: ' , ' }),
    /at least one algorithm/,
  );
});
