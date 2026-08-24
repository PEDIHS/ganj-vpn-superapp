import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { ApiError } from '../errors.js';
import { JwksJwtVerifier } from '../security/jwt.js';

async function loadFactory(specifier, exportName, environment) {
  if (!specifier) throw new Error(`${exportName} module is required.`);
  const target = specifier.startsWith('.') || specifier.startsWith('/')
    ? pathToFileURL(resolve(specifier)).href
    : specifier;
  const loaded = await import(target);
  if (typeof loaded[exportName] !== 'function') throw new Error(`${specifier} must export ${exportName}().`);
  return loaded[exportName]({ environment });
}

class PostgresLoginStateStore {
  constructor(pool) { this.pool = pool; }

  async consume({ state, codeVerifier, now = new Date() }) {
    const stateDigest = createHash('sha256').update(state).digest('hex');
    const challenge = createHash('sha256').update(codeVerifier).digest('base64url');
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await client.query(
        `SELECT code_challenge, redirect_uri, device_id, expires_at, consumed_at
           FROM control_telegram_login_states WHERE state_digest = $1 FOR UPDATE`,
        [stateDigest],
      );
      if (result.rowCount !== 1) throw new ApiError(400, 'invalid_login_state', 'Telegram login state is invalid.');
      const row = result.rows[0];
      if (row.consumed_at || new Date(row.expires_at).getTime() <= now.getTime() || row.code_challenge !== challenge) {
        throw new ApiError(400, 'invalid_login_state', 'Telegram login state is invalid, expired, consumed, or PKCE-mismatched.');
      }
      await client.query('UPDATE control_telegram_login_states SET consumed_at = $2 WHERE state_digest = $1', [stateDigest, now]);
      await client.query('COMMIT');
      return { redirectUri: row.redirect_uri, deviceId: row.device_id };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async close() { await this.pool.end(); }
}

export class TelegramOidcExchangeAdapter {
  constructor({ tokenEndpoint, clientId, clientSecret, stateStore, idTokenVerifier, accountBroker, fetchImpl = fetch, clock = () => new Date() }) {
    const endpoint = new URL(tokenEndpoint);
    if (endpoint.protocol !== 'https:') throw new Error('Telegram token endpoint must use HTTPS.');
    if (!clientId || typeof stateStore?.consume !== 'function' || typeof idTokenVerifier?.verify !== 'function'
      || typeof accountBroker?.linkAndIssueSession !== 'function') {
      throw new Error('Telegram OIDC dependencies are incomplete.');
    }
    this.kind = 'telegram-oidc-code-pkce-v1';
    this.tokenEndpoint = endpoint;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.stateStore = stateStore;
    this.idTokenVerifier = idTokenVerifier;
    this.accountBroker = accountBroker;
    this.fetchImpl = fetchImpl;
    this.clock = clock;
  }

  async exchangeAuthorizationCode({ code, state, codeVerifier }) {
    const login = await this.stateStore.consume({ state, codeVerifier, now: this.clock() });
    const form = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      code_verifier: codeVerifier,
      client_id: this.clientId,
      redirect_uri: login.redirectUri,
    });
    if (this.clientSecret) form.set('client_secret', this.clientSecret);
    const response = await this.fetchImpl(this.tokenEndpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
      body: form,
      redirect: 'error',
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new ApiError(401, 'telegram_exchange_failed', 'Telegram authorization code was rejected.');
    const raw = await response.text();
    if (Buffer.byteLength(raw, 'utf8') > 65_536) throw new ApiError(401, 'telegram_exchange_failed', 'Telegram token response is invalid.');
    let tokenResponse;
    try { tokenResponse = JSON.parse(raw); } catch { throw new ApiError(401, 'telegram_exchange_failed', 'Telegram token response is invalid.'); }
    if (typeof tokenResponse.id_token !== 'string') throw new ApiError(401, 'telegram_exchange_failed', 'Telegram identity token is missing.');
    let claims;
    try { claims = await this.idTokenVerifier.verify(tokenResponse.id_token); }
    catch { throw new ApiError(401, 'telegram_exchange_failed', 'Telegram identity token is invalid.'); }
    if (typeof claims.sub !== 'string' || claims.sub.length === 0) {
      throw new ApiError(401, 'telegram_exchange_failed', 'Telegram identity subject is missing.');
    }
    return this.accountBroker.linkAndIssueSession({
      telegramSubject: claims.sub,
      username: typeof claims.preferred_username === 'string' ? claims.preferred_username : null,
      displayName: typeof claims.name === 'string' ? claims.name : null,
      deviceId: login.deviceId,
      providerClaims: { auth_time: claims.auth_time ?? null, amr: claims.amr ?? [] },
    });
  }

  async close() {
    await Promise.allSettled([this.stateStore.close?.(), this.accountBroker.close?.()]);
  }
}

export async function createTelegramAuthAdapter({ environment = process.env } = {}) {
  for (const required of [
    'TELEGRAM_OIDC_TOKEN_ENDPOINT', 'TELEGRAM_OIDC_JWKS_URI', 'TELEGRAM_OIDC_ISSUER',
    'TELEGRAM_OIDC_CLIENT_ID', 'DATABASE_URL', 'CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE',
  ]) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.TELEGRAM_DATABASE_POOL_MAX ?? 3),
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-telegram-exchange',
  });
  await pool.query('SELECT 1');
  const accountBroker = await loadFactory(
    environment.CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE,
    'createTelegramAccountBroker',
    environment,
  );
  if (environment.NODE_ENV === 'production' && accountBroker?.kind === 'test-only') {
    await pool.end();
    throw new Error('Test Telegram account broker is forbidden in production.');
  }
  return new TelegramOidcExchangeAdapter({
    tokenEndpoint: environment.TELEGRAM_OIDC_TOKEN_ENDPOINT,
    clientId: environment.TELEGRAM_OIDC_CLIENT_ID,
    clientSecret: environment.TELEGRAM_OIDC_CLIENT_SECRET,
    stateStore: new PostgresLoginStateStore(pool),
    idTokenVerifier: new JwksJwtVerifier({
      jwksUri: environment.TELEGRAM_OIDC_JWKS_URI,
      issuer: environment.TELEGRAM_OIDC_ISSUER,
      audience: environment.TELEGRAM_OIDC_CLIENT_ID,
      allowedAlgorithms: (environment.TELEGRAM_OIDC_ALLOWED_ALGORITHMS ?? 'RS256').split(',').map((item) => item.trim()),
    }),
    accountBroker,
  });
}
