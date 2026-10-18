import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { ApiError, success } from './errors.js';
import { subscriptionNotices, expiryNotices, paymentNotice } from './ganj-bot-notice-policy.js';
import { sendCustomerPush } from './firebase-push-sender.js';

const PATH = '/v1/internal/ganj-bot/customer-event';
const ALLOWED = new Set(['event_id', 'telegram_subject', 'type', 'data']);
const EVENT_TYPES = new Set(['subscription_usage', 'subscription_expiry', 'payment_transition']);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function verify(request, raw, secret, clock) {
  if (typeof secret !== 'string' || Buffer.byteLength(secret, 'utf8') < 32)
    throw new ApiError(503, 'bot_events_unavailable', 'Event delivery not configured.');
  const stamp = request.headers.get('x-ganj-event-time');
  const sig = request.headers.get('x-ganj-event-signature');
  if (!/^\d{10}$/.test(stamp ?? '') || !/^[0-9a-f]{64}$/.test(sig ?? ''))
    throw new ApiError(401, 'invalid_event_signature', 'Event signature is invalid.');
  if (Math.abs(clock().getTime() / 1000 - Number(stamp)) > 90)
    throw new ApiError(401, 'stale_event_signature', 'Event signature has expired.');
  const expected = createHmac('sha256', secret).update(stamp + '.' + raw).digest('hex');
  if (!timingSafeEqual(Buffer.from(expected, 'hex'), Buffer.from(sig, 'hex')))
    throw new ApiError(401, 'invalid_event_signature', 'Event signature is invalid.');
}

function decode(raw) {
  let body;
  try { body = JSON.parse(raw); } catch { throw new ApiError(400, 'invalid_json', 'Invalid JSON.'); }
  if (!body || typeof body !== 'object' || Array.isArray(body) ||
      Object.keys(body).some(key => !ALLOWED.has(key)) ||
      !UUID.test(body.event_id ?? '') ||
      !/^\d{4,20}$/.test(body.telegram_subject ?? '') ||
      !EVENT_TYPES.has(body.type) ||
      !body.data || typeof body.data !== 'object' || Array.isArray(body.data))
    throw new ApiError(400, 'invalid_event', 'Invalid bot event.');
  return body;
}

function classify(event) {
  if (event.type === 'payment_transition') return paymentNotice(event.data);
  if (event.type === 'subscription_usage') return subscriptionNotices(event.data);
  return expiryNotices(event.data);
}

function preference(kind) {
  return kind === 'subscription_usage' || kind === 'subscription_expiry' ? 'subscription_expiry'
    : kind === 'purchase_success' ? 'purchase_success'
    : kind === 'payment_failure' ? 'payment_failure' : null;
}

/** Authenticated Bot-side post-commit delivery. No public user-driven alerts. */
export function createBotCustomerEventRouter({
  repository, secret, clock = () => new Date(),
  environment = process.env, push = sendCustomerPush,
}) {
  return async function route({ request, url, requestId }) {
    if (url.pathname !== PATH) return null;
    if (request.method !== 'POST') throw new ApiError(405, 'method_not_allowed', 'POST required.');
    if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function')
      throw new ApiError(503, 'bot_events_unavailable', 'Event store unavailable.');
    const raw = await request.text();
    if (Buffer.byteLength(raw, 'utf8') > 8_192)
      throw new ApiError(413, 'event_too_large', 'Event is too large.');
    verify(request, raw, secret, clock);
    const event = decode(raw);
    const notices = classify(event);
    if (notices.length > 2) throw new ApiError(400, 'invalid_event', 'Too many notifications.');
    const db = repository.database();
    const client = await db.connect();
    let saved = 0;
    let receivingUser = null;
    const pendingPush = [];
    try {
      await client.query('BEGIN');
      const owners = await client.query(
        "SELECT id FROM control_users WHERE telegram_subject = $1 AND status = 'active'",
        [event.telegram_subject],
      );
      if (owners.rowCount !== 1) {
        // Avoid disclosing whether a Telegram identity has signed up.
        await client.query('COMMIT');
        return { status: 202, body: success({ accepted: true }, requestId, clock) };
      }
      const userId = owners.rows[0].id;
      receivingUser = userId;
      for (const notice of notices) {
        const pref = preference(notice.kind);
        if (!pref) throw new ApiError(400, 'invalid_event', 'Unexpected notification category.');
        const id = randomUUID();
        const inserted = await client.query(
          'INSERT INTO control_notifications (id,user_id,kind,title,body,action,event_key) ' +
          'SELECT $1,$2,$3,$4,$5,$6::jsonb,$7 FROM control_notification_preferences ' +
          'WHERE user_id = $2 AND ' + pref + ' = true ' +
          'ON CONFLICT (user_id,event_key) WHERE event_key IS NOT NULL DO NOTHING RETURNING id',
          [id, userId, notice.kind, notice.title, notice.body, JSON.stringify(notice.action), notice.eventKey],
        );
        if (inserted.rowCount === 1) {
          const journal = await client.query(
            'INSERT INTO control_customer_event_journal (user_id,notification_id,event_key) ' +
            'VALUES ($1,$2,$3) RETURNING event_seq',
            [userId, id, notice.eventKey],
          );
          if (journal.rowCount !== 1) throw new Error('event_journal_unavailable');
          // PostgreSQL NOTIFY is delivered only after COMMIT; journal persists
          // if every listening app disconnects, so reconnect can replay.
          await client.query("SELECT pg_notify('ganj_customer_events', $1)",
            [JSON.stringify({ user_id: userId, seq: String(journal.rows[0].event_seq) })]);
          saved++;
          pendingPush.push({ id, kind: notice.kind, title: notice.title,
            body: notice.body, createdAt: clock().toISOString() });
        }
      }
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally { client.release(); }
    if (pendingPush.length && receivingUser) {
      // A notification is already durably recorded. FCM failure does NOT
      // invalidate, retry or misreport a payment/receipt operation.
      try {
        await push({ database: db, userId: receivingUser, notifications: pendingPush, environment });
      } catch { /* durable event is replayable; no sensitive details in logs */ }
    }
    return { status: 202, body: success({ accepted: true, events_created: saved }, requestId, clock) };
  };
}
