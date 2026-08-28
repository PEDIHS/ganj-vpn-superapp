const CONTROL_PLANE_SERVERS_PATH = /^\/v1\/admin\/control-plane\/servers(?:\/[0-9a-f-]{36})?$/i;
const FREE_SERVER_IMPORT_PATH = '/v1/admin/free/servers/import';

function isSafeAdminPath(path) {
  return path === FREE_SERVER_IMPORT_PATH || CONTROL_PLANE_SERVERS_PATH.test(path);
}

function sanitizeBaseUrl(value) {
  const raw = value || window.location.origin;
  const parsed = new URL(raw, window.location.origin);
  if (parsed.protocol !== 'https:' && parsed.hostname !== 'localhost' && parsed.hostname !== '127.0.0.1') {
    throw new Error('Admin API requires HTTPS outside local development.');
  }
  parsed.username = '';
  parsed.password = '';
  parsed.search = '';
  parsed.hash = '';
  return parsed.origin;
}

function safeMessage(error) {
  if (!error || typeof error !== 'object') return 'درخواست انجام نشد.';
  const code = typeof error.code === 'string' ? error.code : 'request_failed';
  const known = {
    unauthorized: 'نشست مدیریت معتبر نیست.',
    admin_scope_required: 'این حساب دسترسی Control Plane ندارد.',
    insufficient_scope: 'این حساب مجوز مدیریت سرور رایگان را ندارد.',
    server_not_found: 'سرور پیدا نشد.',
    free_server_not_found: 'سرور رایگان پیدا نشد.',
    server_code_conflict: 'کد سرور قبلاً استفاده شده است.',
    invalid_server_status: 'وضعیت انتخاب‌شده معتبر نیست.',
    invalid_protocols: 'پروتکل‌های انتخاب‌شده معتبر نیستند.',
    invalid_secret_reference: 'مرجع Secret Store معتبر نیست.',
    invalid_free_config: 'کانفیگ رایگان معتبر یا پشتیبانی‌شده نیست.',
    unsupported_free_protocol: 'پروتکل این کانفیگ در پلن رایگان پشتیبانی نمی‌شود.',
    secret_write_failed: 'ذخیره امن کانفیگ انجام نشد؛ دوباره تلاش کنید.',
    secret_conflict: 'برای این کد سرور قبلاً یک اتصال امن ثبت شده است.',
    unsupported_fields: 'اطلاعات ارسالی با قرارداد امن پنل سازگار نیست.',
    invalid_request: 'اطلاعات فرم کامل یا معتبر نیست.',
    raw_secret_forbidden: 'ورود کانفیگ خام در این بخش مجاز نیست.',
  };
  return known[code] ?? 'درخواست مدیریت با خطا مواجه شد.';
}

export class AdminApiError extends Error {
  constructor({ status, code, retryable = false, requestId = null }) {
    super(safeMessage({ code }));
    this.name = 'AdminApiError';
    this.status = status;
    this.code = code;
    this.retryable = retryable;
    this.requestId = requestId;
  }
}

export class AdminApiClient {
  constructor({ baseUrl, accessTokenProvider, fetchImpl = fetch }) {
    if (typeof accessTokenProvider !== 'function') throw new Error('accessTokenProvider is required.');
    if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is required.');
    this.baseUrl = sanitizeBaseUrl(baseUrl);
    this.accessTokenProvider = accessTokenProvider;
    this.fetchImpl = fetchImpl;
  }

  async request(method, path, body) {
    if (!isSafeAdminPath(path)) throw new Error('Unsafe Admin API path.');
    const token = await this.accessTokenProvider();
    if (typeof token !== 'string' || token.length < 16 || /\s/.test(token)) {
      throw new AdminApiError({ status: 401, code: 'unauthorized' });
    }
    const headers = new Headers({
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      'Cache-Control': 'no-store',
    });
    if (body !== undefined) headers.set('Content-Type', 'application/json');
    const response = await this.fetchImpl(`${this.baseUrl}${path}`, {
      method,
      headers,
      credentials: 'omit',
      redirect: 'error',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    let envelope;
    try {
      envelope = await response.json();
    } catch {
      throw new AdminApiError({ status: response.status, code: 'invalid_response' });
    }
    if (!response.ok || envelope?.error) {
      throw new AdminApiError({
        status: response.status,
        code: envelope?.error?.code ?? 'request_failed',
        retryable: envelope?.error?.retryable === true,
        requestId: envelope?.meta?.request_id ?? null,
      });
    }
    return envelope?.data;
  }

  listServers() {
    return this.request('GET', '/v1/admin/control-plane/servers');
  }

  createServer(input) {
    return this.request('POST', '/v1/admin/control-plane/servers', input);
  }

  importFreeServer(input) {
    return this.request('POST', FREE_SERVER_IMPORT_PATH, input);
  }

  updateServer(serverId, patch) {
    if (!/^[0-9a-f-]{36}$/i.test(serverId)) throw new Error('Invalid server id.');
    return this.request('PATCH', `/v1/admin/control-plane/servers/${serverId}`, patch);
  }
}

export function createMemoryTokenProvider() {
  let value = null;
  return {
    set(token) {
      if (token !== null && (typeof token !== 'string' || token.length < 16 || /\s/.test(token))) {
        throw new Error('Invalid access token.');
      }
      value = token;
    },
    clear() { value = null; },
    get() { return value; },
    provider: async () => value,
  };
}
