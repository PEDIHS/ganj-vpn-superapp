import { createHash, randomUUID } from 'node:crypto';
import { ApiError, rejectUnknown, requireObject, requireString, requireUuid, success } from './errors.js';

const ADMIN_SCOPE = 'admin:control-plane';
const TICKET_STATUSES = new Set(['open', 'waiting_user', 'waiting_support', 'resolved', 'closed']);
const MUTABLE_STATUSES = new Set(['waiting_user', 'waiting_support', 'resolved', 'closed']);
const SECRET_LIKE = /(?:vless|vmess|trojan|ss|wireguard):\/\/|-----BEGIN [A-Z ]*PRIVATE KEY-----|\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/i;

function requireAdmin(principal) {
  if (!Array.isArray(principal?.scopes) || !principal.scopes.includes(ADMIN_SCOPE)) {
    throw new ApiError(403, 'admin_scope_required', 'Control-plane administrator scope is required.');
  }
}

function databaseFor(repository) {
  if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function') {
    throw new ApiError(503, 'support_unavailable', 'Support is unavailable.', { retryable: true });
  }
  return repository.database();
}

function safeBody(value) {
  const body = requireString(value, 'body', { min: 1, max: 8000 }).trim();
  if (!body) throw new ApiError(400, 'invalid_request', 'Support message body cannot be blank.');
  if (SECRET_LIKE.test(body)) {
    throw new ApiError(400, 'sensitive_content_rejected', 'Support message appears to contain private connection material.');
  }
  return body;
}

function presentTicket(row) {
  if (!TICKET_STATUSES.has(row.status)) throw new ApiError(500, 'support_ticket_invalid', 'Ticket status is invalid.');
  return {
    id: row.id,
    user_id: row.user_id,
    public_code: row.public_code,
    category: row.category,
    priority: row.priority,
    subject: row.subject,
    status: row.status,
    created_at: row.created_at.toISOString(),
    updated_at: row.updated_at.toISOString(),
  };
}

function presentMessage(row) {
  if (!['user', 'support', 'system'].includes(row.sender_role)) {
    throw new ApiError(500, 'support_message_invalid', 'Message sender role is invalid.');
  }
  return {
    id: row.id,
    sender_role: row.sender_role,
    body: row.body,
    created_at: row.created_at.toISOString(),
  };
}

async function audit(repository, principal, requestId, action, ticketId, before, after) {
  if (typeof repository.appendAdminAudit !== 'function') return;
  const digest = (value) => value == null ? null : createHash('sha256').update(JSON.stringify(value)).digest('hex');
  await repository.appendAdminAudit({
    id: randomUUID(),
    actorSubject: principal.subject ?? `user:${principal.userId}`,
    action,
    resourceType: 'support_ticket',
    resourceId: ticketId,
    reason: 'admin_support_operation',
    requestId,
    beforeDigest: digest(before),
    afterDigest: digest(after),
    outcome: 'success',
    createdAt: new Date().toISOString(),
  });
}

async function findTicket(database, ticketId, { lock = false } = {}) {
  const result = await database.query(
    `SELECT id, user_id, public_code, category, priority, subject, status, created_at, updated_at
       FROM control_support_tickets
      WHERE id = $1${lock ? ' FOR UPDATE' : ''}`,
    [ticketId],
  );
  if (result.rowCount !== 1) throw new ApiError(404, 'support_ticket_not_found', 'Support ticket was not found.');
  return result.rows[0];
}

async function createReplyNotification(database, ticket) {
  await database.query(
    `INSERT INTO control_notifications (id, user_id, kind, title, body, action)
     SELECT $1, $2, 'support_reply', $3, $4, $5::jsonb
       FROM control_notification_preferences
      WHERE user_id = $2 AND support_reply = true`,
    [
      randomUUID(),
      ticket.user_id,
      'پاسخ جدید پشتیبانی',
      `برای تیکت ${ticket.public_code} یک پاسخ جدید ثبت شد.`,
      JSON.stringify({ type: 'open_support', id: ticket.id }),
    ],
  );
}

