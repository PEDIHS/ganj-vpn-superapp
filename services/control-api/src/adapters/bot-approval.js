import {
  createHash,
  createHmac,
  randomBytes,
  randomUUID,
  timingSafeEqual,
} from 'node:crypto';
import { ApiError } from '../errors.js';

const BASE64URL_32 = /^[A-Za-z0-9_-]{43}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const BOT_USERNAME = /^[A-Za-z0-9_]{5,32}$/;
const DEFAULT_TTL_SECONDS = 10 * 60;
const DEFAULT_SIGNATURE_SKEW_SECONDS = 90;

function sha256(value, encoding = 'hex') {
  return createHash('sha256').update(value).digest(encoding);
}

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, stable(item)]),
    );
  }
  return value;
}

function canonicalJson(value) {
  return JSON.stringify(stable(value));
}

function safeSameHex(left, right) {
  if (!/^[0-9a-f]{64}$/.test(left ?? '') || !/^[0-9a-f]{64}$/.test(right ?? '')) return false;
  return timingSafeEqual(Buffer.from(left, 'hex'), Buffer.from(right, 'hex'));
}

function positiveDuration(name, raw, fallback, minimum, maximum) {
  const value = raw === undefined ? fallback : Number(raw);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} to ${maximum}.`);
  }
  return value;
}

function optionalBoundedString(value, maximum) {
  if (value === undefined || value === null || value === '') return null;
  if (typeof value !== 'string' || value.length > maximum || /[\u0000-\u001f\u007f]/.test(value)) {
    throw new ApiError(400, 'invalid_bot_approval', 'Bot approval identity metadata is invalid.');
  }
  return value;
}

export class TelegramBotApprovalService {
  constructor({
    store,
    botUsername,
    redirectUris,
    hmacSecret,
    clock = () => new Date(),
    ttlSeconds = DEFAULT_TTL_SECONDS,
    signatureSkewSeconds = DEFAULT_SIGNATURE_SKEW_SECONDS,
  }) {
    for (const method of ['create', 'findOwned', 'applyDecision', 'consumeApproved']) {
      if (typeof store?.[method] !== 'function') throw new Error(`Bot approval store is missing ${method}().`);
    }
    if (!BOT_USERNAME.test(botUsername ?? '')) throw new Error('GANJ_BOT_USERNAME is invalid.');
    if (!Array.isArray(redirectUris) || redirectUris.length === 0) {
      throw new Error('GANJ_BOT_APPROVAL_REDIRECT_URIS must contain at least one HTTPS redirect URI.');
    }
    this.redirectUris = new Set(redirectUris.map((value) => {
      const url = new URL(value);
      if (url.protocol !== 'https:' || url.username || url.password || url.hash) {
        throw new Error('Bot approval redirect URIs must be HTTPS URLs without credentials or fragments.');
      }
      return url.toString();
    }));
    if (typeof hmacSecret !== 'string' || Buffer.byteLength(hmacSecret, 'utf8') < 32) {
      throw new Error('GANJ_BOT_APPROVAL_HMAC_SECRET must contain at least 32 bytes.');
    }
    this.store = store;
    this.botUsername = botUsername;
    this.hmacSecret = Buffer.from(hmacSecret, 'utf8');
    this.clock = clock;
    this.ttlSeconds = positiveDuration('GANJ_BOT_APPROVAL_TTL_SECONDS', ttlSeconds, DEFAULT_TTL_SECONDS, 120, 900);
    this.signatureSkewSeconds = positiveDuration(
      'GANJ_BOT_APPROVAL_SIGNATURE_SKEW_SECONDS',
      signatureSkewSeconds,
      DEFAULT_SIGNATURE_SKEW_SECONDS,
      30,
      300,
    );
  }

  async start({ principal, codeChallenge, redirectUri }) {
    if (!UUID.test(principal?.userId ?? '') || !UUID.test(principal?.deviceId ?? '')) {
      throw new ApiError(401, 'authentication_required', 'A device-bound guest or linked session is required.');
    }
    if (!BASE64URL_32.test(codeChallenge ?? '') || !this.redirectUris.has(redirectUri)) {
      throw new ApiError(400, 'invalid_bot_approval', 'PKCE challenge or redirect URI is invalid.');
    }
    const requestId = randomUUID();
    const state = randomBytes(32).toString('base64url');
    const approvalToken = randomBytes(32).toString('base64url');
    const expiresAt = new Date(this.clock().getTime() + this.ttlSeconds * 1000);
    await this.store.create({
      requestId,
      userId: principal.userId,
      deviceId: principal.deviceId,
      stateDigest: sha256(state),
      approvalTokenDigest: sha256(approvalToken),
      codeChallenge,
      redirectUri,
      expiresAt,
      now: this.clock(),
    });
    const botUrl = new URL(`https://t.me/${this.botUsername}`);
    botUrl.searchParams.set('start', `ga_${approvalToken}`);
    return {
      request_id: requestId,
      bot_url: botUrl.toString(),
      state,
      expires_at: expiresAt.toISOString(),
    };
  }

  async status({ principal, requestId }) {
    if (!UUID.test(requestId ?? '')) throw new ApiError(400, 'invalid_bot_approval', 'Request ID is invalid.');
    const record = await this.store.findOwned({
      requestId,
      userId: principal.userId,
      deviceId: principal.deviceId,
    });
    if (!record) throw new ApiError(404, 'bot_approval_not_found', 'Approval request was not found.');
    const expired = new Date(record.expiresAt).getTime() <= this.clock().getTime();
    return {
      request_id: requestId,
      status: expired && record.status === 'pending' ? 'expired' : record.status,
      expires_at: new Date(record.expiresAt).toISOString(),
    };
  }

  verifyInternalSignature({ timestamp, signature, body }) {
    const timestampSeconds = Number(timestamp);
    const nowSeconds = Math.floor(this.clock().getTime() / 1000);
    if (!Number.isSafeInteger(timestampSeconds)
      || Math.abs(nowSeconds - timestampSeconds) > this.signatureSkewSeconds
      || !/^[0-9a-f]{64}$/.test(signature ?? '')) {
      throw new ApiError(401, 'invalid_bot_signature', 'Bot approval signature is invalid or expired.');
    }
    const message = `${timestampSeconds}.${canonicalJson(body)}`;
    const expected = createHmac('sha256', this.hmacSecret).update(message).digest('hex');
    if (!safeSameHex(expected, signature)) {
      throw new ApiError(401, 'invalid_bot_signature', 'Bot approval signature is invalid or expired.');
    }
  }

  async applyBotDecision({ timestamp, signature, body }) {
    this.verifyInternalSignature({ timestamp, signature, body });
    const approvalToken = body?.approval_token;
    const eventId = body?.event_id;
    const decision = body?.decision;
    if (!BASE64URL_32.test(approvalToken ?? '')
      || typeof eventId !== 'string' || eventId.length < 16 || eventId.length > 128
      || !/^[A-Za-z0-9._:-]+$/.test(eventId)
      || !['approve', 'deny'].includes(decision)) {
      throw new ApiError(400, 'invalid_bot_approval', 'Bot approval decision is malformed.');
    }
    const telegramSubject = decision === 'approve' ? body.telegram_subject : null;
    if (decision === 'approve'
      && (typeof telegramSubject !== 'string' || telegramSubject.length < 1 || telegramSubject.length > 256)) {
      throw new ApiError(400, 'invalid_bot_approval', 'Telegram identity is missing from approval.');
    }
    return this.store.applyDecision({
      approvalTokenDigest: sha256(approvalToken),
      eventId,
      decision,
      telegramSubject,
      username: optionalBoundedString(body.telegram_username, 64),
      displayName: optionalBoundedString(body.telegram_display_name, 200),
      now: this.clock(),
    });
  }

  async consume({ principal, requestId, state, codeVerifier }) {
    if (!UUID.test(requestId ?? '') || !BASE64URL_32.test(state ?? '')
      || typeof codeVerifier !== 'string' || codeVerifier.length < 43 || codeVerifier.length > 128
      || !/^[A-Za-z0-9._~-]+$/.test(codeVerifier)) {
      throw new ApiError(400, 'invalid_bot_approval', 'Approval exchange is malformed.');
    }
    const codeChallenge = sha256(codeVerifier, 'base64url');
    return this.store.consumeApproved({
      requestId,
      userId: principal.userId,
      deviceId: principal.deviceId,
      stateDigest: sha256(state),
      codeChallenge,
      now: this.clock(),
    });
  }

  async close() {
    await this.store.close?.();
  }
}

