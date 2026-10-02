import { ApiError, success } from './errors.js';

const REQUEST_PATH = /^\/v1\/auth\/telegram\/bot\/requests\/([0-9a-f-]{36})$/;

function exactKeys(value, allowed) {
  const keys = Object.keys(value);
  if (keys.some((key) => !allowed.has(key))) {
    throw new ApiError(400, 'invalid_request', 'Request contains unsupported fields.');
  }
}

export function createBotApprovalRouter({ auth, authSession, botApproval, parseBody, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');
  if (typeof parseBody !== 'function') throw new Error('parseBody is required.');

  async function requirePrincipal(request) {
    return auth.authenticate(request);
  }

  return async function route({ request, url, requestId }) {
    const pathname = url.pathname;
    const isBotPath = pathname.startsWith('/v1/auth/telegram/bot/')
      || pathname === '/v1/internal/telegram/bot-approval';
    if (!isBotPath) return null;
    if (!botApproval) {
      throw new ApiError(503, 'bot_approval_unavailable', 'Telegram Bot Approval is not configured.');
    }

    if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/start') {
      const principal = await requirePrincipal(request);
      const body = await parseBody(request, 8_192);
      exactKeys(body, new Set(['code_challenge', 'redirect_uri']));
      const data = await botApproval.start({
        principal,
        codeChallenge: body.code_challenge,
        redirectUri: body.redirect_uri,
      });
      return { status: 201, body: success(data, requestId, clock) };
    }

    const requestMatch = pathname.match(REQUEST_PATH);
    if (request.method === 'GET' && requestMatch) {
      const principal = await requirePrincipal(request);
      const data = await botApproval.status({ principal, requestId: requestMatch[1] });
      return { status: 200, body: success(data, requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/exchange') {
      if (typeof authSession?.linkTelegram !== 'function') {
        throw new ApiError(503, 'bot_approval_unavailable', 'Telegram account broker is unavailable.');
      }
      const principal = await requirePrincipal(request);
      const body = await parseBody(request, 8_192);
      exactKeys(body, new Set(['request_id', 'state', 'code_verifier']));
      const approved = await botApproval.consume({
        principal,
        requestId: body.request_id,
        state: body.state,
        codeVerifier: body.code_verifier,
      });
      const session = await authSession.linkTelegram({
        telegramSubject: approved.telegramSubject,
        username: approved.username,
        displayName: approved.displayName,
        deviceId: principal.deviceId,
      });
      return { status: 200, body: success(session, requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/internal/telegram/bot-approval') {
      const body = await parseBody(request, 12_288);
      exactKeys(body, new Set([
        'approval_token', 'event_id', 'decision', 'telegram_subject',
        'telegram_username', 'telegram_display_name',
      ]));
      const data = await botApproval.applyBotDecision({
        timestamp: request.headers.get('x-ganj-bot-timestamp'),
        signature: request.headers.get('x-ganj-bot-signature'),
        body,
      });
      return { status: 200, body: success(data, requestId, clock) };
    }

    throw new ApiError(404, 'route_not_found', 'Route was not found.');
  };
}
