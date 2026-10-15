import { parseBody } from './application.js';
import { ApiError, requireUuid, success } from './errors.js';

function databaseFor(repository) {
  if (repository?.kind !== 'postgres-v1' || typeof repository.database !== 'function') {
    throw new ApiError(503, 'devices_unavailable', 'Device management is unavailable.', { retryable: true });
  }
  return repository.database();
}

function mapDevice(row, currentDeviceId) {
  return {
    id: row.id,
    platform: 'android',
    name: row.name ?? null,
    app_version: row.app_version ?? null,
    os_version: row.os_version ?? null,
    status: row.status,
    current: row.id === currentDeviceId,
    last_seen_at: row.last_seen_at?.toISOString?.() ?? row.last_seen_at ?? null,
  };
}

export function createDeviceRouter({ auth, repository, clock = () => new Date() }) {
  if (!auth || typeof auth.authenticate !== 'function') throw new Error('Authentication adapter is required.');

  return async function route({ request, url, requestId }) {
    const listPath = url.pathname === '/v1/me/devices';
    const revokeMatch = url.pathname.match(/^\/v1\/me\/devices\/([0-9a-f-]{36})$/i);
    const currentPath=url.pathname==='/v1/me/devices/current';
    if (!listPath && !revokeMatch && !currentPath) return null;

    const principal = await auth.authenticate(request);
    requireUuid(principal.userId, 'authenticated user id');
    requireUuid(principal.deviceId, 'authenticated device id');

    if(request.method==='POST' && currentPath) {
      const body=await parseBody(request,4096);
      const {device_proof:proof,...unsignedBody}=body;
      if(!await auth.verifyDeviceProof({principal,method:'POST',pathAndQuery:url.pathname,unsignedBody,proof}))throw new ApiError(403,'invalid_device_proof','Device proof is invalid.');
      for(const [key,value] of Object.entries(unsignedBody)) {
        if(!['name','app_version','os_version'].includes(key)||typeof value!=='string'||value.length<1||value.length>(key==='name'?120:64)||/[\x00-\x1f\x7f]/.test(value))throw new ApiError(400,'invalid_device_metadata','Invalid device metadata.');
      }
      await databaseFor(repository).query("UPDATE control_devices SET name=COALESCE($3,name),app_version=COALESCE($4,app_version),os_version=COALESCE($5,os_version),last_seen_at=now() WHERE user_id=$1 AND id=$2 AND status='active'",[principal.userId,principal.deviceId,body.name??null,body.app_version??null,body.os_version??null]);
      return {status:200,body:success({updated:true},requestId,clock)};
    }

    if (request.method === 'GET' && listPath) {
      const database = databaseFor(repository);
      const result = await database.query(
        `SELECT id, status, name, app_version, os_version, last_seen_at, created_at
           FROM control_devices
          WHERE user_id = $1
          ORDER BY CASE WHEN id = $2 THEN 0 ELSE 1 END, created_at DESC`,
        [principal.userId, principal.deviceId],
      );
      return {
        status: 200,
        body: success(result.rows.map((row) => mapDevice(row, principal.deviceId)), requestId, clock),
      };
    }

    if (request.method === 'DELETE' && revokeMatch) {
      const deviceId = requireUuid(revokeMatch[1], 'device_id');
      if (deviceId === principal.deviceId) {
        throw new ApiError(409, 'cannot_revoke_current_device', 'The current device cannot revoke itself.');
      }
      await repository.transaction(async () => {
        const database = databaseFor(repository);
        const owned = await database.query(
          `SELECT id, status
             FROM control_devices
            WHERE user_id = $1 AND id = $2
            FOR UPDATE`,
          [principal.userId, deviceId],
        );
        if (owned.rowCount !== 1) throw new ApiError(404, 'device_not_found', 'Device was not found.');
        if (owned.rows[0].status === 'revoked') return;
        await database.query(
          `UPDATE control_devices
              SET status = 'revoked', revoked_at = COALESCE(revoked_at, now())
            WHERE user_id = $1 AND id = $2`,
          [principal.userId, deviceId],
        );
        await database.query(
          `UPDATE control_auth_sessions
              SET revoked_at = COALESCE(revoked_at, now())
            WHERE user_id = $1 AND device_id = $2 AND revoked_at IS NULL`,
          [principal.userId, deviceId],
        );
        await database.query('UPDATE control_refresh_tokens SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=$1 AND device_id=$2', [principal.userId, deviceId]);
        await database.query(
          'DELETE FROM control_service_devices WHERE user_id = $1 AND device_id = $2',
          [principal.userId, deviceId],
        );
      });
      return { status: 204, body: null };
    }

    return null;
  };
}
