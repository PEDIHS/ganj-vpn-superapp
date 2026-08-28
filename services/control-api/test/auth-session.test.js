import assert from 'node:assert/strict';
import {
  createHash,
  generateKeyPairSync,
  randomBytes,
  sign as signBytes,
} from 'node:crypto';
import test from 'node:test';
import { AuthSessionService } from '../src/adapters/auth-session.js';
import { JwksJwtVerifier } from '../src/security/jwt.js';

const USER_ID = '10000000-0000-4000-8000-000000000001';
const DEVICE_ID = '20000000-0000-4000-8000-000000000001';
const NOW = new Date('2026-08-28T00:00:00.000Z');

function hash(value) {
  return createHash('sha256').update(value).digest('hex');
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b))
      .map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}

class FakeSessionStore {
  constructor(identity) {
    this.identity = identity;
    this.nonces = new Set();
    this.refresh = new Map();
    this.sessions = [];
    this.familyRevoked = false;
  }

  async consumeAuthNonce({ nonceDigest }) {
    if (this.nonces.has(nonceDigest)) return false;
    this.nonces.add(nonceDigest);
    return true;
  }

  async registerGuest(value) {
    this.registration = value;
    return { userId: USER_ID, authMethod: 'guest' };
  }

  async createSession(value) {
    this.sessions.push(value);
    this.refresh.set(value.refreshTokenHash, {
      userId: value.userId,
      deviceId: value.deviceId,
      familyId: value.familyId,
      authMethod: value.authMethod,
      consumed: false,
    });
  }

  async findRefreshForProof({ tokenHash, deviceId }) {
    const row = this.refresh.get(tokenHash);
    if (!row || row.deviceId !== deviceId) return null;
    return {
      keyVersion: this.identity.keyVersion,
      signingPublicJwk: this.identity.signingPublicJwk,
    };
  }

  async rotateRefresh(value) {
    const row = this.refresh.get(value.tokenHash);
    if (!row || row.deviceId !== value.deviceId) return { status: 'invalid' };
    if (row.consumed) {
      this.familyRevoked = true;
      return { status: 'reuse' };
    }
    row.consumed = true;
    this.refresh.set(value.nextRefreshTokenHash, { ...row, consumed: false });
    return { status: 'rotated', userId: row.userId, authMethod: row.authMethod };
  }

  async revokeSession(value) {
    this.revoked = value;
  }

  async createTelegramState(value) {
    this.telegramState = value;
  }

  async linkTelegram() {
    return { userId: USER_ID };
  }
}

function fixture() {
  const signing = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const encryption = generateKeyPairSync('x25519');
  const issuer = generateKeyPairSync('ed25519');
  const publicEncryptionJwk = encryption.publicKey.export({ format: 'jwk' });
  const identity = {
    deviceId: DEVICE_ID,
    keyVersion: 'v1',
    signingPublicKeySpki: signing.publicKey.export({ format: 'der', type: 'spki' }).toString('base64url'),
    encryptionPublicKeyRaw: publicEncryptionJwk.x,
    signingPublicJwk: signing.publicKey.export({ format: 'jwk' }),
  };
  const store = new FakeSessionStore(identity);
  const service = new AuthSessionService({
    store,
    signingPrivateJwk: issuer.privateKey.export({ format: 'jwk' }),
    signingKeyId: 'session-test-v1',
    issuer: 'https://auth.ganj.example',
    audience: 'ganj-control-api',
    telegramAuthorizationEndpoint: 'https://oauth.telegram.org/auth',
    telegramClientId: 'ganj-test',
    telegramRedirectUris: ['ganjvpn://oauth/telegram'],
    clock: () => new Date(NOW),
  });
  return { signing, identity, store, service };
}

function proof({ signing, identity, path, body, nonce = randomBytes(18).toString('base64url') }) {
  const timestamp = Math.floor(NOW.getTime() / 1000);
  const bodyHash = createHash('sha256')
    .update(JSON.stringify(canonical(body)))
    .digest('base64url');
  const bytes = Buffer.from([
    'GANJ-DEVICE-PROOF-V1',
    'POST',
    path,
    bodyHash,
    timestamp,
    nonce,
    identity.keyVersion,
  ].join('\n'), 'ascii');
  const signature = signBytes('sha256', bytes, {
    key: signing.privateKey,
    dsaEncoding: 'ieee-p1363',
  }).toString('base64url');
  return `gdp1.${timestamp}.${nonce}.${bodyHash}.${identity.keyVersion}.${signature}`;
}

