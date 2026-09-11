import { createHash, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { ApiError, failure, rejectUnknown, requireObject, requireString, success } from './errors.js';

const APPROVAL_TTL_MS = 10 * 60 * 1000;
const BOT_USERNAME = /^[A-Za-z0-9_]{5,32}$/;
const TELEGRAM_SUBJECT = /^[1-9][0-9]{0,19}$/;

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function safeEqual(left, right) {
  const a = Buffer.from(left ?? '', 'utf8');
  const b = Buffer.from(right ?? '', 'utf8');
  return a.length === b.length && timingSafeEqual(a, b);
}

async function parseBody(request, maximumBytes = 8192) {
  const type = request.headers.get('content-type') ?? '';
  if (!type.toLowerCase().startsWith('application/json')) {
    throw new ApiError(400, 'invalid_content_type', 'Content-Type must be application/json.');
  }
  const raw = await request.text();
  if (Buffer.byteLength(raw, 'utf8') > maximumBytes) throw new ApiError(413, 'request_too_large', 'Request body is too large.');
  try { return requireObject(JSON.parse(raw), 'body'); } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(400, 'invalid_json', 'Request body is not valid JSON.');
  }
}

function bearer(request) {
  const value = request.headers.get('authorization') ?? '';
  return value.startsWith('Bearer ') && !value.includes(',') ? value.slice(7) : '';
}

function approvalId(value) {
  const parsed = requireString(value, 'approval_id', { min: 43, max: 43 });
  if (!/^[A-Za-z0-9_-]{43}$/.test(parsed)) throw new ApiError(400, 'invalid_approval_id', 'Approval ID is invalid.');
  return parsed;
}