export class PostgresTelegramBotApprovalStore {
  constructor(pool) {
    if (typeof pool?.connect !== 'function') throw new Error('PostgreSQL pool is required.');
    this.pool = pool;
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

  async create(value) {
    await this.pool.query(
      `INSERT INTO control_telegram_bot_approvals (
         request_id, initiating_user_id, device_id, state_digest, approval_token_digest,
         code_challenge, redirect_uri, expires_at, created_at, updated_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $9)`,
      [value.requestId, value.userId, value.deviceId, value.stateDigest, value.approvalTokenDigest,
        value.codeChallenge, value.redirectUri, value.expiresAt, value.now],
    );
  }

  async findOwned({ requestId, userId, deviceId }) {
    const result = await this.pool.query(
      `SELECT request_id, status, expires_at
         FROM control_telegram_bot_approvals
        WHERE request_id = $1 AND initiating_user_id = $2 AND device_id = $3`,
      [requestId, userId, deviceId],
    );
    return result.rowCount === 1 ? {
      requestId: result.rows[0].request_id,
      status: result.rows[0].status,
      expiresAt: result.rows[0].expires_at,
    } : null;
  }

  async applyDecision(value) {
    return this.transaction(async (client) => {
      const result = await client.query(
        `SELECT * FROM control_telegram_bot_approvals
          WHERE approval_token_digest = $1 FOR UPDATE`,
        [value.approvalTokenDigest],
      );
      if (result.rowCount !== 1) throw new ApiError(404, 'bot_approval_not_found', 'Approval request was not found.');
      const row = result.rows[0];
      if (new Date(row.expires_at).getTime() <= value.now.getTime()) {
        throw new ApiError(410, 'bot_approval_expired', 'Approval request has expired.');
      }
      if (row.approval_event_id === value.eventId) {
        return { accepted: true, replayed: true, request_id: row.request_id, status: row.status };
      }
      if (row.status !== 'pending') {
        throw new ApiError(409, 'bot_approval_already_decided', 'Approval request was already decided.');
      }
      const status = value.decision === 'approve' ? 'approved' : 'denied';
      await client.query(
        `UPDATE control_telegram_bot_approvals SET
           status = $2,
           telegram_subject = $3,
           telegram_username = $4,
           telegram_display_name = $5,
           approval_event_id = $6,
           approved_at = CASE WHEN $2 = 'approved' THEN $7 ELSE NULL END,
           denied_at = CASE WHEN $2 = 'denied' THEN $7 ELSE NULL END,
           updated_at = $7
         WHERE request_id = $1`,
        [row.request_id, status, value.telegramSubject, value.username, value.displayName, value.eventId, value.now],
      );
      return { accepted: true, replayed: false, request_id: row.request_id, status };
    });
  }

  async consumeApproved(value) {
    return this.transaction(async (client) => {
      const result = await client.query(
        `SELECT * FROM control_telegram_bot_approvals
          WHERE request_id = $1 AND initiating_user_id = $2 AND device_id = $3 FOR UPDATE`,
        [value.requestId, value.userId, value.deviceId],
      );
      if (result.rowCount !== 1) throw new ApiError(404, 'bot_approval_not_found', 'Approval request was not found.');
      const row = result.rows[0];
      if (new Date(row.expires_at).getTime() <= value.now.getTime()) {
        throw new ApiError(410, 'bot_approval_expired', 'Approval request has expired.');
      }
      if (!safeSameHex(row.state_digest, value.stateDigest) || row.code_challenge !== value.codeChallenge) {
        throw new ApiError(401, 'bot_approval_binding_mismatch', 'Approval request binding is invalid.');
      }
      if (row.status === 'denied') throw new ApiError(403, 'bot_approval_denied', 'Telegram approval was denied.');
      if (row.status === 'pending') throw new ApiError(409, 'bot_approval_pending', 'Telegram approval is still pending.');
      if (row.status === 'consumed') throw new ApiError(409, 'bot_approval_replayed', 'Telegram approval was already consumed.');
      if (row.status !== 'approved' || !row.telegram_subject) {
        throw new ApiError(409, 'bot_approval_invalid_state', 'Telegram approval is not exchangeable.');
      }
      await client.query(
        `UPDATE control_telegram_bot_approvals
            SET status = 'consumed', consumed_at = $2, updated_at = $2
          WHERE request_id = $1`,
        [row.request_id, value.now],
      );
      return {
        requestId: row.request_id,
        redirectUri: row.redirect_uri,
        telegramSubject: row.telegram_subject,
        username: row.telegram_username,
        displayName: row.telegram_display_name,
      };
    });
  }

  async close() {
    await this.pool.end();
  }
}

export class InMemoryTelegramBotApprovalStore {
  constructor() {
    this.records = new Map();
    this.events = new Map();
  }

