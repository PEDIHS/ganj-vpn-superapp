import {
  createCipheriv,
  createHash,
  createPublicKey,
  diffieHellman,
  generateKeyPairSync,
  hkdfSync,
  randomBytes,
  verify as verifySignature,
} from 'node:crypto';
import { ApiError } from '../errors.js';
import { JwksJwtVerifier } from '../security/jwt.js';
import { createPrincipalValidator } from './postgres.js';

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}

function proofAlgorithm(publicKey) {
  if (publicKey.asymmetricKeyType === 'ed25519') return { algorithm: null, key: publicKey };
  if (publicKey.asymmetricKeyType === 'ec') return { algorithm: 'sha256', key: { key: publicKey, dsaEncoding: 'ieee-p1363' } };
  if (publicKey.asymmetricKeyType === 'rsa') return { algorithm: 'RSA-SHA256', key: publicKey };
  throw new Error('Device signing key type is unsupported.');
}

export class JwksAuthAdapter {
  constructor({ verifier, principalValidator, userClaim = 'sub', deviceClaim = 'device_id', requiredTokenUse = 'access', clock = () => new Date() }) {
    if (typeof verifier?.verify !== 'function' || typeof principalValidator?.validatePrincipal !== 'function') {
      throw new Error('JWT auth dependencies are incomplete.');
    }
    this.kind = 'jwks-asymmetric-v1';
    this.verifier = verifier;
    this.principalValidator = principalValidator;
    this.userClaim = userClaim;
    this.deviceClaim = deviceClaim;
    this.requiredTokenUse = requiredTokenUse;
    this.clock = clock;
  }

  async authenticate(request) {
    const authorization = request.headers.get('authorization');
    if (!authorization?.startsWith('Bearer ') || authorization.includes(',')) {
      throw new ApiError(401, 'unauthorized', 'A bearer access token is required.');
    }
    try {
      const claims = await this.verifier.verify(authorization.slice(7));
      if (this.requiredTokenUse && claims.token_use !== this.requiredTokenUse) throw new Error('Token use is invalid.');
      const userId = claims[this.userClaim];
      const deviceId = claims[this.deviceClaim];
      if (typeof userId !== 'string' || typeof deviceId !== 'string' || typeof claims.jti !== 'string') {
        throw new Error('Required principal claims are missing.');
      }
      const validated = await this.principalValidator.validatePrincipal({ userId, deviceId, jti: claims.jti, now: this.clock() });
      if (!validated) throw new Error('Principal is revoked or untrusted.');
      return {
        userId,
        deviceId,
        subject: claims.sub,
        authMethod: 'asymmetric-jwt',
        tokenId: claims.jti,
        scopes: [
          ...(typeof claims.scope === 'string' ? claims.scope.split(' ') : []),
          ...(Array.isArray(claims.scp) ? claims.scp.filter((item) => typeof item === 'string') : []),
        ].filter(Boolean),
        signingPublicJwk: validated.signingPublicJwk,
        encryptionPublicJwk: validated.encryptionPublicJwk,
        deviceKeyVersion: validated.keyVersion,
      };
    } catch {
      throw new ApiError(401, 'unauthorized', 'Access token is invalid, expired, revoked, or device-unbound.');
    }
  }

  async verifyDeviceProof({ principal, payload, proof }) {
    try {
      if (typeof proof !== 'string' || !/^[A-Za-z0-9_-]+$/.test(proof)) return false;
      const publicKey = createPublicKey({ key: principal.signingPublicJwk, format: 'jwk' });
      const options = proofAlgorithm(publicKey);
      return verifySignature(
        options.algorithm,
        Buffer.from(JSON.stringify(canonical(payload))),
        options.key,
        Buffer.from(proof, 'base64url'),
      );
    } catch {
      return false;
    }
  }

  async sealConnectionProfile({ principal, plaintext, associatedData }) {
    const deviceKey = createPublicKey({ key: principal.encryptionPublicJwk, format: 'jwk' });
    if (deviceKey.asymmetricKeyType !== 'x25519') throw new Error('Device encryption key must be X25519.');
    const ephemeral = generateKeyPairSync('x25519');
    const sharedSecret = diffieHellman({ privateKey: ephemeral.privateKey, publicKey: deviceKey });
    const aad = Buffer.from(JSON.stringify(canonical(associatedData)));
    const salt = createHash('sha256').update(aad).digest();
    const key = Buffer.from(hkdfSync('sha256', sharedSecret, salt, Buffer.from('ganj-vpn-profile-v1'), 32));
    sharedSecret.fill(0);
    const nonce = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', key, nonce);
    cipher.setAAD(aad);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(plaintext), 'utf8'), cipher.final()]);
    key.fill(0);
    const ephemeralJwk = ephemeral.publicKey.export({ format: 'jwk' });
    const ephemeralRaw = Buffer.from(ephemeralJwk.x, 'base64url');
    const envelope = Buffer.concat([Buffer.from('GVP1'), ephemeralRaw, encrypted, cipher.getAuthTag()]);
    return {
      algorithm: 'X25519+HKDF-SHA256+AES-256-GCM/GVP1',
      keyVersion: principal.deviceKeyVersion,
      nonce: nonce.toString('base64'),
      ciphertext: envelope.toString('base64'),
    };
  }

  async close() { await this.principalValidator.close?.(); }
}

export async function createAuthAdapter({ environment = process.env } = {}) {
  for (const required of ['AUTH_JWKS_URI', 'AUTH_ISSUER', 'AUTH_AUDIENCE', 'DATABASE_URL']) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  const verifier = new JwksJwtVerifier({
    jwksUri: environment.AUTH_JWKS_URI,
    issuer: environment.AUTH_ISSUER,
    audience: environment.AUTH_AUDIENCE,
    allowedAlgorithms: (environment.AUTH_ALLOWED_ALGORITHMS ?? 'RS256').split(',').map((item) => item.trim()),
  });
  const principalValidator = await createPrincipalValidator({ environment });
  return new JwksAuthAdapter({
    verifier,
    principalValidator,
    userClaim: environment.AUTH_USER_ID_CLAIM ?? 'sub',
    deviceClaim: environment.AUTH_DEVICE_ID_CLAIM ?? 'device_id',
    requiredTokenUse: environment.AUTH_TOKEN_USE ?? 'access',
  });
}
