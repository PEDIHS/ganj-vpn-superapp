import { randomUUID } from 'node:crypto';
import { failure, rejectUnknown, requireString, success } from './errors.js';
import { parseBody } from './application.js';

function optionalString(value, name, maximum) {
  if (value === undefined || value === null) return null;
  return requireString(value, name, { min: 1, max: maximum });
}

export function createTelegramBotApprovalApplication({
  baseApplication,
  auth,
  telegramBotApproval,
  clock = () => new Date(),
}) {
  if (typeof baseApplication !== 'function') throw new Error('Base Control API application is required.');
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Auth adapter is required.');
  if (!telegramBotApproval || ['start', 'decide', 'status', 'exchangeAuthorizationCode']
    .some((method) => typeof telegramBotApproval[method] !== 'function')) {
    throw new Error('Telegram Bot approval adapter is incomplete.');
  }

  return async function handle(request) {
    const url = new URL(request.url);
    const { pathname } = url;
    const botRoute = pathname.startsWith('/v1/auth/telegram/bot/')
      || pathname === '/v1/internal/telegram/bot/approval';
    if (!botRoute) return baseApplication(request);

    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      if (request.method === 'POST' && pathname === '/v1/internal/telegram/bot/approval') {
        const body = await parseBody(request, 8_192);
        rejectUnknown(body, ['request_token', 'action', 'telegram_user_id', 'username', 'display_name']);
        const result = await telegramBotApproval.decide({
          authorization: request.headers.get('authorization'),
          requestToken: requireString(body.request_token, 'request_token', { min: 43, max: 43 }),
          action: requireString(body.action, 'action', { min: 6, max: 7 }),
          telegramUserId: body.telegram_user_id,
          username: optionalString(body.username, 'username', 32),
          displayName: optionalString(body.display_name, 'display_name', 160),
        });
        return { status: 200, body: success(result, requestId, clock) };
      }

      const principal = await auth.authenticate(request);

      if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/start') {
        const body = await parseBody(request, 8_192);
        rejectUnknown(body, ['code_challenge', 'redirect_uri']);
        const result = await telegramBotApproval.start({
          principal,
          codeChallenge: requireString(body.code_challenge, 'code_challenge', { min: 43, max: 43 }),
          redirectUri: requireString(body.redirect_uri, 'redirect_uri', { min: 12, max: 2048 }),
        });
        return { status: 200, body: success(result, requestId, clock) };
      }

      if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/status') {
        const body = await parseBody(request, 4_096);
        rejectUnknown(body, ['state']);
        const result = await telegramBotApproval.status({
          principal,
          state: requireString(body.state, 'state', { min: 43, max: 43 }),
        });
        return { status: 200, body: success(result, requestId, clock) };
      }

      if (request.method === 'POST' && pathname === '/v1/auth/telegram/bot/exchange') {
        const body = await parseBody(request, 8_192);
        rejectUnknown(body, ['code', 'state', 'code_verifier']);
        const result = await telegramBotApproval.exchangeAuthorizationCode({
          principal,
          code: requireString(body.code, 'code', { min: 43, max: 43 }),
          state: requireString(body.state, 'state', { min: 43, max: 43 }),
          codeVerifier: requireString(body.code_verifier, 'code_verifier', { min: 43, max: 128 }),
        });
        return { status: 200, body: success(result, requestId, clock) };
      }

      return baseApplication(request);
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
