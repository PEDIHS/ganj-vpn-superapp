const INVOICE_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const CUSTOMER_ID = /^[0-9]{1,20}$/;
const USERNAME = /^[A-Za-z0-9._@:-]{1,128}$/;
const FIELDS = new Set(['external_service_id', 'external_customer_id', 'service_username',
  'traffic_limit_bytes', 'traffic_used_bytes', 'traffic_usage_available']);

/** Read-only display metadata. Ownership is checked in both the projection and the live invoice. */
export function createLegacyBotServiceMetadataSource({ environment, repository, fetchImpl = fetch }) {
  const endpoint = new URL(environment.GANJ_BOT_CONNECTION_RESOLVER_URL);
  if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password || endpoint.search || endpoint.hash
    || !endpoint.pathname.endsWith('/api/internal/ganj-app/connection-v1/')) throw new Error('metadata_endpoint_invalid');
  endpoint.pathname = endpoint.pathname.replace(/connection-v1\/$/, 'service-metadata-v1/');
  const token = environment.GANJ_BOT_CONNECTION_RESOLVER_TOKEN;
  if (typeof token !== 'string' || Buffer.byteLength(token) < 32) throw new Error('metadata_token_invalid');
  const sourceKey = environment.LEGACY_SOURCE_KEY ?? 'ganj-bot-primary';
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(sourceKey)) throw new Error('metadata_source_invalid');

  async function read(records, includeUsage) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), includeUsage ? 6_000 : 2_000);
    try {
      const response = await fetchImpl(endpoint, {
        method: 'POST', redirect: 'error', signal: controller.signal,
        headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json', accept: 'application/json' },
        body: JSON.stringify({ records, include_usage: includeUsage }),
      });
      if (!response.ok || Number(response.headers.get('content-length') ?? 0) > 512 * 1024) throw new Error('metadata_unavailable');
      const raw = await response.text();
      if (Buffer.byteLength(raw) > 512 * 1024) throw new Error('metadata_response_too_large');
      const body = JSON.parse(raw);
      if (!body || !Array.isArray(body.items) || body.items.length > records.length) throw new Error('metadata_payload_invalid');
      const expected = new Set(records.map((r) => `${r.external_service_id}:${r.external_customer_id}`));
      const seen = new Set();
      for (const item of body.items) {
        const key = `${item?.external_service_id}:${item?.external_customer_id}`;
        if (!item || typeof item !== 'object' || Array.isArray(item)
          || Object.keys(item).some((k) => !FIELDS.has(k)) || !expected.has(key) || seen.has(key)
          || !(item.service_username === null || typeof item.service_username === 'string' && USERNAME.test(item.service_username))
          || typeof item.traffic_usage_available !== 'boolean'
          || !(item.traffic_limit_bytes === null || Number.isSafeInteger(item.traffic_limit_bytes) && item.traffic_limit_bytes >= 0)
          || !(item.traffic_used_bytes === null || Number.isSafeInteger(item.traffic_used_bytes) && item.traffic_used_bytes >= 0)
          || item.traffic_usage_available && item.traffic_used_bytes === null) throw new Error('metadata_record_invalid');
        seen.add(key);
      }
      return body.items;
    } finally { clearTimeout(timer); }
  }

  return Object.freeze({
    async decorate({ principal, services }) {
      if (!services.length) return [];
      const result = await repository.database().query(
        `SELECT control_service_id, external_service_id, external_customer_id
           FROM control_legacy_service_projections
          WHERE user_id = $1 AND control_service_id = ANY($2::uuid[]) AND source_key = $3`,
        [principal.userId, services.map((s) => s.id), sourceKey],
      );
      const owned = new Set(services.map((s) => s.id));
      const rows = result.rows.filter((r) => owned.has(r.control_service_id)
        && INVOICE_ID.test(r.external_service_id) && CUSTOMER_ID.test(r.external_customer_id));
      const records = rows.map((r) => ({ external_service_id: r.external_service_id, external_customer_id: r.external_customer_id }));
      const metadata = new Map();
      if (records.length) {
        // Names are cheap invoice metadata and survive a slow/unavailable usage provider.
        const merge = (items) => {
          for (const item of items) {
            const key = `${item.external_service_id}:${item.external_customer_id}`;
            metadata.set(key, { ...metadata.get(key), ...item });
          }
        };
        try { merge(await read(records, false)); } catch { /* Names can be recovered by the usage pass. */ }
        // At most three bounded reads prevent a large account from serially timing out the list.
        // Each PHP batch also has a provider deadline; late/unknown usage remains explicit.
        const batchSize = Math.ceil(records.length / 3);
        const batches = [];
        for (let offset = 0; offset < records.length; offset += batchSize) batches.push(records.slice(offset, offset + batchSize));
        await Promise.all(batches.map(async (batch) => {
          try { merge(await read(batch, true)); } catch { /* The connection path remains available. */ }
        }));
      }
      const byService = new Map(rows.map((r) => [r.control_service_id,
        metadata.get(`${r.external_service_id}:${r.external_customer_id}`)]));
      return services.map((service) => {
        if (!byService.has(service.id)) return service;
        const value = byService.get(service.id);
        return { ...service, service_username: value?.service_username ?? null,
          traffic_usage_available: value?.traffic_usage_available === true,
          ...(value?.traffic_usage_available ? {
            traffic_used_bytes: value.traffic_used_bytes,
            traffic_limit_bytes: value.traffic_limit_bytes,
          } : {}),
        };
      });
    },
  });
}
