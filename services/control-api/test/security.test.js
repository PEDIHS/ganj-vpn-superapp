import assert from 'node:assert/strict';
import {
  createDecipheriv,
  createHash,
  createPrivateKey,
  createPublicKey,
  createSign,
  diffieHellman,
  generateKeyPairSync,
  hkdfSync,
  sign as signRaw,
} from 'node:crypto';
import test from 'node:test';
import { GooglePlayPurchaseVerifier } from '../src/adapters/google-play.js';
import { GooglePlayRtdnAdapter } from '../src/adapters/google-play-rtdn.js';
import { JwksAuthAdapter } from '../src/adapters/jwks-auth.js';
import { TelegramOidcExchangeAdapter } from '../src/adapters/telegram-oidc.js';
import { JwksJwtVerifier } from '../src/security/jwt.js';
import { FIXTURES } from '../src/repository.js';

const NOW = new Date('2026-08-24T12:00:00.000Z');

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}

function jwt(privateKey, claims, { kid = 'key-1', alg = 'RS256' } = {}) {
  const header = Buffer.from(JSON.stringify({ alg, typ: 'JWT', kid })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  return `${header}.${payload}.${signer.sign(privateKey).toString('base64url')}`;
}

function rsaFixture() {
  const keys = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const publicJwk = keys.publicKey.export({ format: 'jwk' });
  return {
    ...keys,
    jwk: { ...publicJwk, kid: 'key-1', alg: 'RS256', use: 'sig' },
  };
}

test('JWKS verifier validates asymmetric signature and registered claims with caching', async () => {
  const keys = rsaFixture();
  let fetches = 0;
  const verifier = new JwksJwtVerifier({
    jwksUri: 'https://issuer.test/.well-known/jwks.json',
    issuer: 'https://issuer.test',
    audience: 'ganj-control-api',
    clock: () => new Date(NOW),
    fetchImpl: async () => {
      fetches += 1;
      return new Response(JSON.stringify({ keys: [keys.jwk] }), { status: 200 });
    },
  });
  const claims = {
    iss: 'https://issuer.test',
    aud: ['another-audience', 'ganj-control-api'],
    sub: FIXTURES.users.primary,
    device_id: FIXTURES.devices.primary,
    jti: 'session-jti-1',
    token_use: 'access',
    iat: NOW.getTime() / 1000,
    exp: NOW.getTime() / 1000 + 600,
  };
  assert.equal((await verifier.verify(jwt(keys.privateKey, claims))).sub, FIXTURES.users.primary);
  await verifier.verify(jwt(keys.privateKey, claims));
  assert.equal(fetches, 1);
  await assert.rejects(() => verifier.verify(jwt(keys.privateKey, { ...claims, aud: 'wrong' })), /audience/);
  await assert.rejects(() => verifier.verify(jwt(keys.privateKey, { ...claims, exp: NOW.getTime() / 1000 - 120 })), /expired/);
  const signed = jwt(keys.privateKey, claims);
  const signedParts = signed.split('.');
  signedParts[2] = `${signedParts[2][0] === 'A' ? 'B' : 'A'}${signedParts[2].slice(1)}`;
  const tampered = signedParts.join('.');
  await assert.rejects(() => verifier.verify(tampered), /signature/);
});

test('JWKS auth binds JWT session, device proof, and X25519 encrypted profile', async () => {
  const issuerKeys = rsaFixture();
  const signingKeys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const encryptionKeys = generateKeyPairSync('x25519');
  const verifier = new JwksJwtVerifier({
    jwksUri: 'https://issuer.test/jwks',
    issuer: 'https://issuer.test',
    audience: 'ganj-control-api',
    clock: () => new Date(NOW),
    fetchImpl: async () => new Response(JSON.stringify({ keys: [issuerKeys.jwk] }), { status: 200 }),
  });
  const usedProofNonces = new Set();
  const principalValidator = {
    async validatePrincipal({ jti }) {
      if (jti !== 'active-jti') return null;
      return {
        signingPublicJwk: signingKeys.publicKey.export({ format: 'jwk' }),
        encryptionPublicJwk: encryptionKeys.publicKey.export({ format: 'jwk' }),
        keyVersion: 'device-key-v3',
      };
    },
    async consumeDeviceProofNonce({ deviceId, keyVersion, nonceDigest }) {
      const key = `${deviceId}:${keyVersion}:${nonceDigest}`;
      if (usedProofNonces.has(key)) return false;
      usedProofNonces.add(key);
      return true;
    },
  };
  const adapter = new JwksAuthAdapter({ verifier, principalValidator, clock: () => new Date(NOW) });
  const accessToken = jwt(issuerKeys.privateKey, {
    iss: 'https://issuer.test', aud: 'ganj-control-api', sub: FIXTURES.users.primary,
    device_id: FIXTURES.devices.primary, jti: 'active-jti', token_use: 'access',
    iat: NOW.getTime() / 1000, exp: NOW.getTime() / 1000 + 600,
  });
  const principal = await adapter.authenticate(new Request('https://api.test/v1/services', {
    headers: { authorization: `Bearer ${accessToken}` },
  }));
  assert.equal(principal.deviceId, FIXTURES.devices.primary);

  const method = 'POST';
  const pathAndQuery = '/v1/services/40000000-0000-4000-8000-000000000001/connection-profile';
  const unsignedBody = {
    client_nonce: 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
    device_id: principal.deviceId,
    server_id: FIXTURES.servers.premium,
  };
  const proofTimestamp = Math.floor(NOW.getTime() / 1_000);
  const proofNonce = 'AAAAAAAAAAAAAAAAAAAAAA';
  const proofBodyHash = createHash('sha256')
    .update(JSON.stringify(canonical(unsignedBody)))
    .digest('base64url');
  const proofCanonical = [
    'GANJ-DEVICE-PROOF-V1',
    method,
    pathAndQuery,
    proofBodyHash,
    String(proofTimestamp),
    proofNonce,
    principal.deviceKeyVersion,
  ].join('\n');
  const proofSignature = signRaw(
    'sha256',
    Buffer.from(proofCanonical, 'ascii'),
    { key: signingKeys.privateKey, dsaEncoding: 'ieee-p1363' },
  ).toString('base64url');
  const proof = [
    'gdp1',
    proofTimestamp,
    proofNonce,
    proofBodyHash,
    principal.deviceKeyVersion,
    proofSignature,
  ].join('.');
  const proofInput = { principal, method, pathAndQuery, unsignedBody, proof };
  assert.equal(await adapter.verifyDeviceProof(proofInput), true);
  assert.equal(await adapter.verifyDeviceProof(proofInput), false);
  assert.equal(
    await adapter.verifyDeviceProof({
      ...proofInput,
      unsignedBody: { ...unsignedBody, client_nonce: 'changed' },
    }),
    false,
  );

  const associatedData = { profileId: 'profile-1', deviceId: principal.deviceId, expiresAt: '2026-08-24T12:05:00Z' };
  const plaintext = { endpoint: 'vpn.internal', credential: 'sensitive', grant_id: 'profile-1' };
  const sealed = await adapter.sealConnectionProfile({ principal, plaintext, associatedData });
  assert.equal(sealed.keyVersion, 'device-key-v3');
  assert.equal(JSON.stringify(sealed).includes('sensitive'), false);
  const envelope = Buffer.from(sealed.ciphertext, 'base64');
  assert.equal(envelope.subarray(0, 4).toString(), 'GVP1');
  const ephemeralRaw = envelope.subarray(4, 36);
  const deviceJwk = encryptionKeys.publicKey.export({ format: 'jwk' });
  const ephemeralKey = createPublicKey({ key: { kty: 'OKP', crv: 'X25519', x: ephemeralRaw.toString('base64url') }, format: 'jwk' });
  const shared = diffieHellman({ privateKey: encryptionKeys.privateKey, publicKey: ephemeralKey });
  const aad = Buffer.from(JSON.stringify(canonical(associatedData)));
  const key = Buffer.from(hkdfSync('sha256', shared, createHash('sha256').update(aad).digest(), Buffer.from('ganj-vpn-profile-v1'), 32));
  const ciphertext = envelope.subarray(36, -16);
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(sealed.nonce, 'base64'));
  decipher.setAAD(aad);
  decipher.setAuthTag(envelope.subarray(-16));
  const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  assert.deepEqual(JSON.parse(decrypted.toString()), plaintext);
  assert.equal(deviceJwk.crv, 'X25519');

  const revokedToken = jwt(issuerKeys.privateKey, {
    iss: 'https://issuer.test', aud: 'ganj-control-api', sub: FIXTURES.users.primary,
    device_id: FIXTURES.devices.primary, jti: 'revoked', token_use: 'access', exp: NOW.getTime() / 1000 + 600,
  });
  await assert.rejects(
    () => adapter.authenticate(new Request('https://api.test', { headers: { authorization: `Bearer ${revokedToken}` } })),
    (error) => error.status === 401 && error.code === 'unauthorized' && !error.message.includes('active-jti'),
  );
});

