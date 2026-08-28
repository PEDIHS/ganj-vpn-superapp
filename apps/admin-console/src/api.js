const CONTROL_PLANE_SERVERS_PATH = /^\/v1\/admin\/control-plane\/servers(?:\/[0-9a-f-]{36})?$/i;
const FREE_SERVER_IMPORT_PATH = '/v1/admin/free/servers/import';
const FREE_POLICY_PATH = '/v1/admin/free/policy';
const RECONCILIATION_SOURCES_PATH = '/v1/admin/reconciliation/sources';
const RECONCILIATION_CONFLICTS_PATH = '/v1/admin/reconciliation/conflicts';
const PASARGUARD_CONNECTORS_PATH = '/v1/admin/pasarguard/connectors';
const PASARGUARD_HEALTH_PATH = /^\/v1\/admin\/pasarguard\/connectors\/[A-Za-z0-9._:-]{1,128}\/health$/;
const PASARGUARD_DIAGNOSTIC_PATH = /^\/v1\/admin\/pasarguard\/services\/[0-9a-f-]{36}\/diagnostic$/i;
const SHARED_ACCOUNT_PATH = /^\/v1\/admin\/shared-account\/users\/[0-9a-f-]{36}$/i;
const UUID = /^[0-9a-f-]{36}$/i;
const CONNECTOR_REF = /^[A-Za-z0-9._:-]{1,128}$/;
const SOURCE_KEY = /^[A-Za-z0-9._:-]{1,128}$/;
const CONFLICT_STATUS = new Set(['open', 'resolved', 'ignored']);

function parsedRelativePath(path) {
  if (typeof path !== 'string' || path.length < 1 || path.length > 2048) return null;
  let parsed;
  try { parsed = new URL(path, 'https://admin-path.invalid'); } catch { return null; }
  if (parsed.origin !== 'https://admin-path.invalid' || parsed.hash) return null;
  return parsed;
}

function hasOnlyQueryKeys(parsed, allowed) {
  for (const key of parsed.searchParams.keys()) if (!allowed.has(key)) return false;
  return true;
}

function isSafeAdminPath(path) {
  const parsed = parsedRelativePath(path);
  if (!parsed) return false;
  const { pathname } = parsed;
  if (parsed.search && pathname !== RECONCILIATION_CONFLICTS_PATH) return false;
  if (pathname === RECONCILIATION_CONFLICTS_PATH) {
    return hasOnlyQueryKeys(parsed, new Set(['status', 'source', 'limit']));
  }
  return pathname === FREE_SERVER_IMPORT_PATH
    || pathname === FREE_POLICY_PATH
    || pathname === RECONCILIATION_SOURCES_PATH
    || pathname === PASARGUARD_CONNECTORS_PATH
    || CONTROL_PLANE_SERVERS_PATH.test(pathname)
    || PASARGUARD_HEALTH_PATH.test(pathname)
    || PASARGUARD_DIAGNOSTIC_PATH.test(pathname)
    || SHARED_ACCOUNT_PATH.test(pathname);
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
    insufficient_scope: 'این حساب مجوز لازم برای این عملیات را ندارد.',
    admin_reconciliation_scope_required: 'این حساب دسترسی مشاهده Bot Sync را ندارد.',
    server_not_found: 'سرور پیدا نشد.',
    free_server_not_found: 'سرور رایگان پیدا نشد.',
    server_code_conflict: 'کد سرور قبلاً استفاده شده است.',
    invalid_server_status: 'وضعیت انتخاب‌شده معتبر نیست.',
    invalid_protocols: 'پروتکل‌های انتخاب‌شده معتبر نیستند.',
    invalid_secret_reference: 'مرجع Secret Store معتبر نیست.',
    invalid_free_config: 'کانفیگ رایگان معتبر یا پشتیبانی‌شده نیست.',
    unsupported_free_protocol: 'پروتکل این کانفیگ در پلن رایگان پشتیبانی نمی‌شود.',
    free_config_import_unavailable: 'ورود امن کانفیگ رایگان روی این محیط فعال نیست.',
    free_config_import_rollback_failed: 'ثبت سرور انجام نشد و پاک‌سازی Secret نیز نیازمند بررسی اپراتور است.',
    secret_write_failed: 'ذخیره امن کانفیگ انجام نشد؛ دوباره تلاش کنید.',
    secret_conflict: 'برای این کد سرور قبلاً یک اتصال امن ثبت شده است.',
    unsupported_fields: 'اطلاعات ارسالی با قرارداد امن پنل سازگار نیست.',
    invalid_request: 'اطلاعات فرم کامل یا معتبر نیست.',
    raw_secret_forbidden: 'ورود کانفیگ خام در این بخش مجاز نیست.',
    invalid_conflict_status: 'وضعیت Conflict معتبر نیست.',
    invalid_source_key: 'شناسه منبع Bot Sync معتبر نیست.',
    pasarguard_admin_unavailable: 'Diagnostics پاسارگارد روی این محیط فعال نیست.',
    pasarguard_unavailable: 'PasarGuard در دسترس نیست.',
    pasarguard_auth_failed: 'احراز هویت PasarGuard ناموفق بود.',
    upstream_binding_not_found: 'اتصال سرویس به PasarGuard پیدا نشد.',
    upstream_service_not_found: 'سرویس در PasarGuard پیدا نشد.',
    account_not_found: 'حساب Ganj پیدا نشد.',
  };
  return known[code] ?? 'درخواست مدیریت با خطا مواجه شد.';
}

function requireUuid(value, name) {
  if (typeof value !== 'string' || !UUID.test(value)) throw new Error(`${name} is invalid.`);
  return value;
}

function requireConnectorRef(value) {
  if (typeof value !== 'string' || !CONNECTOR_REF.test(value)) throw new Error('Connector reference is invalid.');
  return value;
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
    requireUuid(serverId, 'server id');
    return this.request('PATCH', `/v1/admin/control-plane/servers/${serverId}`, patch);
  }

  getFreePolicy() {
    return this.request('GET', FREE_POLICY_PATH);
  }

  updateFreePolicy(patch) {
    return this.request('PATCH', FREE_POLICY_PATH, patch);
  }

  listPasarGuardConnectors() {
    return this.request('GET', PASARGUARD_CONNECTORS_PATH);
  }

  probePasarGuard(connectorRef) {
    requireConnectorRef(connectorRef);
    return this.request('GET', `/v1/admin/pasarguard/connectors/${connectorRef}/health`);
  }

  diagnosePasarGuardService(serviceId) {
    requireUuid(serviceId, 'service id');
    return this.request('GET', `/v1/admin/pasarguard/services/${serviceId}/diagnostic`);
  }

  listReconciliationSources() {
    return this.request('GET', RECONCILIATION_SOURCES_PATH);
  }

  listReconciliationConflicts({ status = 'open', source = null, limit = 100 } = {}) {
    if (!CONFLICT_STATUS.has(status)) throw new Error('Conflict status is invalid.');
    if (source != null && (typeof source !== 'string' || !SOURCE_KEY.test(source))) throw new Error('Source key is invalid.');
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 200) throw new Error('Conflict limit is invalid.');
    const query = new URLSearchParams({ status, limit: String(limit) });
    if (source) query.set('source', source);
    return this.request('GET', `${RECONCILIATION_CONFLICTS_PATH}?${query}`);
  }

  getSharedAccount(userId) {
    requireUuid(userId, 'user id');
    return this.request('GET', `/v1/admin/shared-account/users/${userId}`);
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
