import { ApiError, meta, success } from './errors.js';

const DEFAULT_LIMIT = 20;
const MAX_LIMIT = 50;

function databaseFor(repository) {
  if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function') {
    throw new ApiError(503, 'wallet_unavailable', 'Wallet data is unavailable.', { retryable: true });
  }
  return repository.database();
}

function parseLimit(url) {
  const raw = url.searchParams.get('limit');
  if (raw === null) return DEFAULT_LIMIT;
  if (!/^[0-9]{1,3}$/.test(raw)) throw new ApiError(400, 'invalid_request', 'limit is invalid.');
  const limit = Number(raw);
  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_LIMIT) {
    throw new ApiError(400, 'invalid_request', `limit must be between 1 and ${MAX_LIMIT}.`);
  }
  return limit;
}

function encodeCursor(row) {
  return Buffer.from(`${row.created_at.toISOString()}|${row.id}`, 'utf8').toString('base64url');
}

function decodeCursor(value) {
  if (!value) return null;
  if (!/^[A-Za-z0-9_-]{8,512}$/.test(value)) throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  let decoded;
  try {
    decoded = Buffer.from(value, 'base64url').toString('utf8');
  } catch {
    throw new ApiError(400, 'invalid_request', 'cursor is invalid.');
  }
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

function money(amountMinor, currency) {
  return { amount_minor: Number(amountMinor), currency };
}

function mapEntry(row) {
  return {
    id: row.id,
    type: row.entry_type,
    direction: row.direction,
    amount: money(row.amount_minor, row.currency),
    balance_after: money(row.balance_after_minor, row.currency),
    reference_type: row.reference_type ?? null,
    reference_id: row.reference_id ?? null,
    description: row.description ?? null,
    created_at: row.created_at.toISOString(),
  };
}

export function createWalletRouter({ auth, repository, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');

  return async function route({ request, url, requestId }) {
    if (request.method !== 'GET') return null;
    if (url.pathname !== '/v1/wallet' && url.pathname !== '/v1/wallet/transactions') return null;

    const principal = await auth.authenticate(request);
    const database = databaseFor(repository);

    if (url.pathname === '/v1/wallet') {
      const result = await database.query(
        `SELECT balance_amount_minor, currency
           FROM control_wallets
          WHERE user_id = $1`,
        [principal.userId],
      );
      if (result.rowCount !== 1) {
        throw new ApiError(503, 'wallet_not_initialized', 'Wallet is not initialized.', { retryable: true });
      }
      const row = result.rows[0];
      return {
        status: 200,
        body: success({ balance: money(row.balance_amount_minor, row.currency) }, requestId, clock),
      };
    }

    const limit = parseLimit(url);
    const cursor = decodeCursor(url.searchParams.get('cursor'));
    const params = [principal.userId, limit + 1];
    let cursorClause = '';
    if (cursor) {
      params.push(cursor.createdAt, cursor.id);
      cursorClause = 'AND (created_at, id) < ($3::timestamptz, $4::uuid)';
    }
    const result = await database.query(
      `SELECT id, entry_type, direction, amount_minor, currency, balance_after_minor,
              reference_type, reference_id, description, created_at
         FROM control_wallet_ledger
        WHERE user_id = $1
        ${cursorClause}
        ORDER BY created_at DESC, id DESC
        LIMIT $2`,
      params,
    );
    const hasMore = result.rows.length > limit;
    const visible = result.rows.slice(0, limit);
    const body = success(visible.map(mapEntry), requestId, clock);
    body.meta.has_more = hasMore;
    body.meta.next_cursor = hasMore && visible.length > 0 ? encodeCursor(visible[visible.length - 1]) : null;
    return { status: 200, body };
  };
}
