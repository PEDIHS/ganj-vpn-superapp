import { randomUUID } from 'node:crypto';
import { createApplication, parseBody } from './application.js';
import { createAccountRouter } from './account-routes.js';
import { createAdminControlPlaneRouter } from './admin-control-plane.js';
import { createBotApprovalRouter } from './bot-approval-routes.js';
import { createDeviceRouter } from './device-routes.js';
import { ApiError, failure } from './errors.js';
import { createNotificationRouter } from './notification-routes.js';
import { createWalletRouter } from './wallet-routes.js';

/**
 * Keeps the base user-facing Control API intact while mounting isolated operator, account, device,
 * notification, wallet and Telegram Bot Approval route groups ahead of it. Bot Approval is
 * device/session-bound and cannot mint tokens directly; final session issuance is delegated back
 * to AuthSessionService after PKCE exchange.
 */
export function createAdminAwareApplication(runtime, { clock = () => new Date() } = {}) {
  const base = createApplication({ ...runtime, clock });
  const accountRouter = createAccountRouter({
    auth: runtime.auth,
    repository: runtime.repository,
    authSession: runtime.authSession,
    clock,
  });
  const deviceRouter = createDeviceRouter({
    auth: runtime.auth,
    repository: runtime.repository,
    clock,
  });
  const notificationRouter = createNotificationRouter({
    auth: runtime.auth,
    repository: runtime.repository,
    parseBody,
    clock,
  });
  const walletRouter = createWalletRouter({
    auth: runtime.auth,
    repository: runtime.repository,
    clock,
  });
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
    const accountPath = url.pathname === '/v1/me';
    const devicePath = url.pathname === '/v1/me/devices' || url.pathname.startsWith('/v1/me/devices/');
    const notificationPath = url.pathname === '/v1/notifications'
      || url.pathname.startsWith('/v1/notifications/');
    const walletPath = url.pathname === '/v1/wallet' || url.pathname === '/v1/wallet/transactions';
    const adminPath = url.pathname.startsWith('/v1/admin/control-plane/');
    if (!botApprovalPath && !accountPath && !devicePath && !notificationPath && !walletPath && !adminPath) {
      return base(request);
    }

    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      if (botApprovalPath) {
        const response = await botApprovalRouter({ request, url, requestId });
        return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
      }

      if (accountPath) {
        const response = await accountRouter({ request, url, requestId });
        return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
      }

      if (devicePath) {
        const response = await deviceRouter({ request, url, requestId });
        return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
      }

      if (notificationPath) {
        const response = await notificationRouter({ request, url, requestId });
        return response ?? failure(new ApiError(404, 'route_not_found', 'Route was not found.'), requestId, clock);
      }

      if (walletPath) {
        const response = await walletRouter({ request, url, requestId });
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
