import { createDecipheriv, createSign } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';

const ACCESS_TOKEN_URL='https://oauth2.googleapis.com/token';
const FCM_API='https://fcm.googleapis.com/v1/projects/';
const SCOPE='https://www.googleapis.com/auth/firebase.messaging';

export function decryptToken(sealed, hexKey) {
  if (!Buffer.isBuffer(sealed) || sealed.length < 30 ||
      !/^[0-9a-f]{64}$/i.test(hexKey ?? '')) throw new Error('push_token_decryption_unavailable');
  const decipher=createDecipheriv('aes-256-gcm',Buffer.from(hexKey,'hex'),sealed.subarray(0,12));
  decipher.setAuthTag(sealed.subarray(12,28));
  return Buffer.concat([decipher.update(sealed.subarray(28)),decipher.final()]).toString('utf8');
}

function encode(value) { return Buffer.from(JSON.stringify(value)).toString('base64url'); }

/** OAuth2 Service Account JWT, never shipped to Android or included in logs. */
export class FirebasePushSender {
  constructor({ account, projectId, fetchImpl = fetch, now = Date.now }) {
    if (!account || typeof account.client_email !== 'string' ||
        !account.client_email.endsWith('.gserviceaccount.com') ||
        !/^[a-z0-9][a-z0-9-]{4,40}$/.test(projectId ?? '') ||
        account.project_id !== projectId || !account.private_key?.includes('PRIVATE KEY')) {
      throw new Error('firebase_service_account_not_configured');
    }
    this.account=account;this.projectId=projectId;this.fetchImpl=fetchImpl;this.now=now;
    this.cached=null;
  }
  async accessToken() {
    if (this.cached && this.cached.expiry > this.now()+60_000) return this.cached.token;
    const issued=Math.floor(this.now()/1000);
    const header=encode({alg:'RS256',typ:'JWT'});
    const claim=encode({iss:this.account.client_email,scope:SCOPE,aud:ACCESS_TOKEN_URL,
      iat:issued,exp:issued+3600});
    const unsigned=header+'.'+claim;
    const signer=createSign('RSA-SHA256');signer.update(unsigned);signer.end();
    const assertion=unsigned+'.'+signer.sign(this.account.private_key).toString('base64url');
    const response=await this.fetchImpl(ACCESS_TOKEN_URL,{
      method:'POST',redirect:'error',signal:AbortSignal.timeout(5_000),
      headers:{'content-type':'application/x-www-form-urlencoded'},
      body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),
    });
    if (!response.ok) throw new Error('firebase_access_token_failed');
    const payload=await response.json();
    if (typeof payload.access_token !== 'string' ||
        !Number.isFinite(Number(payload.expires_in)) || Number(payload.expires_in)<90)
      throw new Error('firebase_access_token_invalid');
    this.cached={token:payload.access_token,expiry:this.now()+Number(payload.expires_in)*1000};
    return this.cached.token;
  }
  async send(token, event) {
    const auth=await this.accessToken();
    const important=['subscription_usage','subscription_expiry','payment_failure','security_update']
      .includes(event.kind);
    const payload={
      message:{
        token,
        // Deliberately DATA-ONLY: one native NotificationManager decides
        // per-event dedupe, account scope and OS heads-up behavior.
        data:{
          ganj_event:'1',account_id:event.userId,
          notification_id:event.id,kind:event.kind,
          title:event.title,body:event.body,created_at:event.createdAt,
        },
        android:{priority:important?'HIGH':'NORMAL',ttl:'600s'},
      },
    };
    const response=await this.fetchImpl(FCM_API+encodeURIComponent(this.projectId)+'/messages:send',{
      method:'POST',redirect:'error',signal:AbortSignal.timeout(5_000),
      headers:{authorization:'Bearer '+auth,'content-type':'application/json'},
      body:JSON.stringify(payload),
    });
    if (!response.ok) throw new Error('firebase_delivery_failed');
    return true;
  }
}

let cachedSender;
let cachedPath;
async function fromConfiguration(environment) {
  const file=environment.GANJ_FCM_SERVICE_ACCOUNT_FILE;
  const projectId=environment.GANJ_FCM_PROJECT_ID;
  if (!file || !projectId) return null;
  if (cachedSender && cachedPath===file) return cachedSender;
  const info=await stat(file);
  if (!info.isFile() || (info.mode & 0o077)!==0 || info.size>16_384)
    throw new Error('firebase_service_account_file_permissions');
  const account=JSON.parse(await readFile(file,'utf8'));
  const sender=new FirebasePushSender({account,projectId});
  cachedSender=sender;cachedPath=file;
  return sender;
}

/** Push is best-effort; the durable notification journal is authoritative. */
export async function sendCustomerPush({ database, userId, notifications, environment }) {
  if (!notifications.length) return;
  if (!/^[0-9a-f]{64}$/i.test(environment.GANJ_PUSH_TOKEN_ENCRYPTION_KEY ?? '')) return;
  const sender=await fromConfiguration(environment);
  if (!sender) return;
  const results=await database.query(
    'SELECT token_encrypted FROM control_phone_push_devices WHERE user_id = $1 ORDER BY updated_at DESC LIMIT 5',
    [userId],
  );
  for (const device of results.rows) {
    const token=decryptToken(device.token_encrypted, environment.GANJ_PUSH_TOKEN_ENCRYPTION_KEY);
    for (const item of notifications) await sender.send(token,{...item,userId});
  }
}
