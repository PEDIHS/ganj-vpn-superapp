import {
  createHash,
  createHmac,
  randomBytes,
  timingSafeEqual,
} from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { ApiError } from '../errors.js';

const BASE64URL_32 = /^[A-Za-z0-9_-]{43}$/;
const TELEGRAM_SUBJECT = /^[1-9][0-9]{0,19}$/;
const TELEGRAM_USERNAME = /^[A-Za-z0-9_]{5,32}$/;
const STATE_SECONDS = 10 * 60;

function digest(value) {
  return createHash('sha256').update(value).digest('hex');
}

function safeSame(left, right) {
  const a = Buffer.from(String(left ?? ''));
  const b = Buffer.from(String(right ?? ''));
  return a.length === b.length && timingSafeEqual(a, b);
}

function requireSecret(name, value) {
  if (typeof value !== 'string' || value.length < 32 || value.length > 4096) {
    throw new Error(`${name} must contain 32 to 4096 characters.`);
  }
  return value;
}

function normalizeBearer(value) {
  if (typeof value !== 'string' || !value.startsWith('Bearer ')) return null;
  const token = value.slice(7);
  return token.length >= 32 && token.length <= 4096 ? token : null;
}

function normalizedText(value, maximum) {
  if (value == null) return null;
  if (typeof value !== 'string') return null;
  const text = value.trim();
  return text.length >= 1 && text.length <= maximum ? text : null;
}

async function loadFactory(specifier, exportName, environment) {
  if (!specifier) throw new Error(`${exportName} module is required.`);
  const target = specifier.startsWith('.') || specifier.startsWith('/')
    ? pathToFileURL(resolve(specifier)).href
    : specifier;
  const loaded = await import(target);
  if (typeof loaded[exportName] !== 'function') {
    throw new Error(`${specifier} must export ${exportName}().`);
  }
  return loaded[exportName]({ environment });
}

export class TelegramBotApprovalAdapter {
  constructor({
    store,
    accountBroker,
    botUsername,
    redirectUris,
    internalToken,
    codeKey,
    clock = () => new Date(),
    stateSeconds = STATE_SECONDS,
  }) {
    const storeMethods = [
      'create', 'approve', 'cancel', 'findStatus', 'loadForExchange',
      'prepareAccountLink', 'markConsumed', 'restoreApproved',
    ];
    if (!store || storeMethods.some((method) => typeof store[method] !== 'function')) {
      throw new Error('Telegram Bot approval store is incomplete.');
    }
    if (typeof accountBroker?.linkAndIssueSession !== 'function') {
      throw new Error('Telegram account broker is incomplete.');
    }
    if (!TELEGRAM_USERNAME.test(botUsername ?? '')) {
      throw new Error('TELEGRAM_BOT_USERNAME is invalid.');
    }
    const allowedRedirects = new Set(redirectUris ?? []);
    if (allowedRedirects.size === 0 || [...allowedRedirects].some((item) => {
      try {
        const url = new URL(item);
        return url.protocol !== 'https:' || Boolean(url.username || url.password || url.hash);
      } catch {
        return true;
      }
    })) {
      throw new Error('Telegram Bot approval redirect URI allow-list is invalid.');
    }
    if (!Number.isInteger(stateSeconds) || stateSeconds < 120 || stateSeconds > 900) {
      throw new Error('Telegram Bot approval state TTL is invalid.');
    }
    this.kind = 'telegram-bot-approval-pkce-v1';
    this.store = store;
    this.accountBroker = accountBroker;
    this.botUsername = botUsername;
    this.redirectUris = allowedRedirects;
    this.internalTokenHash = digest(requireSecret('TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN', internalToken));
    this.codeKey = Buffer.from(requireSecret('TELEGRAM_BOT_APPROVAL_CODE_KEY', codeKey), 'utf8');
    this.clock = clock;
    this.stateSeconds = stateSeconds;
  }

