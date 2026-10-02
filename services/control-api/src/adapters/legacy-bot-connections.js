import { createHash } from 'node:crypto';

const CANDIDATE = /^[a-f0-9]{40}$/;
const SOURCE_KEY = /^[A-Za-z0-9._:-]{1,128}$/;

function stableUuid(value) {
  const bytes = Buffer.from(createHash('sha256').update(value).digest().subarray(0, 16));
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function validateEndpoint(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || url.search || url.hash) {
    throw new Error('Legacy connection resolver endpoint must be credential-free HTTPS.');
  }
  if (!url.pathname.endsWith('/api/internal/ganj-app/connection-v1/')) {
    throw new Error('Legacy connection resolver endpoint path is invalid.');
  }
  return url;
}

function safeConnection(item) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error('legacy_connection_invalid');
  if (typeof item.candidate_ref !== 'string' || !CANDIDATE.test(item.candidate_ref)) throw new Error('legacy_candidate_ref_invalid');
  const connection = item.connection;
  if (!connection || typeof connection !== 'object' || Array.isArray(connection)) throw new Error('legacy_connection_material_invalid');
  return {
    candidateRef: item.candidate_ref,
    name: typeof item.name === 'string' && item.name.trim() ? item.name.trim().slice(0, 160) : 'Ganj Server',
    countryCode: typeof item.country_code === 'string' && /^[A-Z]{2}$/.test(item.country_code) ? item.country_code : 'ZZ',
    city: typeof item.city === 'string' && item.city.trim() ? item.city.trim().slice(0, 120) : null,
    connection,
  };
}

export function createLegacyBotConnectionSource({ environment, repository, fetchImpl = fetch }) {
  const endpoint = validateEndpoint(environment.GANJ_BOT_CONNECTION_RESOLVER_URL ?? '');
  const bearerToken = environment.GANJ_BOT_CONNECTION_RESOLVER_TOKEN ?? '';
  if (Buffer.byteLength(bearerToken, 'utf8') < 32) throw new Error('Legacy connection resolver token is invalid.');
  if (!repository || typeof repository.database !== 'function') throw new Error('PostgreSQL repository is required for legacy connections.');

  async function projection(serviceId, userId) {
    const result = await repository.database().query(
      `SELECT source_key, external_service_id
         FROM control_legacy_service_projections
        WHERE control_service_id = $1 AND user_id = $2
        ORDER BY last_applied_at DESC NULLS LAST, updated_at DESC
        LIMIT 1`,
      [serviceId, userId],
    );
    if (result.rowCount !== 1) return null;
    const row = result.rows[0];
    if (!SOURCE_KEY.test(row.source_key)) throw new Error('legacy_projection_source_invalid');
    return { sourceKey: row.source_key, externalServiceId: row.external_service_id };
  }

  async function candidates(service, principal) {
    const mapping = await projection(service.id, principal.userId);
    if (!mapping) return [];
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5_000);
    let response;
    try {
      response = await fetchImpl(endpoint, {
        method: 'POST',
        redirect: 'error',
        signal: controller.signal,
        headers: {
          authorization: `Bearer ${bearerToken}`,
          'content-type': 'application/json',
          accept: 'application/json',
        },
        body: JSON.stringify({ external_service_id: mapping.externalServiceId }),
      });
    } finally {
      clearTimeout(timeout);
    }
    if (response.status === 404 || response.status === 409 || response.status === 422) return [];
    if (!response.ok) throw new Error(`legacy_connection_resolver_http_${response.status}`);
    const raw = await response.text();
    if (Buffer.byteLength(raw, 'utf8') > 256 * 1024) throw new Error('legacy_connection_resolver_response_too_large');
    const body = JSON.parse(raw);
    if (!body || !Array.isArray(body.connections) || body.connections.length > 100) throw new Error('legacy_connection_resolver_payload_invalid');
    return body.connections.map(safeConnection).map((candidate) => ({ ...candidate, sourceKey: mapping.sourceKey }));
  }

  function asServer(service, candidate) {
    const id = stableUuid(`${candidate.sourceKey}:${service.id}:${candidate.candidateRef}`);
    return {
      id,
      code: `legacy-${candidate.candidateRef.slice(0, 12)}`,
      name: candidate.name,
      country_code: candidate.countryCode,
      city: candidate.city,
      tier: service.tier,
      status: 'active',
      load_ratio: null,
      latency_hint_ms: null,
      protocols: [candidate.connection.protocol],
      connection: candidate.connection,
    };
  }

  return Object.freeze({
    kind: 'legacy-bot-live',
    async listServers({ principal, services }) {
      const output = [];
      for (const service of services.slice(0, 20)) {
        for (const candidate of await candidates(service, principal)) output.push(asServer(service, candidate));
      }
      const unique = new Map(output.map((server) => [server.id, server]));
      return [...unique.values()].slice(0, 200);
    },
    async resolveServer({ principal, service, serverId }) {
      for (const candidate of await candidates(service, principal)) {
        const server = asServer(service, candidate);
        if (server.id === serverId) return server;
      }
      return null;
    },
  });
}
