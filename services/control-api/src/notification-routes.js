import { ApiError, requireUuid, success } from './errors.js';

const DEFAULT_LIMIT = 30;
const MAX_LIMIT = 50;
const KINDS = new Set([
  'subscription_expiry',
  'purchase_success',
  'payment_failure',
  'maintenance',
  'security_update',
  'support_reply',
  'marketing',
]);
const ACTION_TYPES = new Set(['open_store', 'open_subscription', 'open_support', 'open_wallet', 'open_settings']);
const PREFERENCE_KEYS = [
  'subscription_expiry',
  'purchase_success',
  'payment_failure',
  'maintenance',
  'security_update',
  'support_reply',
  'marketing',
];

function databaseFor(repository) {
  if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function') {
    throw new ApiError(503, 'notifications_unavailable', 'Notifications are unavailable.', { retryable: true });
  }
  return repository.database();
}

function parseLimit(url) {
  const raw = url.searchParams.get('limit');
  if (raw === null) return DEFAULT_LIMIT;
  if (!/^[0-9]{1,3}$/.test(raw)) throw new ApiError(400, 'invalid_request', 'limit is invalid.');
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1 || value > MAX_LIMIT) {
    throw new ApiError(400, 'invalid_request', `limit must be between 1 and ${MAX_LIMIT}.`);
  }
  return value;
}

function encodeCursor(row) {
  return Buffer.from(`${row.created_at.toISOString()}|${row.id}`, 'utf8').toString('base64url');
}

function decodeCursor(value) {
  if (!value) return null;
  if (!/^[A-Za-z0-9_-]{8,512}$/.test(value)) throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  const decoded = Buffer.from(value, 'base64url').toString('utf8');
  const separator = decoded.indexOf('|');
  if (separator <= 0) throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  const createdAt = decoded.slice(0, separator);
  const id = decoded.slice(separator + 1);
  if (!/^\d{4}-\d{2}-\d{2}T/.test(createdAt) || !/^[0-9a-f-]{36}$/i.test(id)) {
    throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  }
  const parsed = new Date(createdAt);
  if (Number.isNaN(parsed.getTime())) throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  return { createdAt: parsed.toISOString(), id };
}

function safeText(value, name, max) {
  if (typeof value !== 'string' || value.length < 1 || value.length > max || /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(value)) {
    throw new ApiError(500, 'notification_payload_invalid', `${name} is invalid.`);
  }
  return value;
}

function safeAction(value) {
  if (value === null || value === undefined) return null;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(500, 'notification_action_invalid', 'Notification action is invalid.');
  }
  const keys = Object.keys(value);
  if (keys.some((key) => !['type', 'id'].includes(key))) {
    throw new ApiError(500, 'notification_action_invalid', 'Notification action is invalid.');
  }
  if (!ACTION_TYPES.has(value.type)) {
    throw new ApiError(500, 'notification_action_invalid', 'Notification action type is invalid.');
  }
  const result = { type: value.type };
  if (value.id !== undefined && value.id !== null) result.id = requireUuid(value.id, 'notification action id');
  return result;
}

function mapNotification(row) {
  if (!KINDS.has(row.kind)) throw new ApiError(500, 'notification_kind_invalid', 'Notification kind is invalid.');
  return {
    id: row.id,
    kind: row.kind,
    title: safeText(row.title, 'Notification title', 160),
    body: safeText(row.body, 'Notification body', 2000),
    read: row.read_at !== null,
    action: safeAction(row.action),
    created_at: row.created_at.toISOString(),
  };
}

function mapPreferences(row) {
  if (!row) throw new ApiError(503, 'notification_preferences_unavailable', 'Notification preferences are unavailable.', { retryable: true });
  return Object.fromEntries(PREFERENCE_KEYS.map((key) => [key, row[key] === true]));
}

function validatePreferences(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ApiError(400, 'invalid_request', 'Body must be an object.');
  const keys = Object.keys(body);
  if (keys.length !== PREFERENCE_KEYS.length || keys.some((key) => !PREFERENCE_KEYS.includes(key))) {
    throw new ApiError(400, 'invalid_request', 'All notification preference fields are required.');
  }
  for (const key of PREFERENCE_KEYS) {
    if (typeof body[key] !== 'boolean') throw new ApiError(400, 'invalid_request', `${key} must be boolean.`);
  }
  return Object.fromEntries(PREFERENCE_KEYS.map((key) => [key, body[key]]));
}