  async start({ principal, codeChallenge, redirectUri }) {
    if (!principal || typeof principal.userId !== 'string' || typeof principal.deviceId !== 'string') {
      throw new ApiError(401, 'telegram_bot_login_requires_session', 'A valid guest/device session is required.');
    }
    if (!BASE64URL_32.test(codeChallenge ?? '') || !this.redirectUris.has(redirectUri)) {
      throw new ApiError(400, 'invalid_telegram_bot_login', 'Telegram Bot PKCE challenge or redirect URI is invalid.');
    }
    const state = randomBytes(32).toString('base64url');
    const requestToken = randomBytes(32).toString('base64url');
    const now = this.clock();
    const expiresAt = new Date(now.getTime() + this.stateSeconds * 1000);
    await this.store.create({
      stateDigest: digest(state),
      requestDigest: digest(requestToken),
      codeChallenge,
      redirectUri,
      userId: principal.userId,
      deviceId: principal.deviceId,
      expiresAt,
      now,
    });
    return {
      approval_url: `https://t.me/${this.botUsername}?start=APP-${requestToken}`,
      state,
      expires_at: expiresAt.toISOString(),
    };
  }

  async decide({ authorization, requestToken, action, telegramUserId, username, displayName }) {
    const bearer = normalizeBearer(authorization);
    if (!bearer || !safeSame(digest(bearer), this.internalTokenHash)) {
      throw new ApiError(401, 'telegram_bot_approval_unauthorized', 'Telegram Bot approval authentication failed.');
    }
    if (!BASE64URL_32.test(requestToken ?? '')) {
      throw new ApiError(400, 'invalid_telegram_bot_request', 'Telegram Bot approval request is invalid.');
    }
    const now = this.clock();
    if (action === 'cancel') {
      const cancelled = await this.store.cancel({ requestDigest: digest(requestToken), now });
      if (!cancelled) {
        throw new ApiError(409, 'telegram_bot_request_unavailable', 'Telegram Bot approval request is unavailable.');
      }
      return { status: 'cancelled' };
    }
    if (action !== 'approve' || !TELEGRAM_SUBJECT.test(String(telegramUserId ?? ''))) {
      throw new ApiError(400, 'invalid_telegram_bot_decision', 'Telegram Bot approval decision is invalid.');
    }
    const normalizedUsername = username == null ? null : normalizedText(username, 32);
    if (username != null && (!normalizedUsername || !TELEGRAM_USERNAME.test(normalizedUsername))) {
      throw new ApiError(400, 'invalid_telegram_bot_identity', 'Telegram username is invalid.');
    }
    const normalizedDisplayName = displayName == null ? null : normalizedText(displayName, 160);
    if (displayName != null && !normalizedDisplayName) {
      throw new ApiError(400, 'invalid_telegram_bot_identity', 'Telegram display name is invalid.');
    }
    const approved = await this.store.approve({
      requestDigest: digest(requestToken),
      telegramSubject: String(telegramUserId),
      username: normalizedUsername,
      displayName: normalizedDisplayName,
      now,
    });
    if (approved === 'conflict') {
      throw new ApiError(409, 'telegram_bot_identity_conflict', 'Approval request was already confirmed by another Telegram identity.');
    }
    if (!approved) {
      throw new ApiError(409, 'telegram_bot_request_unavailable', 'Telegram Bot approval request is unavailable.');
    }
    return { status: 'approved' };
  }

