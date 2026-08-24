import {
  createHash,
  createHmac,
  generateKeyPairSync,
  randomUUID,
  sign as signPayload,
} from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { ApiError, rejectUnknown, requireObject, requireString, requireUuid, success } from './errors.js';

const TIERS = Object.freeze({ free: 0, premium: 1, vip: 2 });
const ENVIRONMENTS = new Set(['development', 'testing', 'staging', 'production']);
const PURPOSES = new Set(['essential', 'product_analytics', 'crash_diagnostics', 'personalized_marketing', 'support_diagnostics']);
const CATEGORIES = new Set(['connection', 'purchase', 'account', 'ui', 'performance', 'security', 'other']);
const SUPPORT_CATEGORIES = new Set(['connection', 'billing', 'account', 'security', 'feedback', 'other']);
const PRIORITIES = new Set(['urgent', 'high', 'normal']);
const NETWORK_TYPES = new Set(['wifi', 'cellular', 'ethernet', 'unknown']);
const CONNECTION_STATES = new Set(['disconnected', 'connecting', 'connected', 'disconnecting', 'unknown']);
const TEST_KINDS = new Set(['network', 'dns_resolver', 'server_ping', 'packet_loss', 'vpn_state', 'device_integrity']);
const TEST_OUTCOMES = new Set(['passed', 'warning', 'failed', 'unavailable']);
const EVENT_PROPERTIES = Object.freeze({
  'app.installed': new Set(['app_version', 'locale', 'source']),
  'app.opened': new Set(['app_version', 'locale', 'source']),
  'vpn.connect_started': new Set(['tier', 'network_type', 'selection_method']),
  'vpn.connect_succeeded': new Set(['tier', 'network_type', 'selection_method', 'latency_bucket']),
  'vpn.connect_failed': new Set(['tier', 'network_type', 'selection_method', 'result_code']),
  'subscription.checkout_started': new Set(['plan_code', 'channel', 'source']),
  'subscription.purchase_completed': new Set(['plan_code', 'channel', 'currency']),
  'subscription.purchase_failed': new Set(['plan_code', 'channel', 'result_code']),
  'service.selected': new Set(['tier', 'source']),
  'experiment.exposed': new Set(['experiment_key', 'variant']),
});
const FORBIDDEN_KEY = /(config|credential|secret|password|private.?key|access.?token|refresh.?token|purchase.?token|endpoint|hostname|destination|dns.?query|raw.?ip|advertising.?id|manual.?import)/i;
const SECRET_LIKE = /(?:vless|vmess|trojan|ss|wireguard):\/\/|-----BEGIN [A-Z ]*PRIVATE KEY-----|\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/i;

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, stable(item)]));
  }
  return value;
}

function canonical(value) { return JSON.stringify(stable(value)); }
function hash(value) { return createHash('sha256').update(canonical(value)).digest('hex'); }

function uuidFromDigest(buffer) {
  const bytes = Buffer.from(buffer.subarray(0, 16));
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function semver(value, name) {
  const input = requireString(value, name, { min: 1, max: 32 });
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(input)) throw new ApiError(400, 'invalid_request', `${name} must be semantic version.`);
  return input;
}

function compareVersions(left, right) {
  const a = left.split('-', 1)[0].split('.').map(Number);
  const b = right.split('-', 1)[0].split('.').map(Number);
  for (let index = 0; index < 3; index += 1) if (a[index] !== b[index]) return a[index] - b[index];
  return 0;
}

function instant(value, name, now, { pastDays = 30, futureMinutes = 5 } = {}) {
  const input = requireString(value, name, { min: 20, max: 40 });
  const parsed = Date.parse(input);
  if (!Number.isFinite(parsed)) throw new ApiError(400, 'invalid_request', `${name} must be an RFC 3339 timestamp.`);
  if (parsed < now.getTime() - pastDays * 86_400_000 || parsed > now.getTime() + futureMinutes * 60_000) {
    throw new ApiError(400, 'invalid_timestamp', `${name} is outside the accepted time window.`);
  }
  return new Date(parsed).toISOString();
}