function guestRequest(value) {
  const unsigned = {
    device_id: value.identity.deviceId,
    encryption_public_key_raw: value.identity.encryptionPublicKeyRaw,
    key_version: value.identity.keyVersion,
    signing_public_key_spki: value.identity.signingPublicKeySpki,
  };
  return {
    ...unsigned,
    deviceProof: proof({
      signing: value.signing,
      identity: value.identity,
      path: '/v1/auth/guest',
      body: unsigned,
    }),
  };
}

test('guest registration proves both device keys and issues a JWKS-verifiable access JWT', async () => {
  const value = fixture();
  const session = await value.service.guest({
    deviceId: value.identity.deviceId,
    keyVersion: value.identity.keyVersion,
    signingPublicKeySpki: value.identity.signingPublicKeySpki,
    encryptionPublicKeyRaw: value.identity.encryptionPublicKeyRaw,
    deviceProof: guestRequest(value).deviceProof,
  });

  assert.equal(session.user_id, USER_ID);
  assert.equal(session.device_id, DEVICE_ID);
  assert.equal(session.token_type, 'Bearer');
  assert.equal(session.access_token.split('.').length, 3);
  assert.equal(value.store.registration.keyVersion, 'v1');
  assert.equal(value.store.registration.encryptionPublicJwk.crv, 'X25519');
  assert.equal(value.store.sessions.length, 1);
  assert.equal('d' in value.service.jwks().keys[0], false);

  const verifier = new JwksJwtVerifier({
    jwksUri: 'https://auth.ganj.example/.well-known/jwks.json',
    issuer: 'https://auth.ganj.example',
    audience: 'ganj-control-api',
    allowedAlgorithms: ['EdDSA'],
    clock: () => new Date(NOW),
    fetchImpl: async () => new Response(JSON.stringify(value.service.jwks()), { status: 200 }),
  });
  const claims = await verifier.verify(session.access_token);
  assert.equal(claims.sub, USER_ID);
  assert.equal(claims.device_id, DEVICE_ID);
  assert.equal(claims.token_use, 'access');
});

test('guest registration rejects a proof copied to mutated identity material', async () => {
  const value = fixture();
  const request = guestRequest(value);
  await assert.rejects(
    value.service.guest({
      deviceId: value.identity.deviceId,
      keyVersion: value.identity.keyVersion,
      signingPublicKeySpki: value.identity.signingPublicKeySpki,
      encryptionPublicKeyRaw: 'A'.repeat(43),
      deviceProof: request.deviceProof,
    }),
    (error) => error.code === 'invalid_device_proof',
  );
  assert.equal(value.store.sessions.length, 0);
});

test('refresh rotates once and reuse revokes the complete token family', async () => {
  const value = fixture();
  const request = guestRequest(value);
  const first = await value.service.guest({
    deviceId: value.identity.deviceId,
    keyVersion: value.identity.keyVersion,
    signingPublicKeySpki: value.identity.signingPublicKeySpki,
    encryptionPublicKeyRaw: value.identity.encryptionPublicKeyRaw,
    deviceProof: request.deviceProof,
  });
  const unsignedRefresh = { device_id: DEVICE_ID, refresh_token: first.refresh_token };
  const firstProof = proof({
    signing: value.signing,
    identity: value.identity,
    path: '/v1/auth/refresh',
    body: unsignedRefresh,
  });
  const rotated = await value.service.refresh({
    refreshToken: first.refresh_token,
    deviceId: DEVICE_ID,
    deviceProof: firstProof,
  });
  assert.notEqual(rotated.refresh_token, first.refresh_token);

  const reuseProof = proof({
    signing: value.signing,
    identity: value.identity,
    path: '/v1/auth/refresh',
    body: unsignedRefresh,
  });
  await assert.rejects(
    value.service.refresh({
      refreshToken: first.refresh_token,
      deviceId: DEVICE_ID,
      deviceProof: reuseProof,
    }),
    (error) => error.code === 'refresh_token_reuse_detected',
  );
  assert.equal(value.store.familyRevoked, true);
});

test('Telegram start is access-bound, PKCE S256-only, redirect-allowlisted and state-digested', async () => {
  const value = fixture();
  const result = await value.service.beginTelegram({
    principal: { userId: USER_ID, deviceId: DEVICE_ID },
    codeChallenge: 'A'.repeat(43),
    redirectUri: 'ganjvpn://oauth/telegram',
  });
  const url = new URL(result.authorization_url);
  assert.equal(url.protocol, 'https:');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('state'), result.state);
  assert.equal(value.store.telegramState.stateDigest, hash(result.state));
  assert.equal(value.store.telegramState.userId, USER_ID);

  await assert.rejects(
    value.service.beginTelegram({
      principal: { userId: USER_ID, deviceId: DEVICE_ID },
      codeChallenge: 'A'.repeat(43),
      redirectUri: 'https://attacker.invalid/callback',
    }),
    (error) => error.code === 'invalid_telegram_login',
  );
});
