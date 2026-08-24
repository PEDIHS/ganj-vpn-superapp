import { randomUUID } from 'node:crypto';

export class ApiError extends Error {
  constructor(status, code, message, { retryable = false, details = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.retryable = retryable;
    this.details = details;
  }
}

export function meta(requestId = randomUUID(), clock = () => new Date()) {
  return {
    request_id: requestId,
    server_time: clock().toISOString(),
    has_more: false,
  };
}

export function success(data, requestId, clock) {
  return { data, meta: meta(requestId, clock), error: null };
}

export function failure(error, requestId, clock) {
  const safe = error instanceof ApiError
    ? error
    : new ApiError(500, 'internal_error', 'An unexpected server error occurred.', { retryable: true });
  return {
    status: safe.status,
    body: {
      data: null,
      meta: meta(requestId, clock),
      error: {
        code: safe.code,
        message: safe.message,
        retryable: safe.retryable,
        details: safe.details,
      },
    },
  };
}

export function requireObject(value, name = 'body') {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(400, 'invalid_request', `${name} must be a JSON object.`);
  }
  return value;
}

export function rejectUnknown(object, allowed) {
  const unknown = Object.keys(object).filter((key) => !allowed.includes(key));
  if (unknown.length > 0) {
    throw new ApiError(400, 'unsupported_fields', 'The request contains unsupported fields.', {
      details: { fields: unknown },
    });
  }
}

export function requireString(value, name, { min = 1, max = 4096 } = {}) {
  if (typeof value !== 'string' || value.length < min || value.length > max) {
    throw new ApiError(400, 'invalid_request', `${name} is invalid.`, { details: { field: name } });
  }
  return value;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function requireUuid(value, name) {
  const parsed = requireString(value, name, { min: 36, max: 36 });
  if (!UUID_PATTERN.test(parsed)) {
    throw new ApiError(400, 'invalid_request', `${name} must be a UUID.`, { details: { field: name } });
  }
  return parsed;
}

export function requireIdempotencyKey(headers) {
  const value = headers.get('idempotency-key');
  return requireString(value, 'Idempotency-Key', { min: 16, max: 255 });
}
