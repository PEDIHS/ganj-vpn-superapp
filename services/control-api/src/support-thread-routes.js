import { createHash } from 'node:crypto';
import { ApiError, rejectUnknown, requireString, requireUuid, success } from './errors.js';

const SECRET_LIKE = /(?:vless|vmess|trojan|ss|wireguard):\/\/|-----BEGIN [A-Z ]*PRIVATE KEY-----|\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/i;
const STATUSES = new Set(['open', 'waiting_user', 'waiting_support', 'resolved', 'closed']);
const SENDER_ROLES = new Set(['user', 'support', 'system']);

function databaseFor(repository) {
  if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function') {
    throw new ApiError(503, 'support_unavailable', 'Support is unavailable.', { retryable: true });
  }
  return repository.database();
}

function safeBody(value) {
  const body = requireString(value, 'body', { min: 1, max: 8000 }).trim();
  if (SECRET_LIKE.test(body)) {
    throw new ApiError(400, 'sensitive_content_rejected', 'Support message appears to contain private connection material.');
  }
  return body;
}

function mapTicket(row) {
  if (!STATUSES.has(row.status)) throw new ApiError(500, 'support_ticket_invalid', 'Ticket status is invalid.');
  return {
    id: row.id,
    public_code: row.public_code,
    category: row.category,
    priority: row.priority,
    subject: row.subject,
    status: row.status,
    created_at: row.created_at.toISOString(),
    updated_at: row.updated_at.toISOString(),
  };
}

function mapMessage(row) {
  if (!SENDER_ROLES.has(row.sender_role)) throw new ApiError(500, 'support_message_invalid', 'Message sender role is invalid.');
  return {
    id: row.id,
    sender_role: row.sender_role,
    body: row.body,
    created_at: row.created_at.toISOString(),
  };
}

async function ownedTicket(database, userId, ticketId, { lock = false } = {}) {
  const result = await database.query(
    `SELECT id, public_code, category, priority, subject, status, created_at, updated_at
       FROM control_support_tickets
      WHERE user_id = $1 AND id = $2${lock ? ' FOR UPDATE' : ''}`,
    [userId, ticketId],
  );
  if (result.rowCount !== 1) throw new ApiError(404, 'support_ticket_not_found', 'Support ticket was not found.');
  return result.rows[0];
}

export function createSupportThreadRouter({ auth, repository, parseBody, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');
  if (typeof parseBody !== 'function') throw new Error('parseBody is required.');

  return async function route({ request, url, requestId }) {
    const detailMatch = url.pathname.match(/^\/v1\/support\/tickets\/([0-9a-f-]{36})$/i);
    const messageMatch = url.pathname.match(/^\/v1\/support\/tickets\/([0-9a-f-]{36})\/messages$/i);
    const reopenMatch = url.pathname.match(/^\/v1\/support\/tickets\/([0-9a-f-]{36})\/reopen$/i);
    if (!detailMatch && !messageMatch && !reopenMatch) return null;

    const principal = await auth.authenticate(request);
    requireUuid(principal.userId, 'authenticated user id');
    const database = databaseFor(repository);
    const ticketId = requireUuid((detailMatch ?? messageMatch ?? reopenMatch)[1], 'ticket_id');

    if (request.method === 'GET' && detailMatch) {
      const ticket = await ownedTicket(database, principal.userId, ticketId);
      const messages = await database.query(
        `SELECT id, sender_role, body, created_at
           FROM control_support_messages
          WHERE user_id = $1 AND ticket_id = $2
          ORDER BY created_at, id
          LIMIT 500`,
        [principal.userId, ticketId],
      );
      return {
        status: 200,
        body: success({ ...mapTicket(ticket), messages: messages.rows.map(mapMessage) }, requestId, clock),
      };
    }

    if (request.method === 'GET' && messageMatch) {
      await ownedTicket(database, principal.userId, ticketId);
      const messages = await database.query(
        `SELECT id, sender_role, body, created_at
           FROM control_support_messages
          WHERE user_id = $1 AND ticket_id = $2
          ORDER BY created_at, id
          LIMIT 500`,
        [principal.userId, ticketId],
      );
      return { status: 200, body: success(messages.rows.map(mapMessage), requestId, clock) };
    }

    if (request.method === 'POST' && messageMatch) {
      const input = await parseBody(request);
      rejectUnknown(input, ['client_message_id', 'body']);
      const messageId = requireUuid(input.client_message_id, 'client_message_id');
      const body = safeBody(input.body);
      const bodyDigest = createHash('sha256').update(body).digest('hex');
      const result = await repository.transaction(async () => {
        const tx = databaseFor(repository);
        const ticket = await ownedTicket(tx, principal.userId, ticketId, { lock: true });
        if (ticket.status === 'closed') {
          throw new ApiError(409, 'support_ticket_closed', 'Closed ticket must be reopened before replying.');
        }
        const inserted = await tx.query(
          `INSERT INTO control_support_messages (id, ticket_id, user_id, sender_role, body)
           VALUES ($1, $2, $3, 'user', $4)
           ON CONFLICT (id) DO NOTHING
           RETURNING id, sender_role, body, created_at`,
          [messageId, ticketId, principal.userId, body],
        );
        let message;
        if (inserted.rowCount === 1) {
          message = inserted.rows[0];
        } else {
          const replay = await tx.query(
            `SELECT id, sender_role, body, created_at
               FROM control_support_messages
              WHERE id = $1 AND ticket_id = $2 AND user_id = $3`,
            [messageId, ticketId, principal.userId],
          );
          if (replay.rowCount !== 1 || createHash('sha256').update(replay.rows[0].body).digest('hex') !== bodyDigest) {
            throw new ApiError(409, 'idempotency_conflict', 'client_message_id was already used for different content.');
          }
          message = replay.rows[0];
        }
        await tx.query(
          `UPDATE control_support_tickets
              SET status = 'waiting_support', updated_at = now()
            WHERE user_id = $1 AND id = $2`,
          [principal.userId, ticketId],
        );
        return { message, replay: inserted.rowCount === 0 };
      });
      return {
        status: result.replay ? 200 : 201,
        body: success(mapMessage(result.message), requestId, clock),
      };
    }

    if (request.method === 'POST' && reopenMatch) {
      const ticket = await repository.transaction(async () => {
        const tx = databaseFor(repository);
        const current = await ownedTicket(tx, principal.userId, ticketId, { lock: true });
        if (current.status === 'resolved' || current.status === 'closed') {
          const updated = await tx.query(
            `UPDATE control_support_tickets
                SET status = 'waiting_support', updated_at = now()
              WHERE user_id = $1 AND id = $2
              RETURNING id, public_code, category, priority, subject, status, created_at, updated_at`,
            [principal.userId, ticketId],
          );
          return updated.rows[0];
        }
        return current;
      });
      return { status: 200, body: success(mapTicket(ticket), requestId, clock) };
    }

    return null;
  };
}
