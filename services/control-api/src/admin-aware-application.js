import { randomUUID } from 'node:crypto';
import { createApplication, parseBody } from './application.js';
import { createAdminControlPlaneRouter } from './admin-control-plane.js';
import { createBotApprovalRouter } from './bot-approval-routes.js';
import { ApiError, failure } from './errors.js';

/**
 * Keeps the base user-facing Control API intact while mounting isolated operator and Telegram Bot
 * Approval route groups ahead of it. Bot Approval is device/session-bound and cannot mint tokens
 * directly; final session issuance is delegated back to AuthSessionService after PKCE exchange.
 */
export function createAdminAwareApplication(runtime, { clock = () => new Date() } = {}) {
  const base = createApplication({ ...runtime, clock });
  const adminRouter = createAdminControlPlaneRouter({ repository: runtime.repository, clock, parseBody });
  const botApprovalRouter = createBotApprovalRouter({
    auth: runtime.auth,
    authSession: runtime.authSession,
    botApproval: runtime.botApproval,
    parseBody,
    clock,
  });

  return async function handle(request) {
    const url = new URL(request.url);
    const botApprovalPath = url.pathname.startsWith('/v1/auth/telegram/bot/')
      || url.pathname === '/v1/internal/telegram/bot-approval';
    const adminPath = url.pathname.startsWith('/v1/admin/control-plane/');
    if (!botApprovalPath && !adminPath) return base(request);

    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      if (botApprovalPath) {
        const response = await botApprovalRouter({ request, url, requestId });
        return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
      }

      const principal = await runtime.auth.authenticate(request);
      const response = await adminRouter({
        request,
        url,
        pathname: url.pathname,
        principal,
        requestId,
      });
      return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