function bool(value, name, expected) {
  if (typeof value !== 'boolean' || expected !== undefined && value !== expected) {
    throw new ApiError(400, 'invalid_request', `${name} is invalid.`);
  }
  return value;
}

function enumValue(value, name, allowed) {
  const parsed = requireString(value, name, { min: 1, max: 128 });
  if (!allowed.has(parsed)) throw new ApiError(400, 'invalid_request', `${name} is invalid.`);
  return parsed;
}

function safeText(value, name, limits) {
  const parsed = requireString(value, name, limits).trim();
  if (SECRET_LIKE.test(parsed)) throw new ApiError(400, 'sensitive_content_rejected', `${name} appears to contain private connection material.`);
  return parsed;
}

function validateSafeTree(value, name = 'value', depth = 0) {
  if (depth > 6) throw new ApiError(400, 'invalid_request', `${name} is too deeply nested.`);
  if (typeof value === 'string') {
    if (value.length > 2048 || SECRET_LIKE.test(value)) throw new ApiError(400, 'sensitive_content_rejected', `${name} contains prohibited content.`);
    return;
  }
  if (value === null || typeof value === 'boolean' || typeof value === 'number') return;
  if (Array.isArray(value)) {
    if (value.length > 100) throw new ApiError(400, 'invalid_request', `${name} has too many items.`);
    value.forEach((item, index) => validateSafeTree(item, `${name}[${index}]`, depth + 1));
    return;
  }
  requireObject(value, name);
  if (Object.keys(value).length > 100) throw new ApiError(400, 'invalid_request', `${name} has too many properties.`);
  for (const [key, item] of Object.entries(value)) {
    if (FORBIDDEN_KEY.test(key)) throw new ApiError(400, 'sensitive_content_rejected', `${name} contains a prohibited property.`);
    validateSafeTree(item, `${name}.${key}`, depth + 1);
  }
}

function validateTarget(value = {}) {
  const target = requireObject(value, 'target');
  rejectUnknown(target, ['tiers', 'locales', 'platforms', 'min_app_version', 'max_app_version']);
  const result = {};
  for (const field of ['tiers', 'locales', 'platforms']) {
    if (target[field] === undefined) continue;
    if (!Array.isArray(target[field]) || target[field].length < 1 || target[field].length > 20) throw new ApiError(400, 'invalid_request', `target.${field} is invalid.`);
    result[field] = [...new Set(target[field].map((item) => requireString(item, `target.${field}`, { min: 2, max: 32 })))];
  }
  if (result.tiers?.some((item) => !(item in TIERS))) throw new ApiError(400, 'invalid_request', 'target.tiers is invalid.');
  if (result.locales?.some((item) => !/^[a-z]{2,3}(?:-[A-Z]{2})?$/.test(item))) throw new ApiError(400, 'invalid_request', 'target.locales is invalid.');
  if (result.platforms?.some((item) => item !== 'android')) throw new ApiError(400, 'invalid_request', 'Only the Android platform is currently supported.');
  if (target.min_app_version !== undefined) result.min_app_version = semver(target.min_app_version, 'target.min_app_version');
  if (target.max_app_version !== undefined) result.max_app_version = semver(target.max_app_version, 'target.max_app_version');
  if (result.min_app_version && result.max_app_version && compareVersions(result.min_app_version, result.max_app_version) > 0) {
    throw new ApiError(400, 'invalid_request', 'Target app version range is invalid.');
  }
  return result;
}

function matchesTarget(target, context) {
  if (target.tiers?.length && !target.tiers.includes(context.tier)) return false;
  if (target.locales?.length && !target.locales.includes(context.locale)) return false;
  if (target.platforms?.length && !target.platforms.includes(context.platform)) return false;
  if (target.min_app_version && compareVersions(context.appVersion, target.min_app_version) < 0) return false;
  if (target.max_app_version && compareVersions(context.appVersion, target.max_app_version) > 0) return false;
  return true;
}

