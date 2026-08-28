import { randomUUID } from 'node:crypto';
import { failure } from './errors.js';

/**
 * Adds an unauthenticated readiness probe without weakening application route authorization.
 * Liveness remains /healthz in the base application. Readiness proves the repository can execute a
 * real query after migrations and production adapters have successfully initialized.
 */
export function withOperationalReadiness(application, { repository, clock = () => new Date() }) {
  if (typeof application !== 'function') throw new Error('Application handler is required.');
  if (!repository || typeof repository.listPlans !== 'function') throw new Error('Repository readiness boundary is required.');

  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.pathname !== '/readyz') return application(request);
    const requestIdHeader = request.headers.get('x-request-id');
    const requestId = /^[0-9a-f-]{36}$/i.test(requestIdHeader ?? '') ? requestIdHeader : randomUUID();
    try {
      await repository.listPlans('direct');
      return { status: 200, body: { status: 'ready' } };
    } catch (error) {
      return failure(error, requestId, clock);
    }
  };
}