test('Telegram OIDC exchange consumes state/PKCE, verifies ID token, then delegates session issuance', async () => {
  const observed = {};
  const adapter = new TelegramOidcExchangeAdapter({
    tokenEndpoint: 'https://telegram-id.test/token',
    clientId: 'ganj-mobile',
    clientSecret: 'injected-secret',
    stateStore: {
      async consume(input) {
        observed.state = input;
        return { redirectUri: 'com.ganj.vpn:/oauth', deviceId: FIXTURES.devices.primary };
      },
    },
    idTokenVerifier: { async verify(token) { assert.equal(token, 'signed-id-token'); return { sub: 'telegram:123', preferred_username: 'ganj_user' }; } },
    accountBroker: {
      async linkAndIssueSession(input) { observed.account = input; return { device_id: input.deviceId, tokens: { access_token: 'issued' } }; },
    },
    fetchImpl: async (_url, init) => {
      const form = new URLSearchParams(init.body);
      assert.equal(form.get('code_verifier'), 'v'.repeat(43));
      assert.equal(form.get('client_secret'), 'injected-secret');
      return new Response(JSON.stringify({ id_token: 'signed-id-token' }), { status: 200 });
    },
    clock: () => new Date(NOW),
  });
  const result = await adapter.exchangeAuthorizationCode({ code: 'authorization-code', state: 'state', codeVerifier: 'v'.repeat(43) });
  assert.equal(result.device_id, FIXTURES.devices.primary);
  assert.equal(observed.account.telegramSubject, 'telegram:123');
  assert.equal(observed.state.state, 'state');
});