  async status({ principal, state }) {
    if (!principal || !BASE64URL_32.test(state ?? '')) {
      throw new ApiError(400, 'invalid_telegram_bot_state', 'Telegram Bot approval state is invalid.');
    }
    const row = await this.store.findStatus({
      stateDigest: digest(state),
      userId: principal.userId,
      deviceId: principal.deviceId,
      now: this.clock(),
    });
    if (!row) {
      throw new ApiError(404, 'telegram_bot_state_not_found', 'Telegram Bot approval state was not found for this device.');
    }
    if (['expired', 'cancelled', 'consumed'].includes(row.status)) {
      return { status: row.status, expires_at: row.expiresAt.toISOString() };
    }
    if (row.status !== 'approved') {
      return { status: 'pending', expires_at: row.expiresAt.toISOString() };
    }
    return {
      status: 'approved',
      code: this.exchangeCode(row),
      expires_at: row.expiresAt.toISOString(),
    };
  }

  async exchangeAuthorizationCode({ principal, code, state, codeVerifier }) {
    if (!principal || typeof principal.userId !== 'string' || typeof principal.deviceId !== 'string'
      || !BASE64URL_32.test(code ?? '') || !BASE64URL_32.test(state ?? '')
      || typeof codeVerifier !== 'string' || codeVerifier.length < 43 || codeVerifier.length > 128
      || !/^[A-Za-z0-9._~-]+$/.test(codeVerifier)) {
      throw new ApiError(400, 'invalid_telegram_bot_exchange', 'Telegram Bot exchange input is invalid.');
    }
    const stateDigest = digest(state);
    const row = await this.store.loadForExchange({
      stateDigest,
      userId: principal.userId,
      deviceId: principal.deviceId,
      now: this.clock(),
    });
    if (!row || row.status !== 'approved') {
      throw new ApiError(400, 'invalid_telegram_bot_exchange', 'Telegram Bot approval is invalid, expired, wrong-device, or unavailable.');
    }
    const challenge = createHash('sha256').update(codeVerifier).digest('base64url');
    if (!safeSame(challenge, row.codeChallenge) || !safeSame(code, this.exchangeCode(row))) {
      throw new ApiError(400, 'invalid_telegram_bot_exchange', 'Telegram Bot exchange state, code, or PKCE verifier is invalid.');
    }
    const mergePreparation = await this.store.prepareAccountLink({
      deviceId: row.deviceId,
      telegramSubject: row.telegramSubject,
      now: this.clock(),
    });
    if (mergePreparation === 'merge_required') {
      throw new ApiError(409, 'telegram_account_merge_required', 'Paid guest holdings require a reviewed account merge.');
    }
    const consumed = await this.store.markConsumed({
      stateDigest,
      userId: principal.userId,
      deviceId: principal.deviceId,
      now: this.clock(),
    });
    if (!consumed) {
      throw new ApiError(409, 'telegram_bot_exchange_replayed', 'Telegram Bot exchange was already consumed.');
    }
    try {
      return await this.accountBroker.linkAndIssueSession({
        telegramSubject: row.telegramSubject,
        username: row.username,
        displayName: row.displayName,
        deviceId: row.deviceId,
        providerClaims: { method: 'bot-approval' },
      });
    } catch (error) {
      const restored = await this.store.restoreApproved({
        stateDigest,
        userId: principal.userId,
        deviceId: principal.deviceId,
        consumedAt: this.clock(),
      }).catch(() => false);
      if (!restored) {
        throw new ApiError(
          503,
          'telegram_bot_exchange_recovery_failed',
          'Telegram login could not be recovered safely. Start a new approval.',
          { retryable: true },
        );
      }
      throw error;
    }
  }

  exchangeCode(row) {
    return createHmac('sha256', this.codeKey)
      .update([
        'GANJ-TELEGRAM-BOT-APPROVAL-V1',
        row.stateDigest,
        row.deviceId,
        row.telegramSubject,
        row.expiresAt.toISOString(),
      ].join('\n'), 'utf8')
      .digest('base64url');
  }

  async close() {
    this.codeKey.fill(0);
    await Promise.allSettled([this.store.close?.(), this.accountBroker.close?.()]);
  }
}

export class PostgresTelegramBotApprovalStore {
  constructor(pool) {
    if (typeof pool?.connect !== 'function' || typeof pool?.query !== 'function') {
      throw new Error('PostgreSQL pool is required.');
    }
    this.pool = pool;
  }

