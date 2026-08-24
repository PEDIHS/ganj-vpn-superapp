import { createCipheriv, createHmac, createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { ApiError } from '../errors.js';

function canonical(value) {
  return JSON.stringify(Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b))));
}

export function createDeviceProof(secret, payload) {
  return createHmac('sha256', secret).update(canonical(payload)).digest('hex');
}

export function createTestAuthAdapter({ deviceSecrets }) {
  const secrets = new Map(Object.entries(deviceSecrets));
  return {
    kind: 'test-only',
    async authenticate(request) {
      const userId = request.headers.get('x-test-user-id');
      const deviceId = request.headers.get('x-test-device-id');
      if (!userId || !deviceId || !secrets.has(deviceId)) {
        throw new ApiError(401, 'unauthorized', 'Test principal headers are missing or invalid.');
      }
      const scopes = (request.headers.get('x-test-scopes') ?? '').split(' ').filter(Boolean);
      return { userId, deviceId, subject: request.headers.get('x-test-subject') ?? `test:${userId}`, scopes, authMethod: 'test-only' };
    },
    async verifyDeviceProof({ principal, payload, proof }) {
      const secret = secrets.get(principal.deviceId);
      if (!secret || typeof proof !== 'string') return false;
      const expected = Buffer.from(createDeviceProof(secret, payload), 'utf8');
      const supplied = Buffer.from(proof, 'utf8');
      return supplied.length === expected.length && timingSafeEqual(supplied, expected);
    },
    async sealConnectionProfile({ principal, plaintext, associatedData }) {
      const secret = secrets.get(principal.deviceId);
      if (!secret) throw new ApiError(403, 'device_not_trusted', 'The device is not trusted.');
      const key = createHash('sha256').update(secret).digest();
      const nonce = randomBytes(12);
      const cipher = createCipheriv('aes-256-gcm', key, nonce);
      cipher.setAAD(Buffer.from(canonical(associatedData)));
      const encrypted = Buffer.concat([cipher.update(JSON.stringify(plaintext), 'utf8'), cipher.final()]);
      const ciphertext = Buffer.concat([encrypted, cipher.getAuthTag()]);
      return {
        algorithm: 'TEST-ONLY-HMAC+AES-256-GCM',
        keyVersion: 'test-v1',
        nonce: nonce.toString('base64'),
        ciphertext: ciphertext.toString('base64'),
      };
    },
  };
}