test('Google Play uses subscriptionsv2 state as authority, never entitles PENDING, and models ack/cancel/revoke', async () => {
  const calls = [];
  const activeDocument = {
    packageName: 'com.ganj.vpn',
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    latestOrderId: 'GPA.1234-5678',
    lineItems: [{ productId: 'ganj.premium.30d', expiryTime: '2026-09-23T12:00:00Z' }],
  };
  const verifier = new GooglePlayPurchaseVerifier({
    packageName: 'com.ganj.vpn',
    accessTokenProvider: { async getAccessToken() { return 'access-token'; } },
    clock: () => new Date(NOW),
    fetchImpl: async (url, init) => {
      calls.push({ url: String(url), init });
      return init?.method === 'POST'
        ? new Response(null, { status: 204 })
        : new Response(JSON.stringify(activeDocument), { status: 200 });
    },
  });
  const state = await verifier.verifyPlayPurchase({
    packageName: 'com.ganj.vpn', productId: 'ganj.premium.30d', purchaseToken: 'play-token',
  });
  assert.equal(state.valid, true);
  assert.equal(state.requiresAcknowledgement, true);
  assert.match(calls[0].url, /purchases\/subscriptionsv2\/tokens\/play-token$/);
  await verifier.acknowledgePlayPurchase({ purchaseToken: 'play-token', productId: 'ganj.premium.30d' });
  assert.match(calls.at(-1).url, /purchases\/subscriptions\/ganj.premium.30d\/tokens\/play-token:acknowledge$/);
  await verifier.cancelPlaySubscription({ purchaseToken: 'play-token' });
  assert.match(calls.at(-1).url, /subscriptionsv2\/tokens\/play-token:cancel$/);
  await verifier.revokePlaySubscription({ purchaseToken: 'play-token', revocationType: 'FULL_REFUND' });
  assert.match(calls.at(-1).url, /subscriptionsv2\/tokens\/play-token:revoke$/);

  const pending = new GooglePlayPurchaseVerifier({
    packageName: 'com.ganj.vpn',
    accessTokenProvider: { async getAccessToken() { return 'access-token'; } },
    clock: () => new Date(NOW),
    fetchImpl: async () => new Response(JSON.stringify({
      ...activeDocument,
      subscriptionState: 'SUBSCRIPTION_STATE_PENDING',
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    }), { status: 200 }),
  });
  assert.equal((await pending.verifyPlayPurchase({
    packageName: 'com.ganj.vpn', productId: 'ganj.premium.30d', purchaseToken: 'pending-token',
  })).valid, false);
});

test('RTDN contract requires signed Pub/Sub identity and returns only a re-query signal', async () => {
  const adapter = new GooglePlayRtdnAdapter({
    jwtVerifier: { async verify() { return { email: 'pubsub@test.iam.gserviceaccount.com', email_verified: true }; } },
    expectedServiceAccount: 'pubsub@test.iam.gserviceaccount.com',
    expectedSubscription: 'projects/test/subscriptions/play-rtdn',
    packageName: 'com.ganj.vpn',
  });
  const notification = {
    version: '1.0',
    packageName: 'com.ganj.vpn',
    subscriptionNotification: {
      notificationType: 2,
      purchaseToken: 'sensitive-purchase-token',
      subscriptionId: 'ganj.premium.30d',
    },
  };
  const body = {
    subscription: 'projects/test/subscriptions/play-rtdn',
    message: {
      messageId: 'event-1',
      publishTime: NOW.toISOString(),
      data: Buffer.from(JSON.stringify(notification)).toString('base64'),
    },
  };
  const signal = await adapter.verifyAndDecode({
    request: new Request('https://api.test/v1/billing/play/rtdn', { headers: { authorization: 'Bearer signed-callback' } }),
    body,
  });
  assert.equal(signal.eventId, 'event-1');
  assert.equal(signal.purchaseToken, 'sensitive-purchase-token');
  assert.equal('entitled' in signal, false);

  const wrongIdentity = new GooglePlayRtdnAdapter({
    jwtVerifier: { async verify() { return { email: 'attacker@test', email_verified: true }; } },
    expectedServiceAccount: 'pubsub@test.iam.gserviceaccount.com',
    expectedSubscription: body.subscription,
    packageName: 'com.ganj.vpn',
  });
  await assert.rejects(
    () => wrongIdentity.verifyAndDecode({ request: new Request('https://api.test', { headers: { authorization: 'Bearer token' } }), body }),
    (error) => error.code === 'invalid_callback_identity',
  );
});
