import { constants, createPublicKey, verify as verifySignature } from 'node:crypto';

function decodeJson(segment, name) {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) throw new Error(`JWT ${name} is not base64url.`);
  const bytes = Buffer.from(segment, 'base64url');
  if (bytes.length > 16_384) throw new Error(`JWT ${name} is too large.`);
  try {
    const value = JSON.parse(bytes.toString('utf8'));
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch {
    throw new Error(`JWT ${name} is invalid JSON.`);
  }
}

function audienceMatches(actual, expected) {
  return Array.isArray(actual) ? actual.includes(expected) : actual === expected;
}

function algorithmOptions(algorithm, key) {
  if (algorithm === 'RS256') return { algorithm: 'RSA-SHA256', key };
  if (algorithm === 'PS256') {
    return { algorithm: 'RSA-SHA256', key: { key, padding: constants.RSA_PKCS1_PSS_PADDING, saltLength: 32 } };
  }
  if (algorithm === 'ES256') return { algorithm: 'sha256', key: { key, dsaEncoding: 'ieee-p1363' } };
  if (algorithm === 'EdDSA') return { algorithm: null, key };
  throw new Error('JWT algorithm is not allowed.');
}

export class JwksJwtVerifier {
  constructor({
    jwksUri,
    issuer,
    audience,
    allowedAlgorithms = ['RS256'],
    fetchImpl = fetch,
    clock = () => new Date(),
    clockSkewSeconds = 30,
    cacheTtlMs = 300_000,
  }) {
    const uri = new URL(jwksUri);
    if (uri.protocol !== 'https:') throw new Error('JWKS URI must use HTTPS.');
    if (!issuer || !audience) throw new Error('JWT issuer and audience are required.');
    this.jwksUri = uri;
    this.issuers = new Set(Array.isArray(issuer) ? issuer : [issuer]);
    this.audience = audience;
    this.allowedAlgorithms = new Set(allowedAlgorithms);
    this.fetchImpl = fetchImpl;
    this.clock = clock;
    this.clockSkewSeconds = clockSkewSeconds;
    this.cacheTtlMs = cacheTtlMs;
    this.keys = new Map();
    this.keysExpireAt = 0;
    this.refreshPromise = null;
  }

  async refreshKeys() {
    if (this.refreshPromise) return this.refreshPromise;
    this.refreshPromise = (async () => {
      const response = await this.fetchImpl(this.jwksUri, {
        headers: { accept: 'application/json' },
        redirect: 'error',
        signal: AbortSignal.timeout(5_000),
      });
      if (!response.ok) throw new Error(`JWKS endpoint returned ${response.status}.`);
      const text = await response.text();
      if (Buffer.byteLength(text, 'utf8') > 1_048_576) throw new Error('JWKS response is too large.');
      const document = JSON.parse(text);
      if (!Array.isArray(document.keys) || document.keys.length === 0 || document.keys.length > 100) {
        throw new Error('JWKS document is invalid.');
      }
      const next = new Map();
      for (const jwk of document.keys) {
        if (typeof jwk?.kid !== 'string' || typeof jwk?.kty !== 'string') continue;
        next.set(jwk.kid, jwk);
      }
      if (next.size === 0) throw new Error('JWKS contains no usable keys.');
      this.keys = next;
      this.keysExpireAt = this.clock().getTime() + this.cacheTtlMs;
    })().finally(() => { this.refreshPromise = null; });
    return this.refreshPromise;
  }

  async key(kid, algorithm) {
    if (this.clock().getTime() >= this.keysExpireAt) await this.refreshKeys();
    let jwk = this.keys.get(kid);
    if (!jwk) {
      await this.refreshKeys();
      jwk = this.keys.get(kid);
    }
    if (!jwk || (jwk.alg && jwk.alg !== algorithm) || (jwk.use && jwk.use !== 'sig')) {
      throw new Error('JWT signing key is unavailable or incompatible.');
    }
    return createPublicKey({ key: jwk, format: 'jwk' });
  }

  async verify(token) {
    if (typeof token !== 'string' || token.length < 32 || token.length > 32_768) throw new Error('JWT is malformed.');
    const segments = token.split('.');
    if (segments.length !== 3 || !segments.every(Boolean)) throw new Error('JWT is malformed.');
    const header = decodeJson(segments[0], 'header');
    const claims = decodeJson(segments[1], 'claims');
    if (typeof header.kid !== 'string' || !this.allowedAlgorithms.has(header.alg)) throw new Error('JWT header is not allowed.');
    if (!/^[A-Za-z0-9_-]+$/.test(segments[2])) throw new Error('JWT signature is not base64url.');
    const signature = Buffer.from(segments[2], 'base64url');
    if (signature.length === 0) throw new Error('JWT signature is missing.');
    const publicKey = await this.key(header.kid, header.alg);
    const options = algorithmOptions(header.alg, publicKey);
    const valid = verifySignature(options.algorithm, Buffer.from(`${segments[0]}.${segments[1]}`), options.key, signature);
    if (!valid) throw new Error('JWT signature is invalid.');

    const now = Math.floor(this.clock().getTime() / 1000);
    const skew = this.clockSkewSeconds;
    if (!this.issuers.has(claims.iss) || !audienceMatches(claims.aud, this.audience)) throw new Error('JWT issuer or audience is invalid.');
    if (!Number.isFinite(claims.exp) || claims.exp <= now - skew) throw new Error('JWT is expired.');
    if (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || claims.nbf > now + skew)) throw new Error('JWT is not active.');
    if (claims.iat !== undefined && (!Number.isFinite(claims.iat) || claims.iat > now + skew)) throw new Error('JWT issued-at claim is invalid.');
    return claims;
  }
}
