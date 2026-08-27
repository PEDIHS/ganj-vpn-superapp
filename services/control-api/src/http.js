import { createServer } from 'node:http';
import { Readable } from 'node:stream';

const SECURITY_HEADERS = Object.freeze({
  'cache-control': 'no-store',
  'content-type': 'application/json; charset=utf-8',
  'referrer-policy': 'no-referrer',
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
});

export function createHttpServer(handle, { logger = console } = {}) {
  return createServer(async (incoming, outgoing) => {
    const started = performance.now();
    try {
      const origin = `http://${incoming.headers.host ?? 'localhost'}`;
      const method = incoming.method ?? 'GET';
      const hasBody = method !== 'GET' && method !== 'HEAD';
      const request = new Request(new URL(incoming.url ?? '/', origin), {
        method,
        headers: incoming.headers,
        body: hasBody ? Readable.toWeb(incoming) : undefined,
        duplex: hasBody ? 'half' : undefined,
      });
      const result = await handle(request);
      const noBody = result.status === 204 || result.status === 304;
      const body = noBody ? '' : JSON.stringify(result.body);
      outgoing.writeHead(result.status, {
        ...SECURITY_HEADERS,
        'content-length': Buffer.byteLength(body),
        ...(result.headers ?? {}),
      });
      outgoing.end(noBody ? undefined : body);
      logger.info?.({
        event: 'http_request',
        method,
        path: new URL(request.url).pathname,
        status: result.status,
        duration_ms: Math.round(performance.now() - started),
      });
    } catch (error) {
      const body = JSON.stringify({
        data: null,
        meta: { request_id: null, server_time: new Date().toISOString(), has_more: false },
        error: { code: 'http_boundary_error', message: 'Request could not be processed.', retryable: true, details: {} },
      });
      outgoing.writeHead(500, { ...SECURITY_HEADERS, 'content-length': Buffer.byteLength(body) });
      outgoing.end(body);
      logger.error?.({ event: 'http_boundary_error' });
    }
  });
}