  async create(value) {
    this.records.set(value.requestId, { ...value, status: 'pending', telegramSubject: null });
  }

  async findOwned({ requestId, userId, deviceId }) {
    const row = this.records.get(requestId);
    return row && row.userId === userId && row.deviceId === deviceId
      ? { requestId, status: row.status, expiresAt: row.expiresAt }
      : null;
  }

  async applyDecision(value) {
    const row = [...this.records.values()].find((item) => item.approvalTokenDigest === value.approvalTokenDigest);
    if (!row) throw new ApiError(404, 'bot_approval_not_found', 'Approval request was not found.');
    if (row.expiresAt.getTime() <= value.now.getTime()) throw new ApiError(410, 'bot_approval_expired', 'Approval request has expired.');
    if (this.events.get(value.eventId) === row.requestId) {
      return { accepted: true, replayed: true, request_id: row.requestId, status: row.status };
    }
    if (row.status !== 'pending') throw new ApiError(409, 'bot_approval_already_decided', 'Approval request was already decided.');
    row.status = value.decision === 'approve' ? 'approved' : 'denied';
    row.telegramSubject = value.telegramSubject;
    row.username = value.username;
    row.displayName = value.displayName;
    row.eventId = value.eventId;
    this.events.set(value.eventId, row.requestId);
    return { accepted: true, replayed: false, request_id: row.requestId, status: row.status };
  }

