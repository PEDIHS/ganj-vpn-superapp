import { ApiError, success } from './errors.js';

function mapPostgresUser(row) {
  if (row.status !== 'active') {
    throw new ApiError(403, 'account_inactive', 'The current account is not active.');
  }
  return {
    id: row.id,
    status: 'active',
    display_name: row.display_name ?? null,
    locale: 'fa-IR',
    telegram_linked: Boolean(row.telegram_subject),
    telegram_username: null,
  };
}

async function currentUser({ repository, authSession, principal }) {
  if (repository?.kind === 'postgres-v1' && typeof repository.database === 'function') {
    const result = await repository.database().query(
      `SELECT id, status, display_name, telegram_subject
         FROM control_users
        WHERE id = $1`,
      [principal.userId],
    );
    if (result.rowCount !== 1) {
      throw new ApiError(404, 'account_not_found', 'The current account was not found.');
    }
    return mapPostgresUser(result.rows[0]);
  }

  if (typeof authSession?.currentUser === 'function') {
    return authSession.currentUser(principal);
  }

  throw new ApiError(503, 'account_read_unavailable', 'Current account data is unavailable.');
}

/**
 * Privacy-minimized current-account surface. Telegram provider subject and provider credentials are
 * intentionally never returned. The production read comes from the same control_users row updated
 * by AuthSession/Bot Approval linking; test mode uses its explicit test-auth projection.
 */
export function createAccountRouter({ auth, repository, authSession, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');

  return async function route({ request, url, requestId }) {
    if (request.method !== 'GET' || url.pathname !== '/v1/me') return null;
    const principal = await auth.authenticate(request);
    const data = await currentUser({ repository, authSession, principal });
    return { status: 200, body: success(data, requestId, clock) };
  };
}