export function createAdminSupportRouter({ repository, clock = () => new Date(), parseBody }) {
  if (typeof parseBody !== 'function') throw new Error('Admin support parseBody boundary is required.');

  return async function route({ request, url, pathname, principal, requestId }) {
    if (!pathname.startsWith('/v1/admin/support/')) return null;
    requireAdmin(principal);
    const database = databaseFor(repository);

    if (request.method === 'GET' && pathname === '/v1/admin/support/tickets') {
      const status = url.searchParams.get('status');
      if (status !== null && !TICKET_STATUSES.has(status)) {
        throw new ApiError(400, 'invalid_support_status', 'Support status filter is invalid.');
      }
      const params = status === null ? [] : [status];
      const where = status === null ? '' : 'WHERE status = $1';
      const result = await database.query(
        `SELECT id, user_id, public_code, category, priority, subject, status, created_at, updated_at
           FROM control_support_tickets
          ${where}
          ORDER BY updated_at DESC, id DESC
          LIMIT 200`,
        params,
      );
      return { status: 200, body: success(result.rows.map(presentTicket), requestId, clock) };
    }

    const detailMatch = pathname.match(/^\/v1\/admin\/support\/tickets\/([0-9a-f-]{36})$/i);
    const messageMatch = pathname.match(/^\/v1\/admin\/support\/tickets\/([0-9a-f-]{36})\/messages$/i);
    if (!detailMatch && !messageMatch) return null;
    const ticketId = requireUuid((detailMatch ?? messageMatch)[1], 'ticket_id');

    if (request.method === 'GET' && detailMatch) {
      const ticket = await findTicket(database, ticketId);
      const messages = await database.query(
        `SELECT id, sender_role, body, created_at
           FROM control_support_messages
          WHERE ticket_id = $1
          ORDER BY created_at, id
          LIMIT 500`,
        [ticketId],
      );
      return {
        status: 200,
        body: success({ ...presentTicket(ticket), messages: messages.rows.map(presentMessage) }, requestId, clock),
      };
    }

    if (request.method === 'POST' && messageMatch) {
      const input = requireObject(await parseBody(request), 'body');
      rejectUnknown(input, ['client_message_id', 'body']);
      const messageId = requireUuid(input.client_message_id, 'client_message_id');
      const body = safeBody(input.body);
      const bodyDigest = createHash('sha256').update(body).digest('hex');
      const outcome = await repository.transaction(async () => {
        const tx = databaseFor(repository);
        const ticket = await findTicket(tx, ticketId, { lock: true });
        if (ticket.status === 'closed') {
          throw new ApiError(409, 'support_ticket_closed', 'Closed ticket must be reopened before replying.');
        }
        const inserted = await tx.query(
          `INSERT INTO control_support_messages (id, ticket_id, user_id, sender_role, body)
           VALUES ($1, $2, $3, 'support', $4)
           ON CONFLICT (id) DO NOTHING
           RETURNING id, sender_role, body, created_at`,
          [messageId, ticketId, ticket.user_id, body],
        );
        let message;
        const replayed = inserted.rowCount === 0;
        if (!replayed) {
          message = inserted.rows[0];
        } else {
          const replay = await tx.query(
            `SELECT id, sender_role, body, created_at
               FROM control_support_messages
              WHERE id = $1 AND ticket_id = $2 AND sender_role = 'support'`,
            [messageId, ticketId],
          );
          if (replay.rowCount !== 1 || createHash('sha256').update(replay.rows[0].body).digest('hex') !== bodyDigest) {
            throw new ApiError(409, 'idempotency_conflict', 'client_message_id was already used for different content.');
          }
          message = replay.rows[0];
        }
        const updated = await tx.query(
          `UPDATE control_support_tickets
              SET status = 'waiting_user', updated_at = now()
            WHERE id = $1
            RETURNING id, user_id, public_code, category, priority, subject, status, created_at, updated_at`,
          [ticketId],
        );
        const updatedTicket = updated.rows[0];
        if (!replayed) await createReplyNotification(tx, updatedTicket);
        await audit(repository, principal, requestId, 'support.reply', ticketId, presentTicket(ticket), presentTicket(updatedTicket));
        return { message, replay: replayed };
      });
      return {
        status: outcome.replay ? 200 : 201,
        body: success(presentMessage(outcome.message), requestId, clock),
      };
    }

    if (request.method === 'PATCH' && detailMatch) {
      const input = requireObject(await parseBody(request), 'body');
      rejectUnknown(input, ['status']);
      const status = requireString(input.status, 'status', { min: 4, max: 32 });
      if (!MUTABLE_STATUSES.has(status)) {
        throw new ApiError(400, 'invalid_support_status', 'Requested support status is invalid.');
      }
      const updated = await repository.transaction(async () => {
        const tx = databaseFor(repository);
        const before = await findTicket(tx, ticketId, { lock: true });
        const result = await tx.query(
          `UPDATE control_support_tickets
              SET status = $2, updated_at = now()
            WHERE id = $1
            RETURNING id, user_id, public_code, category, priority, subject, status, created_at, updated_at`,
          [ticketId, status],
        );
        const after = result.rows[0];
        await audit(repository, principal, requestId, 'support.status', ticketId, presentTicket(before), presentTicket(after));
        return after;
      });
      return { status: 200, body: success(presentTicket(updated), requestId, clock) };
    }

    return null;
  };
}