  async consumeApproved(value) {
    const row = this.records.get(value.requestId);
    if (!row || row.userId !== value.userId || row.deviceId !== value.deviceId) {
      throw new ApiError(404, 'bot_approval_not_found', 'Approval request was not found.');
    }
    if (row.expiresAt.getTime() <= value.now.getTime()) throw new ApiError(410, 'bot_approval_expired', 'Approval request has expired.');
    if (!safeSameHex(row.stateDigest, value.stateDigest) || row.codeChallenge !== value.codeChallenge) {
      throw new ApiError(401, 'bot_approval_binding_mismatch', 'Approval request binding is invalid.');
    }
    if (row.status === 'pending') throw new ApiError(409, 'bot_approval_pending', 'Telegram approval is still pending.');
    if (row.status === 'denied') throw new ApiError(403, 'bot_approval_denied', 'Telegram approval was denied.');
    if (row.status === 'consumed') throw new ApiError(409, 'bot_approval_replayed', 'Telegram approval was already consumed.');
    row.status = 'consumed';
    return {
      requestId: row.requestId,
      redirectUri: row.redirectUri,
      telegramSubject: row.telegramSubject,
      username: row.username,
      displayName: row.displayName,
    };
  }
}

async function createPool(environment) {
  if (!environment.DATABASE_URL) throw new Error('DATABASE_URL is required.');
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.BOT_APPROVAL_DATABASE_POOL_MAX ?? 3),
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-bot-approval',
  });
  await pool.query('SELECT 1');
  return pool;
}

function redirectUris(environment) {
  return (environment.GANJ_BOT_APPROVAL_REDIRECT_URIS || environment.TELEGRAM_OIDC_REDIRECT_URIS || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

export async function createBotApprovalAdapter({ environment = process.env } = {}) {
  const pool = await createPool(environment);
  return new TelegramBotApprovalService({
    store: new PostgresTelegramBotApprovalStore(pool),
    botUsername: environment.GANJ_BOT_USERNAME,
    redirectUris: redirectUris(environment),
    hmacSecret: environment.GANJ_BOT_APPROVAL_HMAC_SECRET,
    ttlSeconds: environment.GANJ_BOT_APPROVAL_TTL_SECONDS,
    signatureSkewSeconds: environment.GANJ_BOT_APPROVAL_SIGNATURE_SKEW_SECONDS,
  });
}

export function createTestBotApprovalAdapter({ environment = process.env } = {}) {
  return new TelegramBotApprovalService({
    store: new InMemoryTelegramBotApprovalStore(),
    botUsername: environment.GANJ_BOT_USERNAME || 'GanjTestBot',
    redirectUris: redirectUris(environment).length > 0
      ? redirectUris(environment)
      : ['https://auth.invalid/telegram/callback'],
    hmacSecret: environment.GANJ_BOT_APPROVAL_HMAC_SECRET || 'test-only-bot-approval-secret-32-bytes-minimum',
    clock: () => new Date(),
  });
}
