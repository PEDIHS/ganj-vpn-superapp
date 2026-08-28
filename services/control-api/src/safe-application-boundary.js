import { randomUUID } from 'node:crypto';
import { failure } from './errors.js';

/**
 * Final safety boundary for composed Control API routers.
 *
 * Individual routers normally convert their own ApiError values to the canonical response
 * envelope. This wrapper also catches rejected promises returned from a nested router, so an
 * asynchronous failure can never escape the HTTP application contract or leak a stack trace.
 */
export function createSafeApplicationBoundary(delegate, { clock = () => new Date() } = {}) {
  if (typeof delegate !== 'function') throw new Error('Application delegate is required.');

  return async function handle(request) {
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '')
      ? requestIdHeader
      : randomUUID();
    try {
      return await delegate(request);
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
