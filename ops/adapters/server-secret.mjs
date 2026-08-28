import { readFile } from 'node:fs/promises';

function required(environment, name) {
  const value = environment[name];
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

async function bearer(environment) {
  const path = required(environment, 'SERVER_SECRET_RESOLVER_TOKEN_FILE');
  const value = (await readFile(path, 'utf8')).trim();
  if (!value) throw new Error('Server secret resolver credential is empty.');
  return value;
}

export async function createServerSecretResolver({ environment = process.env } = {}) {
  const endpoint = new URL(required(environment, 'SERVER_SECRET_RESOLVER_URL'));
  if (endpoint.protocol !== 'https:') throw new Error('Server secret resolver must use HTTPS.');
  const token = await bearer(environment);
  return {
    kind: 'remote-secret-resolver-v1',
    async resolveServerConnection({ secretRef, serverId, protocols }) {
      if (!secretRef || !serverId || !Array.isArray(protocols)) throw new Error('Server secret lookup is incomplete.');
      const response = await fetch(new URL('/v1/server-connections:resolve', endpoint), {
        method: 'POST',
        redirect: 'error',
        signal: AbortSignal.timeout(5_000),
        headers: { authorization: `Bearer ${token}`, accept: 'application/json', 'content-type': 'application/json' },
        body: JSON.stringify({ secret_ref: secretRef, server_id: serverId, protocols }),
      });
      if (!response.ok) throw new Error(`Server secret resolver failed with status ${response.status}.`);
      const raw = await response.text();
      if (Buffer.byteLength(raw) > 32_768) throw new Error('Server secret resolver response is too large.');
      const value = JSON.parse(raw);
      if (!value || typeof value !== 'object' || typeof value.endpoint !== 'string' || typeof value.credential !== 'string') {
        throw new Error('Server secret resolver response is invalid.');
      }
      return value;
    },
  };
}