function requireScope(principal, scope) {
  if (!principal.scopes?.includes(scope)) throw new ApiError(403, 'insufficient_scope', `The ${scope} scope is required.`);
}

function highestTier(services, now) {
  return services.filter((item) => item.status === 'active'
    && (!item.expires_at || Date.parse(item.expires_at) > now.getTime())
    && (item.traffic_limit_bytes === null || item.traffic_used_bytes < item.traffic_limit_bytes))
    .reduce((best, item) => TIERS[item.tier] > TIERS[best] ? item.tier : best, 'free');
}

function asBug(report) {
  return {
    id: report.id, public_code: report.publicCode, title: report.title, category: report.category,
    severity: report.severity, status: report.status, created_at: report.createdAt, updated_at: report.updatedAt,
  };
}

function asTicket(ticket) {
  return {
    id: ticket.id, public_code: ticket.publicCode, category: ticket.category, priority: ticket.priority, subject: ticket.subject,
    status: ticket.status, created_at: ticket.createdAt, updated_at: ticket.updatedAt,
  };
}

export class EnterpriseSecurity {
  constructor({ assignmentSecret, signingPrivateKey, keyVersion = 'runtime-config-ed25519-v1' }) {
    if (!Buffer.isBuffer(assignmentSecret) || assignmentSecret.length < 32 || !signingPrivateKey) throw new Error('Enterprise security material is incomplete.');
    this.kind = 'ed25519-hmac-v1';
    this.assignmentSecret = Buffer.from(assignmentSecret);
    this.signingPrivateKey = signingPrivateKey;
    this.keyVersion = keyVersion;
  }

  assign({ subject, flagKey, rolloutBasisPoints }) {
    const digest = createHmac('sha256', this.assignmentSecret).update(`assignment:v1:${flagKey}:${subject}`).digest();
    return {
      selected: digest.readUInt32BE(0) % 10_000 < rolloutBasisPoints,
      assignmentId: uuidFromDigest(digest),
    };
  }

  sign(value) {
    return signPayload(null, Buffer.from(canonical(value)), this.signingPrivateKey).toString('base64');
  }
}

export function createTestEnterpriseSecurity() {
  const { privateKey } = generateKeyPairSync('ed25519');
  const adapter = new EnterpriseSecurity({ assignmentSecret: Buffer.alloc(32, 7), signingPrivateKey: privateKey, keyVersion: 'test-ed25519-v1' });
  adapter.kind = 'test-only';
  return adapter;
}

export async function createEnterpriseSecurityAdapter({ environment = process.env } = {}) {
  if (!environment.CONTROL_API_ASSIGNMENT_SECRET_FILE || !environment.CONTROL_API_RUNTIME_CONFIG_PRIVATE_KEY_FILE) {
    throw new Error('CONTROL_API_ASSIGNMENT_SECRET_FILE and CONTROL_API_RUNTIME_CONFIG_PRIVATE_KEY_FILE are required.');
  }
  const [assignmentSecret, signingPrivateKey] = await Promise.all([
    readFile(environment.CONTROL_API_ASSIGNMENT_SECRET_FILE),
    readFile(environment.CONTROL_API_RUNTIME_CONFIG_PRIVATE_KEY_FILE, 'utf8'),
  ]);
  return new EnterpriseSecurity({
    assignmentSecret,
    signingPrivateKey,
    keyVersion: environment.CONTROL_API_RUNTIME_CONFIG_KEY_VERSION ?? 'runtime-config-ed25519-v1',
  });
}

