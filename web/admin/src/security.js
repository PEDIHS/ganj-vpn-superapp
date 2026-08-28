import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export const SESSION_COOKIE = '__Host-ganj_admin_session';

export function randomToken(bytes = 32) {
  return randomBytes(bytes).toString('base64url');
}

export function tokenDigest(value) {
  return createHash('sha256').update(value, 'utf8').digest('base64url');
}

export function constantTimeEqualDigest(expectedDigest, candidate) {
  if (typeof expectedDigest !== 'string' || typeof candidate !== 'string') return false;
  const actual = tokenDigest(candidate);
  const expectedBuffer = Buffer.from(expectedDigest);
  const actualBuffer = Buffer.from(actual);
  return expectedBuffer.length === actualBuffer.length && timingSafeEqual(expectedBuffer, actualBuffer);
}

export function parseCookies(header = '') {
  const result = {};
  for (const item of header.split(';')) {
    const separator = item.indexOf('=');
    if (separator < 1) continue;
    const name = item.slice(0, separator).trim();
    const value = item.slice(separator + 1).trim();
    if (/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name)) result[name] = value;
  }
  return result;
}

export function sessionCookie(sessionId, maximumAgeSeconds) {
  return `${SESSION_COOKIE}=${sessionId}; Path=/; Max-Age=${maximumAgeSeconds}; HttpOnly; Secure; SameSite=Lax; Priority=High`;
}

export function clearSessionCookie() {
  return `${SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax; Priority=High`;
}

export function securityHeaders({ origin, nonce }) {
  const csp = [
    "default-src 'none'",
    "base-uri 'none'",
    "frame-ancestors 'none'",
    "form-action 'self'",
    "img-src 'self' data:",
    "font-src 'self'",
    "style-src 'self'",
    "script-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "media-src 'none'",
    "worker-src 'none'",
    'upgrade-insecure-requests',
  ].join('; ');
  return {
    'cache-control': 'no-store, max-age=0',
    'content-security-policy': csp,
    'cross-origin-opener-policy': 'same-origin',
    'cross-origin-resource-policy': 'same-origin',
    'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()',
    'referrer-policy': 'no-referrer',
    'strict-transport-security': 'max-age=63072000; includeSubDomains; preload',
    'x-content-type-options': 'nosniff',
    'x-frame-options': 'DENY',
    'x-permitted-cross-domain-policies': 'none',
    'x-request-nonce': nonce,
    'origin-agent-cluster': '?1',
    'vary': 'Cookie',
    'content-location': origin.pathname || '/',
  };
}

export function assertSameOrigin(request, configuredOrigin) {
  const origin = request.headers.get('origin');
  const referer = request.headers.get('referer');
  if (origin !== configuredOrigin.origin) {
    const error = new Error('مبدأ درخواست قابل اعتماد نیست.');
    error.status = 403;
    error.code = 'invalid_origin';
    throw error;
  }
  if (referer && new URL(referer).origin !== configuredOrigin.origin) {
    const error = new Error('ارجاع درخواست قابل اعتماد نیست.');
    error.status = 403;
    error.code = 'invalid_referer';
    throw error;
  }
}

export function assertCsrf(session, candidate) {
  if (!constantTimeEqualDigest(session?.csrfDigest, candidate)) {
    const error = new Error('نشانه امنیتی فرم معتبر نیست. صفحه را تازه کنید.');
    error.status = 403;
    error.code = 'invalid_csrf';
    throw error;
  }
}
