const SAFE_API_PATH = /^\/v1\/admin\/(?:control-plane|support)\/[A-Za-z0-9/_-]*$/;

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
    admin_scope_required: 'این حساب دسترسی مدیریتی لازم را ندارد.',
    server_not_found: 'سرور پیدا نشد.',
    server_code_conflict: 'کد سرور قبلاً استفاده شده است.',
    invalid_server_status: 'وضعیت انتخاب‌شده معتبر نیست.',
    invalid_protocols: 'پروتکل‌های انتخاب‌شده معتبر نیستند.',
    invalid_secret_reference: 'مرجع Secret Store معتبر نیست.',
    raw_secret_forbidden: 'ورود کانفیگ یا credential خام مجاز نیست.',
    support_ticket_not_found: 'تیکت پشتیبانی پیدا نشد.',
    support_ticket_closed: 'این تیکت بسته است؛ ابتدا وضعیت آن را تغییر دهید.',
    invalid_support_status: 'وضعیت تیکت معتبر نیست.',
    sensitive_content_rejected: 'پیام شامل محتوای محرمانه یا کانفیگ VPN است و ارسال نشد.',
    idempotency_conflict: 'شناسه امن این عملیات قبلاً برای محتوای دیگری استفاده شده است.',
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
    if (!SAFE_API_PATH.test(path)) throw new Error('Unsafe Admin API path.');
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

  updateServer(serverId, patch) {
    if (!/^[0-9a-f-]{36}$/i.test(serverId)) throw new Error('Invalid server id.');
    return this.request('PATCH', `/v1/admin/control-plane/servers/${serverId}`, patch);
  }

  listSupportTickets() {
    return this.request('GET', '/v1/admin/support/tickets');
  }

  getSupportTicket(ticketId) {
    if (!/^[0-9a-f-]{36}$/i.test(ticketId)) throw new Error('Invalid support ticket id.');
    return this.request('GET', `/v1/admin/support/tickets/${ticketId}`);
  }

  replySupportTicket(ticketId, clientMessageId, body) {
    if (!/^[0-9a-f-]{36}$/i.test(ticketId)) throw new Error('Invalid support ticket id.');
    if (!/^[0-9a-f-]{36}$/i.test(clientMessageId)) throw new Error('Invalid support message id.');
    return this.request('POST', `/v1/admin/support/tickets/${ticketId}/messages`, {
      client_message_id: clientMessageId,
      body,
    });
  }

  updateSupportTicketStatus(ticketId, status) {
    if (!/^[0-9a-f-]{36}$/i.test(ticketId)) throw new Error('Invalid support ticket id.');
    if (!['waiting_user', 'waiting_support', 'resolved', 'closed'].includes(status)) {
      throw new Error('Invalid support status.');
    }
    return this.request('PATCH', `/v1/admin/support/tickets/${ticketId}`, { status });
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
