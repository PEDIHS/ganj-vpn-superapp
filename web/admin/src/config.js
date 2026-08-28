const HTTPS_URL = /^https:\/\/[^/]+(?:\/.*)?$/;

function required(environment, name) {
  const value = environment[name]?.trim();
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

function httpsUrl(environment, name) {
  const value = required(environment, name);
  if (!HTTPS_URL.test(value)) throw new Error(`${name} must be an absolute HTTPS URL.`);
  return new URL(value);
}

function integer(environment, name, fallback, { minimum, maximum }) {
  const value = environment[name] == null ? fallback : Number(environment[name]);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}.`);
  }
  return value;
}

export function loadConfig(environment = process.env) {
  const adapterMode = environment.ADMIN_ADAPTER_MODE ?? 'production';
  if (!['production', 'test'].includes(adapterMode)) throw new Error('Unsupported ADMIN_ADAPTER_MODE.');
  if (adapterMode === 'test' && environment.NODE_ENV === 'production') {
    throw new Error('Test adapters are forbidden in production.');
  }

  const origin = httpsUrl(environment, 'ADMIN_ORIGIN');
  const redirectUri = httpsUrl(environment, 'ADMIN_OIDC_REDIRECT_URI');
  if (redirectUri.origin !== origin.origin || redirectUri.pathname !== '/auth/callback') {
    throw new Error('ADMIN_OIDC_REDIRECT_URI must be the exact same-origin /auth/callback URL.');
  }

  const issuer = httpsUrl(environment, 'ADMIN_OIDC_ISSUER');
  const upstream = httpsUrl(environment, 'ADMIN_CONTROL_API_URL');
  const roleClaim = environment.ADMIN_OIDC_ROLE_CLAIM?.trim() || 'groups';
  if (!/^[A-Za-z0-9_.:/-]{1,128}$/.test(roleClaim)) throw new Error('ADMIN_OIDC_ROLE_CLAIM is invalid.');

  return Object.freeze({
    adapterMode,
    environment: environment.NODE_ENV ?? 'development',
    origin,
    redirectUri,
    issuer,
    upstream,
    clientId: required(environment, 'ADMIN_OIDC_CLIENT_ID'),
    clientSecret: required(environment, 'ADMIN_OIDC_CLIENT_SECRET'),
    roleClaim,
    sessionAdapterModule: adapterMode === 'production' ? required(environment, 'ADMIN_SESSION_ADAPTER_MODULE') : null,
    port: integer(environment, 'PORT', 8081, { minimum: 1, maximum: 65_535 }),
    sessionTtlSeconds: integer(environment, 'ADMIN_SESSION_TTL_SECONDS', 28_800, { minimum: 300, maximum: 43_200 }),
    oidcTimeoutMs: integer(environment, 'ADMIN_OIDC_TIMEOUT_MS', 5_000, { minimum: 500, maximum: 15_000 }),
    upstreamTimeoutMs: integer(environment, 'ADMIN_UPSTREAM_TIMEOUT_MS', 5_000, { minimum: 500, maximum: 15_000 }),
  });
}
