import {
  createHash,
  createPublicKey,
  timingSafeEqual,
  verify as verifySignature,
} from 'node:crypto';

const PROOF_VERSION = 'gdp1';
const CANONICAL_SCHEMA = 'GANJ-DEVICE-PROOF-V1';
const BASE64URL = /^[A-Za-z0-9_-]+$/;
const SAFE_KEY_VERSION = /^[A-Za-z0-9_-]{1,64}$/;

function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, stable(item)]),
    );
  }
  return value;
}

export function canonicalUnsignedBody(value) {
  return Buffer.from(JSON.stringify(stable(value)), 'utf8');
}

export function parseDeviceProof(value) {
  if (typeof value !== 'string' || value.length > 512) return null;
  const parts = value.split('.');
  if (parts.length !== 6 || parts[0] !== PROOF_VERSION) return null;
  const [, timestampValue, nonce, bodySha256, keyVersion, signature] = parts;
  if (!/^[1-9][0-9]{0,12}$/.test(timestampValue)
    || !BASE64URL.test(nonce) || nonce.length < 22 || nonce.length > 128
    || !BASE64URL.test(bodySha256) || bodySha256.length !== 43
    || !SAFE_KEY_VERSION.test(keyVersion)
    || !BASE64URL.test(signature) || signature.length !== 86) {
    return null;
  }
  const timestampEpochSeconds = Number(timestampValue);
  if (!Number.isSafeInteger(timestampEpochSeconds)) return null;
  return { timestampEpochSeconds, nonce, bodySha256, keyVersion, signature };
}

export class DeviceProofVerifier {
  constructor({
    nonceStore,
    clock = () => new Date(),
    maximumClockSkewSeconds = 90,
  }) {
    if (typeof nonceStore?.consumeDeviceProofNonce !== 'function') {
      throw new Error('A durable device proof nonce store is required.');
    }
    if (!Number.isInteger(maximumClockSkewSeconds) || maximumClockSkewSeconds < 30 || maximumClockSkewSeconds > 300) {
      throw new Error('Device proof clock skew policy is invalid.');
    }
    this.nonceStore = nonceStore;
    this.clock = clock;
    this.maximumClockSkewSeconds = maximumClockSkewSeconds;
  }

  async verify({
    principal,
    method,
    pathAndQuery,
    unsignedBody,
    proof,
  }) {
    try {
      const parsed = parseDeviceProof(proof);
      if (!parsed || parsed.keyVersion !== principal.deviceKeyVersion) return false;
      if (typeof principal.userId !== 'string' || typeof principal.deviceId !== 'string') return false;
      if (typeof method !== 'string' || !/^[A-Z]{3,10}$/.test(method)) return false;
      if (typeof pathAndQuery !== 'string' || !pathAndQuery.startsWith('/')
        || pathAndQuery.length > 2_048 || /[\r\n]/.test(pathAndQuery)) return false;

      const nowSeconds = Math.floor(this.clock().getTime() / 1_000);
      if (!Number.isSafeInteger(nowSeconds)
        || Math.abs(nowSeconds - parsed.timestampEpochSeconds) > this.maximumClockSkewSeconds) return false;

      const body = canonicalUnsignedBody(unsignedBody);
      const calculatedBodyHash = createHash('sha256').update(body).digest();
      const claimedBodyHash = Buffer.from(parsed.bodySha256, 'base64url');
      const canonical = Buffer.from([
        CANONICAL_SCHEMA,
        method,
        pathAndQuery,
        parsed.bodySha256,
        String(parsed.timestampEpochSeconds),
        parsed.nonce,
        parsed.keyVersion,
      ].join('\n'), 'ascii');
      const signature = Buffer.from(parsed.signature, 'base64url');
      try {
        if (claimedBodyHash.length !== calculatedBodyHash.length
          || !timingSafeEqual(claimedBodyHash, calculatedBodyHash)
          || signature.length !== 64) return false;
        const publicKey = createPublicKey({ key: principal.signingPublicJwk, format: 'jwk' });
        if (publicKey.asymmetricKeyType !== 'ec'
          || publicKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1') return false;
        const valid = verifySignature(
          'sha256',
          canonical,
          { key: publicKey, dsaEncoding: 'ieee-p1363' },
          signature,
        );
        if (!valid) return false;
      } finally {
        body.fill(0);
        calculatedBodyHash.fill(0);
        claimedBodyHash.fill(0);
        canonical.fill(0);
        signature.fill(0);
      }

      const nonceDigest = createHash('sha256').update(parsed.nonce, 'ascii').digest('hex');
      const expiresAt = new Date(
        (parsed.timestampEpochSeconds + this.maximumClockSkewSeconds + 30) * 1_000,
      );
      return await this.nonceStore.consumeDeviceProofNonce({
        userId: principal.userId,
        deviceId: principal.deviceId,
        keyVersion: parsed.keyVersion,
        nonceDigest,
        expiresAt,
      }) === true;
    } catch {
      return false;
    }
  }
}