export function createNotificationRouter({ auth, repository, parseBody, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');
  if (typeof parseBody !== 'function') throw new Error('parseBody is required.');

  return async function route({ request, url, requestId }) {
    const listPath = url.pathname === '/v1/notifications';
    const readAllPath = url.pathname === '/v1/notifications/read-all';
    const preferencesPath = url.pathname === '/v1/notifications/preferences';
    const readMatch = url.pathname.match(/^\/v1\/notifications\/([0-9a-f-]{36})\/read$/i);
    if (!listPath && !readAllPath && !preferencesPath && !readMatch) return null;

    const principal = await auth.authenticate(request);
    requireUuid(principal.userId, 'authenticated user id');
    const database = databaseFor(repository);

    if (request.method === 'GET' && listPath) {
      const limit = parseLimit(url);
      const cursor = decodeCursor(url.searchParams.get('cursor'));
      const params = [principal.userId, limit + 1];
      let cursorClause = '';
      if (cursor) {
        params.push(cursor.createdAt, cursor.id);
        cursorClause = 'AND (created_at, id) < ($3::timestamptz, $4::uuid)';
      }
      const result = await database.query(
        `SELECT id, kind, title, body, action, read_at, created_at
           FROM control_notifications
          WHERE user_id = $1
          ${cursorClause}
          ORDER BY created_at DESC, id DESC
          LIMIT $2`,
        params,
      );
      const hasMore = result.rows.length > limit;
      const visible = result.rows.slice(0, limit);
      const body = success(visible.map(mapNotification), requestId, clock);
      body.meta.has_more = hasMore;
      body.meta.next_cursor = hasMore && visible.length > 0 ? encodeCursor(visible[visible.length - 1]) : null;
      body.meta.unread_count = Number((await database.query(
        'SELECT count(*)::int AS count FROM control_notifications WHERE user_id = $1 AND read_at IS NULL',
        [principal.userId],
      )).rows[0]?.count ?? 0);
      return { status: 200, body };
    }

    if (request.method === 'POST' && readMatch) {
      const notificationId = requireUuid(readMatch[1], 'notification_id');
      const result = await database.query(
        `UPDATE control_notifications
            SET read_at = COALESCE(read_at, now())
          WHERE user_id = $1 AND id = $2
          RETURNING id`,
        [principal.userId, notificationId],
      );
      if (result.rowCount !== 1) throw new ApiError(404, 'notification_not_found', 'Notification was not found.');
      return { status: 204, body: null };
    }

    if (request.method === 'POST' && readAllPath) {
      await database.query(
        'UPDATE control_notifications SET read_at = COALESCE(read_at, now()) WHERE user_id = $1 AND read_at IS NULL',
        [principal.userId],
      );
      return { status: 204, body: null };
    }

    if (request.method === 'GET' && preferencesPath) {
      const result = await database.query(
        `SELECT ${PREFERENCE_KEYS.join(', ')}
           FROM control_notification_preferences
          WHERE user_id = $1`,
        [principal.userId],
      );
      return { status: 200, body: success(mapPreferences(result.rows[0]), requestId, clock) };
    }

    if (request.method === 'PUT' && preferencesPath) {
      const preferences = validatePreferences(await parseBody(request));
      const values = PREFERENCE_KEYS.map((key) => preferences[key]);
      const result = await database.query(
        `UPDATE control_notification_preferences SET
           subscription_expiry = $2,
           purchase_success = $3,
           payment_failure = $4,
           maintenance = $5,
           security_update = $6,
           support_reply = $7,
           marketing = $8,
           updated_at = now()
         WHERE user_id = $1
         RETURNING ${PREFERENCE_KEYS.join(', ')}`,
        [principal.userId, ...values],
      );
      return { status: 200, body: success(mapPreferences(result.rows[0]), requestId, clock) };
    }

    return null;
  };
}
