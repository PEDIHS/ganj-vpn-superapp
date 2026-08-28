import {
  createHash,
  createPrivateKey,
  createPublicKey,
  randomBytes,
  randomUUID,
  sign as signBytes,
  timingSafeEqual,
  verify as verifyBytes,
} from 'node:crypto';
import { ApiError } from '../errors.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const KEY_VERSION = /^v[1-9][0-9]{0,8}$/;
const BASE64URL = /^[A-Za-z0-9_-]+$/;
const ACCESS_SECONDS = 15 * 60;
const REFRESH_SECONDS = 30 * 24 * 60 * 60;
const STATE_SECONDS = 10 * 60;
const PROOF_SKEW_SECONDS = 90;

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function encodeJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}

function canonicalBody(value) {
  return Buffer.from(JSON.stringify(canonical(value)), 'utf8');
}

function safeSame(left, right) {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  return a.length === b.length && timingSafeEqual(a, b);
}

function requireDuration(name, raw, fallback, minimum, maximum) {
  const value = raw === undefined ? fallback : Number(raw);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} to ${maximum}.`);
  }
  return value;
}

export function decodeDeviceIdentity({
  deviceId,
  keyVersion,
  signingPublicKeySpki,
  encryptionPublicKeyRaw,
}) {
  if (!UUID.test(deviceId) || !KEY_VERSION.test(keyVersion)) {
    throw new ApiError(400, 'invalid_device_identity', 'Device identity metadata is invalid.');
  }
  if (typeof signingPublicKeySpki !== 'string' || !BASE64URL.test(signingPublicKeySpki)
    || signingPublicKeySpki.length < 80 || signingPublicKeySpki.length > 512) {
    throw new ApiError(400, 'invalid_device_identity', 'Device signing key is invalid.');
  }
  if (typeof encryptionPublicKeyRaw !== 'string' || !BASE64URL.test(encryptionPublicKeyRaw)
    || encryptionPublicKeyRaw.length !== 43) {
    throw new ApiError(400, 'invalid_device_identity', 'Device encryption key is invalid.');
  }
  let signingKey;
  let signingPublicJwk;
  try {
    signingKey = createPublicKey({
      key: Buffer.from(signingPublicKeySpki, 'base64url'),
      format: 'der',
      type: 'spki',
    });
    if (signingKey.asymmetricKeyType !== 'ec'
      || signingKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1') throw new Error();
    signingPublicJwk = signingKey.export({ format: 'jwk' });
  } catch {
    throw new ApiError(400, 'invalid_device_identity', 'Device signing key is not an ES256 public key.');
  }
  const encryptionRaw = Buffer.from(encryptionPublicKeyRaw, 'base64url');
  if (encryptionRaw.length !== 32) {
    throw new ApiError(400, 'invalid_device_identity', 'Device encryption key is not X25519.');
  }
  const encryptionPublicJwk = { kty: 'OKP', crv: 'X25519', x: encryptionPublicKeyRaw };
  try {
    const key = createPublicKey({ key: encryptionPublicJwk, format: 'jwk' });
    if (key.asymmetricKeyType !== 'x25519') throw new Error();
  } catch {
    throw new ApiError(400, 'invalid_device_identity', 'Device encryption key is not X25519.');
  }
  return {
    deviceId,
    keyVersion,
    signingKey,
    signingPublicJwk: { ...signingPublicJwk, alg: 'ES256', use: 'sig' },
    encryptionPublicJwk,
  };
}

export function verifyDeviceIdentityProof({
  identity,
  method,
  pathAndQuery,
  unsignedBody,
  compactProof,
  now = new Date(),
}) {
  if (typeof compactProof !== 'string' || compactProof.length > 1024) {
    throw new ApiError(401, 'invalid_device_proof', 'Device proof is invalid.');
  }
  const parts = compactProof.split('.');
  if (parts.length !== 6 || parts[0] !== 'gdp1') {
    throw new ApiError(401, 'invalid_device_proof', 'Device proof is invalid.');
  }
  const [, timestampText, nonce, claimedBodyHash, keyVersion, signatureText] = parts;
  const timestamp = Number(timestampText);
  if (!Number.isSafeInteger(timestamp)
    || Math.abs(Math.floor(now.getTime() / 1000) - timestamp) > PROOF_SKEW_SECONDS
    || !/^[A-Za-z0-9_-]{22,128}$/.test(nonce)
    || !/^[A-Za-z0-9_-]{43}$/.test(claimedBodyHash)
    || keyVersion !== identity.keyVersion
    || !/^[A-Za-z0-9_-]{80,128}$/.test(signatureText)) {
    throw new ApiError(401, 'invalid_device_proof', 'Device proof is expired or malformed.');
  }
  const actualBodyHash = createHash('sha256').update(canonicalBody(unsignedBody)).digest('base64url');
  if (!safeSame(claimedBodyHash, actualBodyHash)) {
    throw new ApiError(401, 'invalid_device_proof', 'Device proof does not match the request body.');
  }
  const canonicalProof = Buffer.from([
    'GANJ-DEVICE-PROOF-V1',
    method.toUpperCase(),
    pathAndQuery,
    claimedBodyHash,
    timestampText,
    nonce,
    keyVersion,
  ].join('\n'), 'ascii');
  const signature = Buffer.from(signatureText, 'base64url');
  if (signature.length !== 64 || !verifyBytes('sha256', canonicalProof, {
    key: identity.signingKey,
    dsaEncoding: 'ieee-p1363',
  }, signature)) {
    throw new ApiError(401, 'invalid_device_proof', 'Device proof signature is invalid.');
  }
  return {
    nonceDigest: sha256(nonce),
    expiresAt: new Date((timestamp + PROOF_SKEW_SECONDS) * 1000),
  };
}

export class AuthSessionService {
  constructor({
    store,
    signingPrivateJwk,
    signingKeyId,
    issuer,
    audience,
    telegramAuthorizationEndpoint,
    telegramClientId,
    telegramRedirectUris,
    clock = () => new Date(),
    accessSeconds = ACCESS_SECONDS,
    refreshSeconds = REFRESH_SECONDS,
  }) {
    if (!store || typeof store.registerGuest !== 'function' || typeof store.createSession !== 'function'
      || typeof store.rotateRefresh !== 'function' || typeof store.revokeSession !== 'function'
      || typeof store.consumeAuthNonce !== 'function') throw new Error('Auth session store is incomplete.');
    if (!signingKeyId || !/^[A-Za-z0-9_-]{1,64}$/.test(signingKeyId) || !issuer || !audience) {
      throw new Error('Auth issuer, audience, and signing key ID are required.');
    }
    let privateKey;
    try {
      privateKey = createPrivateKey({ key: signingPrivateJwk, format: 'jwk' });
      if (privateKey.asymmetricKeyType !== 'ed25519') throw new Error();
    } catch {
      throw new Error('AUTH_SESSION_SIGNING_PRIVATE_JWK must be an Ed25519 private JWK.');
    }
    const publicJwk = createPublicKey(privateKey).export({ format: 'jwk' });
    this.store = store;
    this.privateKey = privateKey;
    this.keyId = signingKeyId;
    this.issuer = issuer;
    this.audience = audience;
    this.publicJwk = { ...publicJwk, kid: signingKeyId, alg: 'EdDSA', use: 'sig' };
    this.clock = clock;
    this.accessSeconds = requireDuration('AUTH_ACCESS_TOKEN_SECONDS', accessSeconds, ACCESS_SECONDS, 300, 3600);
    this.refreshSeconds = requireDuration('AUTH_REFRESH_TOKEN_SECONDS', refreshSeconds, REFRESH_SECONDS, 3600, 90 * 24 * 60 * 60);
    this.telegramClientId = telegramClientId;
    this.telegramRedirectUris = new Set(telegramRedirectUris ?? []);
    this.telegramAuthorizationEndpoint = telegramAuthorizationEndpoint
      ? new URL(telegramAuthorizationEndpoint) : null;
    if (this.telegramAuthorizationEndpoint && this.telegramAuthorizationEndpoint.protocol !== 'https:') {
      throw new Error('Telegram authorization endpoint must use HTTPS.');
    }
  }

  jwks() {
    return { keys: [{ ...this.publicJwk }] };
  }

  async issue({ userId, deviceId, authMethod, familyId = randomUUID(), parentTokenHash = null }) {
    const now = this.clock();
    const accessJti = randomUUID();
    const sessionId = randomUUID();
    const refreshToken = randomBytes(32).toString('base64url');
    const accessExpiresAt = new Date(now.getTime() + this.accessSeconds * 1000);
    const refreshExpiresAt = new Date(now.getTime() + this.refreshSeconds * 1000);
    const claims = {
      iss: this.issuer,
      aud: this.audience,
      sub: userId,
      device_id: deviceId,
      jti: accessJti,
      token_use: 'access',
      auth_method: authMethod,
      iat: Math.floor(now.getTime() / 1000),
      nbf: Math.floor(now.getTime() / 1000),
      exp: Math.floor(accessExpiresAt.getTime() / 1000),
    };
    const header = encodeJson({ alg: 'EdDSA', typ: 'JWT', kid: this.keyId });
    const payload = encodeJson(claims);
    const signingInput = `${header}.${payload}`;
    const accessToken = `${signingInput}.${signBytes(null, Buffer.from(signingInput), this.privateKey).toString('base64url')}`;
    await this.store.createSession({
      sessionId,
      familyId,
      userId,
      deviceId,
      authMethod,
      accessJtiHash: sha256(accessJti),
      accessExpiresAt,
      refreshTokenHash: sha256(refreshToken),
      refreshExpiresAt,
      parentTokenHash,
    });
    return {
      user_id: userId,
      device_id: deviceId,
      access_token: accessToken,
      access_token_expires_at: accessExpiresAt.toISOString(),
      refresh_token: refreshToken,
      refresh_token_expires_at: refreshExpiresAt.toISOString(),
      token_type: 'Bearer',
    };
  }

  async guest({ deviceId, keyVersion, signingPublicKeySpki, encryptionPublicKeyRaw, deviceProof }) {
    const unsignedBody = {
      device_id: deviceId,
      encryption_public_key_raw: encryptionPublicKeyRaw,
      key_version: keyVersion,
      signing_public_key_spki: signingPublicKeySpki,
    };
    const identity = decodeDeviceIdentity({
      deviceId, keyVersion, signingPublicKeySpki, encryptionPublicKeyRaw,
    });
    const proof = verifyDeviceIdentityProof({
      identity,
      method: 'POST',
      pathAndQuery: '/v1/auth/guest',
      unsignedBody,
      compactProof: deviceProof,
      now: this.clock(),
    });
    if (!await this.store.consumeAuthNonce(proof)) {
      throw new ApiError(409, 'device_proof_replayed', 'Device proof was already used.');
    }
    const user = await this.store.registerGuest({
      deviceId,
      keyVersion,
      signingPublicJwk: identity.signingPublicJwk,
      encryptionPublicJwk: identity.encryptionPublicJwk,
      now: this.clock(),
    });
    return this.issue({ userId: user.userId, deviceId, authMethod: user.authMethod });
  }

  async beginTelegram({ principal, codeChallenge, redirectUri }) {
    if (!this.telegramAuthorizationEndpoint || !this.telegramClientId || this.telegramRedirectUris.size === 0) {
      throw new ApiError(503, 'telegram_login_unavailable', 'Telegram login is not configured.');
    }
    if (!/^[A-Za-z0-9_-]{43}$/.test(codeChallenge) || !this.telegramRedirectUris.has(redirectUri)) {
      throw new ApiError(400, 'invalid_telegram_login', 'Telegram PKCE challenge or redirect URI is invalid.');
    }
    const state = randomBytes(32).toString('base64url');
    const expiresAt = new Date(this.clock().getTime() + STATE_SECONDS * 1000);
    await this.store.createTelegramState({
      stateDigest: sha256(state),
      codeChallenge,
      redirectUri,
      userId: principal.userId,
      deviceId: principal.deviceId,
      expiresAt,
    });
    const url = new URL(this.telegramAuthorizationEndpoint);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', this.telegramClientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('scope', 'openid profile');
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', codeChallenge);
    url.searchParams.set('code_challenge_method', 'S256');
    return {
      authorization_url: url.toString(),
      state,
      expires_at: expiresAt.toISOString(),
    };
  }

  async refresh({ refreshToken, deviceId, deviceProof }) {
    if (typeof refreshToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(refreshToken) || !UUID.test(deviceId)) {
      throw new ApiError(401, 'invalid_refresh_token', 'Refresh token is invalid.');
    }
    const tokenHash = sha256(refreshToken);
    const current = await this.store.findRefreshForProof({ tokenHash, deviceId });
    if (!current) throw new ApiError(401, 'invalid_refresh_token', 'Refresh token is invalid.');
    const identity = {
      deviceId,
      keyVersion: current.keyVersion,
      signingKey: createPublicKey({ key: current.signingPublicJwk, format: 'jwk' }),
    };
    const unsignedBody = { device_id: deviceId, refresh_token: refreshToken };
    const proof = verifyDeviceIdentityProof({
      identity,
      method: 'POST',
      pathAndQuery: '/v1/auth/refresh',
      unsignedBody,
      compactProof: deviceProof,
      now: this.clock(),
    });
    if (!await this.store.consumeAuthNonce(proof)) {
      throw new ApiError(409, 'device_proof_replayed', 'Device proof was already used.');
    }
    const nextRefreshToken = randomBytes(32).toString('base64url');
    const nextAccessJti = randomUUID();
    const nextSessionId = randomUUID();
    const now = this.clock();
    const accessExpiresAt = new Date(now.getTime() + this.accessSeconds * 1000);
    const refreshExpiresAt = new Date(now.getTime() + this.refreshSeconds * 1000);
    const rotated = await this.store.rotateRefresh({
      tokenHash,
      deviceId,
      nextSessionId,
      nextAccessJtiHash: sha256(nextAccessJti),
      nextAccessExpiresAt: accessExpiresAt,
      nextRefreshTokenHash: sha256(nextRefreshToken),
      nextRefreshExpiresAt: refreshExpiresAt,
      now,
    });
    if (rotated.status === 'reuse') {
      throw new ApiError(401, 'refresh_token_reuse_detected', 'Refresh token reuse revoked the session family.');
    }
    if (rotated.status !== 'rotated') {
      throw new ApiError(401, 'invalid_refresh_token', 'Refresh token is invalid or expired.');
    }
    const claims = {
      iss: this.issuer,
      aud: this.audience,
      sub: rotated.userId,
      device_id: deviceId,
      jti: nextAccessJti,
      token_use: 'access',
      auth_method: rotated.authMethod,
      iat: Math.floor(now.getTime() / 1000),
      nbf: Math.floor(now.getTime() / 1000),
      exp: Math.floor(accessExpiresAt.getTime() / 1000),
    };
    const header = encodeJson({ alg: 'EdDSA', typ: 'JWT', kid: this.keyId });
    const payload = encodeJson(claims);
    const signingInput = `${header}.${payload}`;
    return {
      user_id: rotated.userId,
      device_id: deviceId,
      access_token: `${signingInput}.${signBytes(null, Buffer.from(signingInput), this.privateKey).toString('base64url')}`,
      access_token_expires_at: accessExpiresAt.toISOString(),
      refresh_token: nextRefreshToken,
      refresh_token_expires_at: refreshExpiresAt.toISOString(),
      token_type: 'Bearer',
    };
  }

  async logout(principal) {
    await this.store.revokeSession({
      accessJtiHash: sha256(principal.tokenId),
      userId: principal.userId,
      deviceId: principal.deviceId,
      now: this.clock(),
    });
    return { logged_out: true };
  }

  async linkTelegram({ telegramSubject, username, displayName, deviceId }) {
    if (typeof telegramSubject !== 'string' || telegramSubject.length < 1 || telegramSubject.length > 256) {
      throw new ApiError(401, 'telegram_exchange_failed', 'Telegram identity subject is invalid.');
    }
    const linked = await this.store.linkTelegram({
      telegramSubject,
      username,
      displayName,
      deviceId,
      now: this.clock(),
    });
    return this.issue({ userId: linked.userId, deviceId, authMethod: 'telegram' });
  }

  async close() {
    await this.store.close?.();
  }
}

export class PostgresAuthSessionStore {
  constructor(pool) {
    if (typeof pool?.connect !== 'function') throw new Error('PostgreSQL pool is required.');
    this.pool = pool;
    this.kind = 'postgres-auth-session-v1';
  }

  async transaction(work) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query("SET LOCAL statement_timeout = '10s'");
      await client.query("SET LOCAL lock_timeout = '3s'");
      const result = await work(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async consumeAuthNonce({ nonceDigest, expiresAt }) {
    const result = await this.pool.query(
      `INSERT INTO control_auth_proof_nonces (nonce_digest, expires_at)
       VALUES ($1, $2) ON CONFLICT DO NOTHING RETURNING nonce_digest`,
      [nonceDigest, expiresAt],
    );
    return result.rowCount === 1;
  }

  async registerGuest({ deviceId, keyVersion, signingPublicJwk, encryptionPublicJwk, now }) {
    return this.transaction(async (client) => {
      const existing = await client.query(
        `SELECT d.user_id, d.status, d.key_version, d.signing_public_jwk, d.encryption_public_jwk,
                u.status AS user_status, u.telegram_subject
           FROM control_devices d JOIN control_users u ON u.id = d.user_id
          WHERE d.id = $1 FOR UPDATE OF d, u`,
        [deviceId],
      );
      if (existing.rowCount === 1) {
        const row = existing.rows[0];
        const sameIdentity = safeSame(
          sha256(JSON.stringify(canonical(row.signing_public_jwk))),
          sha256(JSON.stringify(canonical(signingPublicJwk))),
        ) && safeSame(
          sha256(JSON.stringify(canonical(row.encryption_public_jwk))),
          sha256(JSON.stringify(canonical(encryptionPublicJwk))),
        ) && row.key_version === keyVersion;
        if (!sameIdentity || row.status !== 'active' || row.user_status !== 'active') {
          throw new ApiError(409, 'device_identity_conflict', 'Device ID is already bound to another or revoked key identity.');
        }
        await client.query('UPDATE control_devices SET last_seen_at = $2 WHERE id = $1', [deviceId, now]);
        return { userId: row.user_id, authMethod: row.telegram_subject ? 'telegram' : 'guest' };
      }
      const userId = randomUUID();
      await client.query(
        `INSERT INTO control_users (id, status, locale, created_at, updated_at)
         VALUES ($1, 'active', 'fa-IR', $2, $2)`,
        [userId, now],
      );
      await client.query(
        `INSERT INTO control_devices (
           id, user_id, status, signing_public_jwk, encryption_public_jwk,
           key_version, attestation_status, last_seen_at, created_at
         ) VALUES ($1, $2, 'active', $3, $4, $5, 'trusted', $6, $6)`,
        [deviceId, userId, signingPublicJwk, encryptionPublicJwk, keyVersion, now],
      );
      return { userId, authMethod: 'guest' };
    });
  }

  async createSession(value) {
    await this.transaction(async (client) => {
      await client.query(
        `INSERT INTO control_auth_sessions (
           jti_hash, user_id, device_id, expires_at, session_id, refresh_family_id, auth_method
         ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [value.accessJtiHash, value.userId, value.deviceId, value.accessExpiresAt,
          value.sessionId, value.familyId, value.authMethod],
      );
      await client.query(
        `INSERT INTO control_refresh_tokens (
           token_hash, session_id, family_id, user_id, device_id, parent_token_hash, expires_at
         ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [value.refreshTokenHash, value.sessionId, value.familyId, value.userId,
          value.deviceId, value.parentTokenHash, value.refreshExpiresAt],
      );
    });
  }

  async findRefreshForProof({ tokenHash, deviceId }) {
    const result = await this.pool.query(
      `SELECT r.device_id, d.key_version, d.signing_public_jwk
         FROM control_refresh_tokens r
         JOIN control_devices d ON d.id = r.device_id AND d.user_id = r.user_id
        WHERE r.token_hash = $1 AND r.device_id = $2 AND d.status = 'active'`,
      [tokenHash, deviceId],
    );
    return result.rowCount === 1 ? {
      keyVersion: result.rows[0].key_version,
      signingPublicJwk: result.rows[0].signing_public_jwk,
    } : null;
  }

  async rotateRefresh(value) {
    return this.transaction(async (client) => {
      const result = await client.query(
        `SELECT r.*, u.status AS user_status, d.status AS device_status, s.auth_method
           FROM control_refresh_tokens r
           JOIN control_users u ON u.id = r.user_id
           JOIN control_devices d ON d.id = r.device_id AND d.user_id = r.user_id
           JOIN control_auth_sessions s ON s.session_id = r.session_id
          WHERE r.token_hash = $1 AND r.device_id = $2
          FOR UPDATE OF r, u, d, s`,
        [value.tokenHash, value.deviceId],
      );
      if (result.rowCount !== 1) return { status: 'invalid' };
      const row = result.rows[0];
      if (row.consumed_at || row.replacement_token_hash || row.revoked_at) {
        await client.query(
          'UPDATE control_refresh_tokens SET revoked_at = COALESCE(revoked_at, $2) WHERE family_id = $1',
          [row.family_id, value.now],
        );
        await client.query(
          'UPDATE control_auth_sessions SET revoked_at = COALESCE(revoked_at, $2) WHERE refresh_family_id = $1',
          [row.family_id, value.now],
        );
        return { status: 'reuse' };
      }
      if (row.user_status !== 'active' || row.device_status !== 'active'
        || new Date(row.expires_at).getTime() <= value.now.getTime()) {
        await client.query('UPDATE control_refresh_tokens SET revoked_at = $2 WHERE token_hash = $1', [value.tokenHash, value.now]);
        return { status: 'invalid' };
      }
      await client.query(
        `UPDATE control_refresh_tokens
            SET consumed_at = $2, replacement_token_hash = $3
          WHERE token_hash = $1`,
        [value.tokenHash, value.now, value.nextRefreshTokenHash],
      );
      await client.query(
        'UPDATE control_auth_sessions SET revoked_at = $2 WHERE session_id = $1',
        [row.session_id, value.now],
      );
      const authMethod = row.auth_method ?? 'guest';
      await client.query(
        `INSERT INTO control_auth_sessions (
           jti_hash, user_id, device_id, expires_at, session_id, refresh_family_id, auth_method
         ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [value.nextAccessJtiHash, row.user_id, row.device_id, value.nextAccessExpiresAt,
          value.nextSessionId, row.family_id, authMethod],
      );
      await client.query(
        `INSERT INTO control_refresh_tokens (
           token_hash, session_id, family_id, user_id, device_id, parent_token_hash, expires_at
         ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [value.nextRefreshTokenHash, value.nextSessionId, row.family_id, row.user_id,
          row.device_id, value.tokenHash, value.nextRefreshExpiresAt],
      );
      return { status: 'rotated', userId: row.user_id, authMethod };
    });
  }

  async revokeSession({ accessJtiHash, userId, deviceId, now }) {
    await this.transaction(async (client) => {
      const result = await client.query(
        `UPDATE control_auth_sessions SET revoked_at = COALESCE(revoked_at, $4)
          WHERE jti_hash = $1 AND user_id = $2 AND device_id = $3
          RETURNING refresh_family_id`,
        [accessJtiHash, userId, deviceId, now],
      );
      const familyId = result.rows[0]?.refresh_family_id;
      if (familyId) {
        await client.query(
          'UPDATE control_refresh_tokens SET revoked_at = COALESCE(revoked_at, $2) WHERE family_id = $1',
          [familyId, now],
        );
        await client.query(
          'UPDATE control_auth_sessions SET revoked_at = COALESCE(revoked_at, $2) WHERE refresh_family_id = $1',
          [familyId, now],
        );
      }
    });
  }

  async createTelegramState(value) {
    await this.pool.query(
      `INSERT INTO control_telegram_login_states (
         state_digest, code_challenge, redirect_uri, device_id, user_id, expires_at
       ) VALUES ($1, $2, $3, $4, $5, $6)`,
      [value.stateDigest, value.codeChallenge, value.redirectUri, value.deviceId, value.userId, value.expiresAt],
    );
  }

  async linkTelegram({ telegramSubject, username, displayName, deviceId, now }) {
    return this.transaction(async (client) => {
      const device = await client.query(
        `SELECT d.user_id, u.telegram_subject
           FROM control_devices d JOIN control_users u ON u.id = d.user_id
          WHERE d.id = $1 AND d.status = 'active' AND u.status = 'active'
          FOR UPDATE OF d, u`,
        [deviceId],
      );
      if (device.rowCount !== 1) throw new ApiError(401, 'telegram_exchange_failed', 'Login device is unavailable.');
      const currentUserId = device.rows[0].user_id;
      const linked = await client.query(
        'SELECT id FROM control_users WHERE telegram_subject = $1 FOR UPDATE',
        [telegramSubject],
      );
      if (linked.rowCount === 1 && linked.rows[0].id !== currentUserId) {
        const holdings = await client.query(
          `SELECT EXISTS(SELECT 1 FROM control_services WHERE user_id = $1)
               OR EXISTS(SELECT 1 FROM control_orders WHERE user_id = $1) AS has_holdings`,
          [currentUserId],
        );
        if (holdings.rows[0].has_holdings) {
          throw new ApiError(409, 'telegram_account_merge_required', 'Guest entitlements require a reviewed account merge.');
        }
        const targetUserId = linked.rows[0].id;
        await client.query(
          'UPDATE control_auth_sessions SET revoked_at = COALESCE(revoked_at, $2) WHERE user_id = $1',
          [currentUserId, now],
        );
        await client.query(
          'UPDATE control_refresh_tokens SET revoked_at = COALESCE(revoked_at, $2) WHERE user_id = $1',
          [currentUserId, now],
        );
        await client.query('DELETE FROM control_refresh_tokens WHERE user_id = $1', [currentUserId]);
        await client.query('DELETE FROM control_auth_sessions WHERE user_id = $1', [currentUserId]);
        await client.query('DELETE FROM control_device_proof_nonces WHERE user_id = $1 AND device_id = $2', [currentUserId, deviceId]);
        await client.query('UPDATE control_devices SET user_id = $2 WHERE id = $1', [deviceId, targetUserId]);
        await client.query("UPDATE control_users SET status = 'deleted', updated_at = $2 WHERE id = $1", [currentUserId, now]);
        return { userId: targetUserId };
      }
      if (device.rows[0].telegram_subject && device.rows[0].telegram_subject !== telegramSubject) {
        throw new ApiError(409, 'telegram_identity_conflict', 'Account is already linked to another Telegram identity.');
      }
      await client.query(
        `UPDATE control_users SET telegram_subject = $2,
           display_name = COALESCE($3, display_name), updated_at = $4 WHERE id = $1`,
        [currentUserId, telegramSubject, displayName ?? username ?? null, now],
      );
      return { userId: currentUserId };
    });
  }

  async close() { await this.pool.end(); }
}

async function createPool(environment, applicationName) {
  if (!environment.DATABASE_URL) throw new Error('DATABASE_URL is required.');
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.AUTH_SESSION_DATABASE_POOL_MAX ?? 5),
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: applicationName,
  });
  await pool.query('SELECT 1');
  return pool;
}

function privateSigningJwk(environment) {
  if (!environment.AUTH_SESSION_SIGNING_PRIVATE_JWK) {
    throw new Error('AUTH_SESSION_SIGNING_PRIVATE_JWK is required.');
  }
  try {
    const value = JSON.parse(environment.AUTH_SESSION_SIGNING_PRIVATE_JWK);
    if (!value || typeof value !== 'object' || Array.isArray(value) || value.kty !== 'OKP'
      || value.crv !== 'Ed25519' || typeof value.d !== 'string') throw new Error();
    return value;
  } catch {
    throw new Error('AUTH_SESSION_SIGNING_PRIVATE_JWK must be valid private JWK JSON.');
  }
}

async function createService(environment, applicationName) {
  for (const required of ['AUTH_ISSUER', 'AUTH_AUDIENCE', 'AUTH_SESSION_SIGNING_KEY_ID']) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  const pool = await createPool(environment, applicationName);
  return new AuthSessionService({
    store: new PostgresAuthSessionStore(pool),
    signingPrivateJwk: privateSigningJwk(environment),
    signingKeyId: environment.AUTH_SESSION_SIGNING_KEY_ID,
    issuer: environment.AUTH_ISSUER,
    audience: environment.AUTH_AUDIENCE,
    telegramAuthorizationEndpoint: environment.TELEGRAM_OIDC_AUTHORIZATION_ENDPOINT,
    telegramClientId: environment.TELEGRAM_OIDC_CLIENT_ID,
    telegramRedirectUris: (environment.TELEGRAM_OIDC_REDIRECT_URIS ?? '').split(',').map((item) => item.trim()).filter(Boolean),
    accessSeconds: environment.AUTH_ACCESS_TOKEN_SECONDS,
    refreshSeconds: environment.AUTH_REFRESH_TOKEN_SECONDS,
  });
}

export async function createAuthSessionAdapter({ environment = process.env } = {}) {
  return createService(environment, 'ganj-vpn-auth-session');
}

export async function createTelegramAccountBroker({ environment = process.env } = {}) {
  const service = await createService(environment, 'ganj-vpn-telegram-account-broker');
  return {
    kind: 'postgres-telegram-account-broker-v1',
    linkAndIssueSession: (value) => service.linkTelegram(value),
    close: () => service.close(),
  };
}