export function createEnterpriseRouter({ repository, security, clock, parseBody }) {
  if (!security || typeof security.assign !== 'function' || typeof security.sign !== 'function') throw new Error('Enterprise security adapter is required.');

  return async function route({ request, url, pathname, principal, requestId }) {
    const now = clock();

    if (request.method === 'GET' && pathname === '/v1/app/runtime-config') {
      const platform = request.headers.get('x-platform');
      if (platform !== 'android') throw new ApiError(400, 'unsupported_platform', 'Only Android runtime configuration is available.');
      const appVersion = semver(request.headers.get('x-app-version'), 'X-App-Version');
      const locale = requireString((request.headers.get('accept-language') ?? 'fa-IR').split(',')[0].trim(), 'Accept-Language', { min: 2, max: 32 });
      if (!/^[a-z]{2,3}(?:-[A-Z]{2})?$/.test(locale)) throw new ApiError(400, 'invalid_locale', 'Accept-Language locale is invalid.');
      const tier = highestTier(await repository.listServices(principal.userId), now);
      const context = { platform, appVersion, locale, tier };
      const runtime = await repository.getPublishedRuntimeConfiguration('production');
      const version = runtime.release?.version ?? 1;
      const values = {};
      for (const entry of runtime.release?.entries ?? []) {
        if (entry.sensitivity === 'public' && matchesTarget(entry.target ?? {}, context)) values[entry.key] = entry.value;
      }
      const featureAssignments = [];
      for (const flag of await repository.listCurrentFeatureFlags()) {
        const audienceMatch = matchesTarget(flag.audience ?? {}, context);
        const assignment = security.assign({ subject: `${principal.userId}:${principal.deviceId}`, flagKey: `${flag.flagKey}:v${flag.version}`, rolloutBasisPoints: flag.rolloutBasisPoints });
        const enabled = Boolean(flag.enabled && audienceMatch && assignment.selected);
        featureAssignments.push({
          key: flag.flagKey,
          enabled,
          variant: enabled ? flag.defaultVariant : 'off',
          assignment_id: assignment.assignmentId,
          config_version: flag.version,
        });
      }
      featureAssignments.sort((a, b) => a.key.localeCompare(b.key));
      const policyWindowMs = 5 * 60_000;
      const expiresAt = new Date((Math.floor(now.getTime() / policyWindowMs) + 1) * policyWindowMs).toISOString();
      const unsigned = { version, values, feature_assignments: featureAssignments, expires_at: expiresAt, key_version: security.keyVersion };
      const etag = `\"${hash(unsigned)}\"`;
      if (request.headers.get('if-none-match') === etag) return { status: 304, body: null, headers: { etag } };
      const data = { ...unsigned, etag, signature: security.sign(unsigned) };
      return { status: 200, body: success(data, requestId, clock), headers: { etag, 'cache-control': 'private, max-age=300' } };
    }

    if (request.method === 'GET' && pathname === '/v1/me/consents') {
      const data = (await repository.listConsents(principal.userId)).map((item) => ({
        receipt_id: item.id, purpose: item.purpose, granted: item.status === 'granted', policy_version: item.policyVersion,
        recorded_at: item.revokedAt ?? item.grantedAt,
      }));
      return { status: 200, body: success(data, requestId, clock) };
    }

    if (request.method === 'PUT' && pathname === '/v1/me/consents') {
      const body = await parseBody(request);
      rejectUnknown(body, ['purpose', 'granted', 'policy_version']);
      const purpose = enumValue(body.purpose, 'purpose', PURPOSES);
      const granted = bool(body.granted, 'granted');
      if (purpose === 'essential' && !granted) throw new ApiError(400, 'essential_consent_required', 'Essential processing cannot be disabled while the account is active.');
      const policyVersion = requireString(body.policy_version, 'policy_version', { min: 1, max: 64 });
      await repository.transaction(() => repository.saveConsent({
        userId: principal.userId, purpose, status: granted ? 'granted' : 'revoked', policyVersion,
        grantedAt: now.toISOString(), revokedAt: granted ? null : now.toISOString(),
      }));
      return { status: 204, body: null };
    }

    if (request.method === 'POST' && pathname === '/v1/telemetry/events:batch') {
      const body = await parseBody(request, 65_536);
      rejectUnknown(body, ['batch_id', 'consent_receipt_id', 'sent_at', 'events']);
      const batchId = requireUuid(body.batch_id, 'batch_id');
      const consentReceiptId = requireUuid(body.consent_receipt_id, 'consent_receipt_id');
      instant(body.sent_at, 'sent_at', now, { pastDays: 7 });
      const consent = await repository.findConsent(principal.userId, consentReceiptId, 'product_analytics');
      if (!consent || consent.status !== 'granted' || consent.revokedAt) throw new ApiError(403, 'analytics_consent_required', 'A current analytics consent receipt is required.');
      if (!Array.isArray(body.events) || body.events.length < 1 || body.events.length > 100) throw new ApiError(400, 'invalid_request', 'events must contain 1 to 100 items.');
      const seen = new Set();
      const events = body.events.map((input, index) => {
        const event = requireObject(input, `events[${index}]`);
        rejectUnknown(event, ['event_id', 'name', 'occurred_at', 'schema_version', 'session_id', 'properties']);
        const eventId = requireUuid(event.event_id, `events[${index}].event_id`);
        if (seen.has(eventId)) throw new ApiError(400, 'duplicate_event_id', 'An event ID is duplicated within the batch.');
        seen.add(eventId);
        const name = requireString(event.name, `events[${index}].name`, { min: 3, max: 128 });
        const allowed = EVENT_PROPERTIES[name];
        if (!allowed) throw new ApiError(400, 'unsupported_analytics_event', 'Analytics event is not allowlisted.');
        if (!Number.isInteger(event.schema_version) || event.schema_version !== 1) throw new ApiError(400, 'unsupported_event_schema', 'Analytics schema version is unsupported.');
        const properties = event.properties === undefined ? {} : requireObject(event.properties, `events[${index}].properties`);
        const unknown = Object.keys(properties).filter((key) => !allowed.has(key) || FORBIDDEN_KEY.test(key));
        if (unknown.length) throw new ApiError(400, 'unsupported_analytics_properties', 'Analytics event contains unsupported properties.', { details: { fields: unknown } });
        for (const [key, value] of Object.entries(properties)) {
          if (!['string', 'number', 'boolean'].includes(typeof value) || typeof value === 'string' && (value.length > 64 || SECRET_LIKE.test(value))) {
            throw new ApiError(400, 'invalid_analytics_property', `Analytics property ${key} must be low-cardinality.`);
          }
        }
        return {
          event_id: eventId, name, occurred_at: instant(event.occurred_at, `events[${index}].occurred_at`, now, { pastDays: 7 }),
          schema_version: 1,
          session_id: event.session_id == null ? null : requireUuid(event.session_id, `events[${index}].session_id`),
          properties,
        };
      });
      const payloadDigest = hash({ consentReceiptId, events });
      const result = await repository.transaction(() => repository.ingestAnalyticsBatch({
        userId: principal.userId, batchId, consentReceiptId, payloadDigest, events, receivedAt: now.toISOString(),
      }));
      return { status: 202, body: success({ batch_id: batchId, accepted_count: result.acceptedCount, duplicate_count: result.duplicateCount, replayed: result.replay }, requestId, clock) };
    }

    if (request.method === 'GET' && pathname === '/v1/reliability/bug-reports') {
      return { status: 200, body: success((await repository.listBugReports(principal.userId)).map(asBug), requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/reliability/bug-reports') {
      const body = await parseBody(request);
      rejectUnknown(body, ['client_report_id', 'title', 'description', 'category', 'device_id', 'occurred_at', 'automatic_context_consent', 'context']);
      const clientReportId = requireUuid(body.client_report_id, 'client_report_id');
      const deviceId = requireUuid(body.device_id, 'device_id');
      if (deviceId !== principal.deviceId || !await repository.ownsDevice(principal.userId, deviceId)) throw new ApiError(403, 'device_mismatch', 'Bug report device is not owned by the current user.');
      bool(body.automatic_context_consent, 'automatic_context_consent', true);
      const category = enumValue(body.category, 'category', CATEGORIES);
      const context = body.context === undefined ? {} : requireObject(body.context, 'context');
      rejectUnknown(context, ['app_version', 'os_version', 'device_model', 'connection_state', 'server_id', 'network_type', 'error_code']);
      if (context.connection_state !== undefined) enumValue(context.connection_state, 'context.connection_state', CONNECTION_STATES);
      if (context.network_type !== undefined) enumValue(context.network_type, 'context.network_type', NETWORK_TYPES);
      if (context.server_id != null) requireUuid(context.server_id, 'context.server_id');
      validateSafeTree(context, 'context');
      const normalized = {
        clientReportId,
        title: safeText(body.title, 'title', { min: 3, max: 160 }),
        description: safeText(body.description, 'description', { min: 10, max: 5000 }),
        category, deviceId, occurredAt: instant(body.occurred_at, 'occurred_at', now), context,
      };
      const payloadDigest = hash(normalized);
      const result = await repository.transaction(() => repository.createBugReport({
        ...normalized, userId: principal.userId, payloadDigest,
        publicCode: `BUG-${hash({ userId: principal.userId, clientReportId }).slice(0, 16).toUpperCase()}`,
        severity: category === 'security' ? 'critical' : ['connection', 'purchase'].includes(category) ? 'high' : category === 'performance' ? 'medium' : 'low',
        status: 'new', createdAt: now.toISOString(), updatedAt: now.toISOString(),
      }));
      return { status: result.replay ? 200 : 201, body: success(asBug(result.report), requestId, clock) };
    }

    if (request.method === 'GET' && pathname === '/v1/support/tickets') {
      return { status: 200, body: success((await repository.listSupportTickets(principal.userId)).map(asTicket), requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/support/tickets') {
      const body = await parseBody(request);
      rejectUnknown(body, ['client_ticket_id', 'category', 'priority', 'subject', 'body']);
      const normalized = {
        clientTicketId: requireUuid(body.client_ticket_id, 'client_ticket_id'),
        category: enumValue(body.category, 'category', SUPPORT_CATEGORIES),
        priority: body.priority === undefined ? 'normal' : enumValue(body.priority, 'priority', PRIORITIES),
        subject: safeText(body.subject, 'subject', { min: 3, max: 160 }),
        body: safeText(body.body, 'body', { min: 10, max: 5000 }),
      };
      const result = await repository.transaction(() => repository.createSupportTicket({
        ...normalized, userId: principal.userId, payloadDigest: hash(normalized), status: 'open',
        publicCode: `TKT-${hash({ userId: principal.userId, clientTicketId: normalized.clientTicketId }).slice(0, 16).toUpperCase()}`,
        createdAt: now.toISOString(), updatedAt: now.toISOString(),
      }));
      return { status: result.replay ? 200 : 201, body: success(asTicket(result.ticket), requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/diagnostics/reports') {
      const body = await parseBody(request, 65_536);
      rejectUnknown(body, ['client_report_id', 'device_id', 'bug_report_id', 'support_ticket_id', 'explicit_consent', 'started_at', 'completed_at', 'tests']);
      bool(body.explicit_consent, 'explicit_consent', true);
      const deviceId = requireUuid(body.device_id, 'device_id');
      if (deviceId !== principal.deviceId || !await repository.ownsDevice(principal.userId, deviceId)) throw new ApiError(403, 'device_mismatch', 'Diagnostic device is not owned by the current user.');
      const startedAt = instant(body.started_at, 'started_at', now);
      const finishedAt = instant(body.completed_at, 'completed_at', now);
      if (Date.parse(finishedAt) < Date.parse(startedAt) || Date.parse(finishedAt) - Date.parse(startedAt) > 15 * 60_000) throw new ApiError(400, 'invalid_diagnostic_window', 'Diagnostic time window is invalid.');
      const bugReportId = body.bug_report_id == null ? null : requireUuid(body.bug_report_id, 'bug_report_id');
      const supportTicketId = body.support_ticket_id == null ? null : requireUuid(body.support_ticket_id, 'support_ticket_id');
      if (bugReportId && !await repository.findOwnedBugReport(principal.userId, bugReportId)) throw new ApiError(404, 'bug_report_not_found', 'Bug report was not found.');
      if (supportTicketId && !await repository.findOwnedSupportTicket(principal.userId, supportTicketId)) throw new ApiError(404, 'support_ticket_not_found', 'Support ticket was not found.');
      if (!Array.isArray(body.tests) || body.tests.length < 1 || body.tests.length > 50) throw new ApiError(400, 'invalid_request', 'tests must contain 1 to 50 items.');
      const tests = body.tests.map((input, index) => {
        const item = requireObject(input, `tests[${index}]`);
        rejectUnknown(item, ['kind', 'outcome', 'latency_ms', 'packet_loss_ratio', 'result_code']);
        const result = {
          kind: enumValue(item.kind, `tests[${index}].kind`, TEST_KINDS),
          outcome: enumValue(item.outcome, `tests[${index}].outcome`, TEST_OUTCOMES),
        };
        if (item.latency_ms != null && (!Number.isInteger(item.latency_ms) || item.latency_ms < 0 || item.latency_ms > 120_000)) throw new ApiError(400, 'invalid_request', 'latency_ms is invalid.');
        if (item.packet_loss_ratio != null && (typeof item.packet_loss_ratio !== 'number' || item.packet_loss_ratio < 0 || item.packet_loss_ratio > 1)) throw new ApiError(400, 'invalid_request', 'packet_loss_ratio is invalid.');
        if (item.result_code != null) result.result_code = requireString(item.result_code, `tests[${index}].result_code`, { min: 1, max: 64 });
        if (item.latency_ms != null) result.latency_ms = item.latency_ms;
        if (item.packet_loss_ratio != null) result.packet_loss_ratio = item.packet_loss_ratio;
        return result;
      });
      const normalized = { clientReportId: requireUuid(body.client_report_id, 'client_report_id'), deviceId, bugReportId, supportTicketId, startedAt, finishedAt, tests };
      const created = await repository.transaction(() => repository.createDiagnosticReport({
        ...normalized, userId: principal.userId, payloadDigest: hash(normalized), redactionVersion: 'diagnostics-v1',
        expiresAt: new Date(now.getTime() + 30 * 86_400_000).toISOString(), createdAt: now.toISOString(),
      }));
      return { status: created.replay ? 200 : 201, body: success({ id: created.report.id, redaction_version: created.report.redactionVersion, expires_at: created.report.expiresAt }, requestId, clock) };
    }

    if (request.method === 'POST' && pathname === '/v1/admin/remote-config/releases') {
      requireScope(principal, 'admin:config:write');
      const body = await parseBody(request, 65_536);
      rejectUnknown(body, ['environment', 'version', 'reason', 'entries']);
      const environment = enumValue(body.environment, 'environment', ENVIRONMENTS);
      if (!Number.isSafeInteger(body.version) || body.version < 1) throw new ApiError(400, 'invalid_request', 'version is invalid.');
      const reason = safeText(body.reason, 'reason', { min: 8, max: 500 });
      if (!Array.isArray(body.entries) || body.entries.length < 1 || body.entries.length > 200) throw new ApiError(400, 'invalid_request', 'entries must contain 1 to 200 items.');
      const seen = new Set();
      const entries = body.entries.map((input, index) => {
        const entry = requireObject(input, `entries[${index}]`);
        rejectUnknown(entry, ['key', 'value', 'sensitivity', 'target']);
        const key = requireString(entry.key, `entries[${index}].key`, { min: 3, max: 128 });
        if (!/^[a-z][a-z0-9_.-]{2,127}$/.test(key) || FORBIDDEN_KEY.test(key) || seen.has(key)) throw new ApiError(400, 'invalid_config_key', 'Remote configuration key is invalid, duplicate, or prohibited.');
        seen.add(key);
        validateSafeTree(entry.value, `entries[${index}].value`);
        return { key, value: entry.value, sensitivity: enumValue(entry.sensitivity, `entries[${index}].sensitivity`, new Set(['public', 'internal'])), target: validateTarget(entry.target ?? {}) };
      });
      const created = await repository.transaction(async () => {
        const release = await repository.createRemoteConfigRelease({
          environment, version: body.version, reason, entries, createdBy: principal.subject,
          contentDigest: hash(entries), createdAt: now.toISOString(),
        });
        await repository.appendAdminAudit({ actorSubject: principal.subject, action: 'remote_config.create', resourceType: 'remote_config_release', resourceId: release.id, reason, requestId, afterDigest: release.contentDigest, outcome: 'success', createdAt: now.toISOString() });
        return release;
      });
      return { status: 201, body: success({ id: created.id, environment: created.environment, version: created.version, status: created.status }, requestId, clock) };
    }

    const publishMatch = pathname.match(/^\/v1\/admin\/remote-config\/releases\/([0-9a-f-]{36})\/publish$/i);
    if (request.method === 'POST' && publishMatch) {
      requireScope(principal, 'admin:config:publish');
      const body = await parseBody(request);
      rejectUnknown(body, ['reason']);
      const reason = safeText(body.reason, 'reason', { min: 8, max: 500 });
      const releaseId = requireUuid(publishMatch[1], 'release_id');
      const published = await repository.transaction(async () => {
        const release = await repository.publishRemoteConfigRelease({ releaseId, publisher: principal.subject, publishedAt: now.toISOString() });
        if (!release) throw new ApiError(404, 'config_release_not_found', 'Remote configuration release was not found.');
        await repository.appendAdminAudit({ actorSubject: principal.subject, action: 'remote_config.publish', resourceType: 'remote_config_release', resourceId: release.id, reason, requestId, afterDigest: release.contentDigest, outcome: 'success', createdAt: now.toISOString() });
        return release;
      });
      return { status: 200, body: success({ id: published.id, environment: published.environment, version: published.version, status: published.status }, requestId, clock) };
    }

    const flagMatch = pathname.match(/^\/v1\/admin\/feature-flags\/([a-z][a-z0-9_.-]{2,127})$/);
    if (request.method === 'PUT' && flagMatch) {
      requireScope(principal, 'admin:flags:write');
      const body = await parseBody(request);
      rejectUnknown(body, ['enabled', 'default_variant', 'rollout_percent', 'audience', 'experiment_key', 'reason']);
      bool(body.enabled, 'enabled');
      const defaultVariant = requireString(body.default_variant, 'default_variant', { min: 1, max: 64 });
      if (typeof body.rollout_percent !== 'number' || body.rollout_percent < 0 || body.rollout_percent > 100) throw new ApiError(400, 'invalid_request', 'rollout_percent is invalid.');
      const audience = validateTarget(body.audience ?? {});
      const reason = safeText(body.reason, 'reason', { min: 8, max: 500 });
      const stored = await repository.transaction(async () => {
        const flag = await repository.upsertFeatureFlag({
          flagKey: flagMatch[1], enabled: body.enabled, defaultVariant,
          rolloutBasisPoints: Math.round(body.rollout_percent * 100), audience,
          experimentKey: body.experiment_key == null ? null : requireString(body.experiment_key, 'experiment_key', { min: 1, max: 128 }),
          reason, createdBy: principal.subject, createdAt: now.toISOString(),
        });
        await repository.appendAdminAudit({ actorSubject: principal.subject, action: 'feature_flag.version', resourceType: 'feature_flag', resourceId: flag.flagKey, reason, requestId, afterDigest: hash(flag), outcome: 'success', createdAt: now.toISOString() });
        return flag;
      });
      return { status: 200, body: success({ key: stored.flagKey, version: stored.version }, requestId, clock) };
    }

    if (request.method === 'GET' && pathname === '/v1/admin/audit-log') {
      requireScope(principal, 'admin:audit:read');
      const data = (await repository.listAdminAudit({ limit: 100 })).map((item) => ({
        id: item.id, actor_subject: item.actorSubject, action: item.action, resource_type: item.resourceType,
        resource_id: item.resourceId, reason: item.reason, request_id: item.requestId,
        outcome: item.outcome, created_at: item.createdAt,
      }));
      return { status: 200, body: success(data, requestId, clock) };
    }

    return null;
  };
}