export function createBotApprovalApplication({ baseApplication, runtime, environment, clock = () => new Date() }) {
  if (typeof baseApplication !== 'function') throw new TypeError('baseApplication is required.');
  if (typeof runtime?.auth?.authenticate !== 'function' || typeof runtime?.authSession?.linkTelegram !== 'function') {
    throw new TypeError('Bot approval requires production auth and auth-session adapters.');
  }
  if (typeof runtime.repository?.database !== 'function') throw new TypeError('Bot approval requires the PostgreSQL repository.');
  const botUsername = environment.GANJ_BOT_USERNAME;
  const sharedSecret = environment.GANJ_BOT_APPROVAL_SECRET;
  if (!BOT_USERNAME.test(botUsername ?? '')) throw new Error('GANJ_BOT_USERNAME is required for Telegram bot approval.');
  if (typeof sharedSecret !== 'string' || Buffer.byteLength(sharedSecret, 'utf8') < 32) {
    throw new Error('GANJ_BOT_APPROVAL_SECRET must contain at least 32 bytes.');
  }
  const database = runtime.repository.database();

  async function start(request, requestId) {
    const principal = await runtime.auth.authenticate(request);
    const token = randomBytes(32).toString('base64url');
    const expiresAt = new Date(clock().getTime() + APPROVAL_TTL_MS);
    await database.query(
      `INSERT INTO control_telegram_bot_approvals
       (approval_digest, user_id, device_id, status, expires_at, created_at, updated_at)
       VALUES ($1,$2,$3,'pending',$4,$5,$5)`,
      [sha256(token), principal.userId, principal.deviceId, expiresAt, clock()],
    );
    return { status: 201, body: success({
      approval_id: token,
      telegram_url: `https://t.me/${botUsername}?start=ganjapp_${token}`,
      expires_at: expiresAt.toISOString(),
      poll_after_ms: 1500,
    }, requestId, clock) };
  }

  async function approve(request, requestId) {
    if (!safeEqual(bearer(request), sharedSecret)) throw new ApiError(401, 'unauthorized', 'Bot approval credential is invalid.');
    const body = await parseBody(request);
    rejectUnknown(body, ['approval_id', 'telegram_subject', 'username', 'display_name']);
    const id = approvalId(body.approval_id);
    const telegramSubject = requireString(body.telegram_subject, 'telegram_subject', { min: 1, max: 20 });
    if (!TELEGRAM_SUBJECT.test(telegramSubject)) throw new ApiError(400, 'invalid_telegram_subject', 'Telegram subject is invalid.');
    const username = body.username == null ? null : requireString(body.username, 'username', { min: 1, max: 64 });
    const displayName = body.display_name == null ? null : requireString(body.display_name, 'display_name', { min: 1, max: 160 });
    const result = await database.query(
      `UPDATE control_telegram_bot_approvals
          SET status='approved', telegram_subject=$2, telegram_username=$3,
              telegram_display_name=$4, approved_at=$5, updated_at=$5
        WHERE approval_digest=$1 AND status='pending' AND expires_at>$5
        RETURNING approval_digest`,
      [sha256(id), telegramSubject, username, displayName, clock()],
    );
    if (result.rowCount !== 1) throw new ApiError(409, 'approval_unavailable', 'Approval is expired, consumed, or no longer pending.');
    return { status: 200, body: success({ approved: true }, requestId, clock) };
  }

  async function complete(request, requestId) {
    const principal = await runtime.auth.authenticate(request);
    const body = await parseBody(request);
    rejectUnknown(body, ['approval_id']);
    const id = approvalId(body.approval_id);
    const now = clock();
    const claimed = await database.query(
      `UPDATE control_telegram_bot_approvals
          SET status='consuming', updated_at=$4
        WHERE approval_digest=$1 AND user_id=$2 AND device_id=$3
          AND status='approved' AND expires_at>$4
        RETURNING telegram_subject, telegram_username, telegram_display_name`,
      [sha256(id), principal.userId, principal.deviceId, now],
    );
    if (claimed.rowCount !== 1) {
      const state = await database.query(
        `SELECT status, expires_at FROM control_telegram_bot_approvals
          WHERE approval_digest=$1 AND user_id=$2 AND device_id=$3`,
        [sha256(id), principal.userId, principal.deviceId],
      );
      if (state.rowCount !== 1) throw new ApiError(404, 'approval_not_found', 'Approval was not found for this device.');
      const row = state.rows[0];
      if (new Date(row.expires_at).getTime() <= now.getTime()) throw new ApiError(410, 'approval_expired', 'Approval expired.');
      if (row.status === 'pending') return { status: 202, body: success({ status: 'pending' }, requestId, clock) };
      if (row.status === 'consumed') throw new ApiError(409, 'approval_consumed', 'Approval was already consumed.');
      throw new ApiError(409, 'approval_unavailable', 'Approval cannot be completed.');
    }
    const identity = claimed.rows[0];
    try {
      const session = await runtime.authSession.linkTelegram({
        telegramSubject: identity.telegram_subject,
        username: identity.telegram_username,
        displayName: identity.telegram_display_name,
        deviceId: principal.deviceId,
      });
      await database.query(
        `UPDATE control_telegram_bot_approvals SET status='consumed', consumed_at=$2, updated_at=$2
          WHERE approval_digest=$1 AND status='consuming'`,
        [sha256(id), clock()],
      );
      return { status: 200, body: success({ status: 'linked', session }, requestId, clock) };
    } catch (error) {
      await database.query(
        `UPDATE control_telegram_bot_approvals SET status='approved', updated_at=$2
          WHERE approval_digest=$1 AND status='consuming'`,
        [sha256(id), clock()],
      );
      throw error;
    }
  }

  return async function botApprovalApplication(request) {
    const requestId = /^[0-9a-f-]{36}$/i.test(request.headers.get('x-request-id') ?? '')
      ? request.headers.get('x-request-id') : randomUUID();
    try {
      const pathname = new URL(request.url).pathname;
      if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/start') return await start(request, requestId);
      if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/complete') return await complete(request, requestId);
      if (request.method === 'POST' && pathname === '/v1/internal/telegram/bot/approve') return await approve(request, requestId);
      return baseApplication(request);
    } catch (error) {
      const result = failure(error, requestId, clock);
      return { status: result.status, body: result.body };
    }
  };
}
