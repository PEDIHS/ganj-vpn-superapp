import { randomUUID } from 'node:crypto';
import { createApplication, parseBody } from './application.js';
import { createAdminControlPlaneRouter } from './admin-control-plane.js';
import { failure } from './errors.js';

/**
 * Keeps the user-facing Control API unchanged while mounting the operator-only control plane under
 * /v1/admin/control-plane/*. The admin path authenticates with the exact same bearer/session
 * boundary and additionally requires the dedicated admin:control-plane scope.
 */
export function createAdminAwareApplication(runtime, { clock = () => new Date() } = {}) {
  const base = createApplication({ ...runtime, clock });
  const adminRouter = createAdminControlPlaneRouter({ repository: runtime.repository, clock, parseBody });

  return async function handle(request) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/v1/admin/control-plane/')) return base(request);

    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      const principal = await runtime.auth.authenticate(request);
      const response = await adminRouter({
        request,
        url,
        pathname: url.pathname,
        principal,
        requestId,
      });
      return response ?? failure(new Error('Admin route was not found.'), requestId, clock);
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