  async create(value) {
    await this.pool.query(
      `INSERT INTO control_telegram_bot_approvals (
         state_digest, request_digest, code_challenge, redirect_uri,
         user_id, device_id, expires_at, created_at
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [value.stateDigest, value.requestDigest, value.codeChallenge, value.redirectUri,
        value.userId, value.deviceId, value.expiresAt, value.now],
    );
  }

  async approve({ requestDigest, telegramSubject, username, displayName, now }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await client.query(
        `SELECT telegram_subject, approved_at, cancelled_at, consumed_at, expires_at
           FROM control_telegram_bot_approvals
          WHERE request_digest = $1 FOR UPDATE`,
        [requestDigest],
      );
      if (result.rowCount !== 1) {
        await client.query('ROLLBACK');
        return false;
      }
      const row = result.rows[0];
      if (row.cancelled_at || row.consumed_at || new Date(row.expires_at).getTime() <= now.getTime()) {
        await client.query('ROLLBACK');
        return false;
      }
      if (row.approved_at) {
        await client.query('COMMIT');
        return row.telegram_subject === telegramSubject ? true : 'conflict';
      }
      await client.query(
        `UPDATE control_telegram_bot_approvals
            SET telegram_subject = $2, telegram_username = $3,
                telegram_display_name = $4, approved_at = $5
          WHERE request_digest = $1`,
        [requestDigest, telegramSubject, username, displayName, now],
      );
      await client.query('COMMIT');
      return true;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async cancel({ requestDigest, now }) {
    const result = await this.pool.query(
      `UPDATE control_telegram_bot_approvals
          SET cancelled_at = $2
        WHERE request_digest = $1
          AND approved_at IS NULL AND cancelled_at IS NULL AND consumed_at IS NULL
          AND expires_at > $2
        RETURNING request_digest`,
      [requestDigest, now],
    );
    return result.rowCount === 1;
  }

  async findStatus({ stateDigest, userId, deviceId, now }) {
    const result = await this.pool.query(
      `SELECT state_digest, code_challenge, user_id, device_id, telegram_subject,
              telegram_username, telegram_display_name, expires_at,
              approved_at, cancelled_at, consumed_at
         FROM control_telegram_bot_approvals
        WHERE state_digest = $1 AND user_id = $2 AND device_id = $3`,
      [stateDigest, userId, deviceId],
    );
    if (result.rowCount !== 1) return null;
    return mapRow(result.rows[0], now);
  }

  async loadForExchange({ stateDigest, userId, deviceId, now }) {
    const result = await this.pool.query(
      `SELECT state_digest, code_challenge, user_id, device_id, telegram_subject,
              telegram_username, telegram_display_name, expires_at,
              approved_at, cancelled_at, consumed_at
         FROM control_telegram_bot_approvals
        WHERE state_digest = $1 AND user_id = $2 AND device_id = $3`,
      [stateDigest, userId, deviceId],
    );
    if (result.rowCount !== 1) return null;
    return mapRow(result.rows[0], now);
  }

  async prepareAccountLink({ deviceId, telegramSubject, now }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const deviceResult = await client.query(
        `SELECT d.user_id, u.telegram_subject
           FROM control_devices d
           JOIN control_users u ON u.id = d.user_id
          WHERE d.id = $1 AND d.status = 'active' AND u.status = 'active'
          FOR UPDATE OF d, u`,
        [deviceId],
      );
      if (deviceResult.rowCount !== 1) {
        await client.query('ROLLBACK');
        return 'merge_required';
      }
      const currentUserId = deviceResult.rows[0].user_id;
      const currentTelegramSubject = deviceResult.rows[0].telegram_subject;
      if (currentTelegramSubject && currentTelegramSubject !== telegramSubject) {
        await client.query('ROLLBACK');
        return 'merge_required';
      }
      const target = await client.query(
        'SELECT id FROM control_users WHERE telegram_subject = $1 FOR UPDATE',
        [telegramSubject],
      );
      if (target.rowCount !== 1 || target.rows[0].id === currentUserId) {
        await client.query('COMMIT');
        return 'ready';
      }
      const holdings = await client.query(
        `SELECT EXISTS(SELECT 1 FROM control_orders WHERE user_id = $1)
             OR EXISTS(SELECT 1 FROM control_services WHERE user_id = $1 AND tier <> 'free') AS has_paid_holdings`,
        [currentUserId],
      );
      if (holdings.rows[0].has_paid_holdings) {
        await client.query('ROLLBACK');
        return 'merge_required';
      }
      await client.query('COMMIT');
      return 'ready';
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async markConsumed({ stateDigest, userId, deviceId, now }) {
    const result = await this.pool.query(
      `UPDATE control_telegram_bot_approvals
          SET consumed_at = $4
        WHERE state_digest = $1 AND user_id = $2 AND device_id = $3
          AND approved_at IS NOT NULL AND cancelled_at IS NULL
          AND consumed_at IS NULL AND expires_at > $4
        RETURNING state_digest`,
      [stateDigest, userId, deviceId, now],
    );
    return result.rowCount === 1;
  }

  async restoreApproved({ stateDigest, userId, deviceId, consumedAt }) {
    const result = await this.pool.query(
      `UPDATE control_telegram_bot_approvals
          SET consumed_at = NULL
        WHERE state_digest = $1 AND user_id = $2 AND device_id = $3
          AND consumed_at IS NOT NULL AND cancelled_at IS NULL
          AND expires_at > $4
        RETURNING state_digest`,
      [stateDigest, userId, deviceId, consumedAt],
    );
    return result.rowCount === 1;
  }

  async close() {
    await this.pool.end();
  }
}

function mapRow(row, now) {
  const expiresAt = new Date(row.expires_at);
  let status = 'pending';
  if (row.consumed_at) status = 'consumed';
  else if (row.cancelled_at) status = 'cancelled';
  else if (expiresAt.getTime() <= now.getTime()) status = 'expired';
  else if (row.approved_at && row.telegram_subject) status = 'approved';
  return {
    status,
    stateDigest: row.state_digest,
    codeChallenge: row.code_challenge,
    userId: row.user_id,
    deviceId: row.device_id,
    telegramSubject: row.telegram_subject,
    username: row.telegram_username,
    displayName: row.telegram_display_name,
    expiresAt,
  };
}

export async function createTelegramBotApprovalAdapter({ environment = process.env } = {}) {
  for (const required of [
    'DATABASE_URL', 'TELEGRAM_BOT_USERNAME', 'TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN',
    'TELEGRAM_BOT_APPROVAL_CODE_KEY', 'TELEGRAM_BOT_REDIRECT_URIS',
    'CONTROL_API_TELEGRAM_ACCOUNT_BROKER_MODULE',
  ]) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: environment.DATABASE_URL,
    max: Number(environment.TELEGRAM_BOT_APPROVAL_DATABASE_POOL_MAX ?? 3),
    connectionTimeoutMillis: 5_000,
    ssl: environment.DATABASE_SSL === 'require' ? { rejectUnauthorized: true } : undefined,
    application_name: 'ganj-vpn-telegram-bot-approval',
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
  return new TelegramBotApprovalAdapter({
    store: new PostgresTelegramBotApprovalStore(pool),
    accountBroker,
    botUsername: environment.TELEGRAM_BOT_USERNAME,
    redirectUris: environment.TELEGRAM_BOT_REDIRECT_URIS
      .split(',').map((item) => item.trim()).filter(Boolean),
    internalToken: environment.TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN,
    codeKey: environment.TELEGRAM_BOT_APPROVAL_CODE_KEY,
  });
}
