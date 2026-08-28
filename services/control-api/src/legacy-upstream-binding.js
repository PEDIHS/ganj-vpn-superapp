const PROVIDERS = new Set(['pasarguard']);
const CONNECTOR = /^[A-Za-z0-9._:-]{1,128}$/;
const USERNAME = /^[A-Za-z0-9._@:-]{1,128}$/;

function safeBinding(input) {
  if (input == null) return null;
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('legacy_upstream_binding_invalid');
  const allowed = new Set(['provider_type', 'external_service_username', 'connector_ref']);
  if (Object.keys(input).some((key) => !allowed.has(key))) throw new Error('legacy_upstream_binding_unsupported_field');
  if (!PROVIDERS.has(input.provider_type)) throw new Error('legacy_upstream_binding_provider_invalid');
  if (typeof input.external_service_username !== 'string' || !USERNAME.test(input.external_service_username)) {
    throw new Error('legacy_upstream_binding_username_invalid');
  }
  if (typeof input.connector_ref !== 'string' || !CONNECTOR.test(input.connector_ref)) {
    throw new Error('legacy_upstream_binding_connector_invalid');
  }
  return Object.freeze({
    providerType: input.provider_type,
    serviceUsername: input.external_service_username,
    connectorRef: input.connector_ref,
  });
}

/**
 * Decorates the reconciliation core with an optional safe upstream-binding sink. The entitlement is
 * committed first. Only when ownership was successfully resolved does this pipeline publish the
 * server-side PasarGuard locator. No connection URI, subscription URL or credential is represented.
 */
export class LegacyProjectionPipeline {
  constructor({ reconciler, bindingSink = null }) {
    if (!reconciler || typeof reconciler.reconcile !== 'function') throw new TypeError('reconciler is required');
    if (bindingSink && typeof bindingSink.bind !== 'function') throw new TypeError('bindingSink is invalid');
    this.reconciler = reconciler;
    this.bindingSink = bindingSink;
  }

  async reconcile(input) {
    const binding = safeBinding(input?.upstream_binding);
    const result = await this.reconciler.reconcile(input);
    if (!binding || !this.bindingSink || !result.serviceId || result.outcome === 'conflict') return result;
    await this.bindingSink.bind({
      serviceId: result.serviceId,
      sourceKey: input.source_key,
      externalServiceId: input.external_service_id,
      ...binding,
    });
    return { ...result, upstreamBound: true };
  }
}

export class ControlApiUpstreamBindingSink {
  constructor({ endpoint, bearerToken, timeoutMillis = 4_000, fetchImpl = fetch }) {
    const url = new URL(endpoint);
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.search || url.hash) {
      throw new Error('Control API upstream binding endpoint must be credential-free HTTPS.');
    }
    if (!url.pathname.endsWith('/v1/internal/bot/service-upstream')) {
      throw new Error('Control API upstream binding endpoint path is invalid.');
    }
    if (typeof bearerToken !== 'string' || Buffer.byteLength(bearerToken, 'utf8') < 32) {
      throw new Error('Control API upstream binding token is invalid.');
    }
    if (!Number.isSafeInteger(timeoutMillis) || timeoutMillis < 500 || timeoutMillis > 15_000) {
      throw new Error('Control API upstream binding timeout is invalid.');
    }
    this.endpoint = url;
    this.bearerToken = bearerToken;
    this.timeoutMillis = timeoutMillis;
    this.fetchImpl = fetchImpl;
  }

  async bind(value) {
    if (value.providerType !== 'pasarguard') throw new Error('legacy_upstream_binding_provider_invalid');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMillis);
    let response;
    try {
      response = await this.fetchImpl(this.endpoint, {
        method: 'POST',
        redirect: 'error',
        signal: controller.signal,
        headers: {
          authorization: `Bearer ${this.bearerToken}`,
          'content-type': 'application/json',
          accept: 'application/json',
        },
        body: JSON.stringify({
          service_id: value.serviceId,
          source_key: value.sourceKey,
          external_service_id: value.externalServiceId,
          external_service_username: value.serviceUsername,
          connector_ref: value.connectorRef,
        }),
      });
    } catch (error) {
      const wrapped = new Error(error?.name === 'AbortError' ? 'upstream_binding_timeout' : 'upstream_binding_unavailable');
      wrapped.code = error?.name === 'AbortError' ? 'ETIMEDOUT' : 'ECONNRESET';
      wrapped.retryable = true;
      throw wrapped;
    } finally {
      clearTimeout(timeout);
    }
    if (!response.ok) {
      const error = new Error(`upstream_binding_http_${response.status}`);
      error.status = response.status;
      error.retryable = response.status === 429 || response.status >= 500;
      throw error;
    }
    return true;
  }

  toString() { return 'ControlApiUpstreamBindingSink([REDACTED])'; }
}

export { safeBinding as normalizeLegacyUpstreamBinding };
