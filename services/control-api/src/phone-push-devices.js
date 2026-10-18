import { createCipheriv, createHash, randomBytes, randomUUID } from 'node:crypto';
import { ApiError } from './errors.js';

export function encryptFcmToken(token, key) {
  if (!/^[A-Za-z0-9:_-]{64,4096}$/.test(token ?? '')) {
    throw new ApiError(400, 'invalid_push_token', 'Invalid FCM token.');
  }
  if (!Buffer.isBuffer(key) || key.length !== 32) {
    throw new ApiError(503, 'push_not_configured', 'Push is not configured.');
  }
  const nonce = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  const data = Buffer.concat([cipher.update(token, 'utf8'), cipher.final()]);
  return {
    digest: createHash('sha256').update(token).digest('hex'),
    encrypted: Buffer.concat([nonce, cipher.getAuthTag(), data]),
  };
}

export async function updatePushDevice({ database, principal, token, secretHex }) {
  if (!/^[0-9a-f]{64}$/i.test(secretHex ?? '')) {
    throw new ApiError(503, 'push_not_configured', 'Push is not configured.');
  }
  const { digest, encrypted } = encryptFcmToken(token, Buffer.from(secretHex, 'hex'));
  const client = await database.connect();
  try {
    await client.query('BEGIN');
    // A physical device's FCM token cannot simultaneously be associated with
    // different users after an account switch.
    await client.query('DELETE FROM control_phone_push_devices WHERE device_id = $1 OR token_sha256 = $2',
      [principal.deviceId, digest]);
    await client.query(
      'INSERT INTO control_phone_push_devices (id,user_id,device_id,token_sha256,token_encrypted) ' +
      'SELECT $1, d.user_id, d.id, $3, $4 FROM control_devices d ' +
      "WHERE d.id = $2 AND d.user_id = $5 AND d.status = 'active'",
      [randomUUID(), principal.deviceId, digest, encrypted, principal.userId],
    );
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}
export async function removePushDevice({ database, principal }) {
  await database.query('DELETE FROM control_phone_push_devices WHERE device_id = $1 AND user_id = $2',
    [principal.deviceId, principal.userId]);
}
