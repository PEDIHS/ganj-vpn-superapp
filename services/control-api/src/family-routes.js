import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { ApiError, requireUuid, rejectUnknown, success } from './errors.js';
import { parseBody } from './application.js';

const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const tokenHash = token => createHash('sha256').update(token).digest('hex');
function active(service, now) {
  if (service.status !== 'active' || service.tier === 'free'
    || service.expires_at && Date.parse(service.expires_at) <= now.getTime()
    || service.traffic_limit_bytes !== null && service.traffic_used_bytes >= service.traffic_limit_bytes) {
    throw new ApiError(403,'service_inactive','An active paid service is required.');
  }
}

export class FamilyService {
  constructor(repository, clock=()=>new Date()) { this.repository=repository; this.clock=clock; }
  db() {
    if(this.repository?.kind!=='postgres-v1')throw new ApiError(503,'family_unavailable','Family sharing is unavailable.');
    return this.repository.database();
  }
  async linked(principal) {
    const r=await this.db().query("SELECT id FROM control_users WHERE id=$1 AND status='active' AND telegram_subject IS NOT NULL",[principal.userId]);
    if(!r.rowCount)throw new ApiError(403,'telegram_link_required','Link your own Telegram account first.');
  }
  async service(principal,id,ownerOnly=false) {
    requireUuid(id,'service_id');
    const service=await this.repository.findAccessibleService(principal.userId,id);
    if(!service)throw new ApiError(404,'service_not_found','Service was not found.');
    if(ownerOnly&&service.userId!==principal.userId)throw new ApiError(403,'owner_required','Only the owner can do this.');
    return service;
  }
  async access(principal,id) {
    const service=await this.service(principal,id);
    if(service.status!=='active'||service.expires_at&&Date.parse(service.expires_at)<=this.clock().getTime()||service.traffic_limit_bytes!==null&&service.traffic_used_bytes>=service.traffic_limit_bytes)throw new ApiError(403,'service_inactive','Service is unavailable.');
    const device=await this.db().query(`SELECT d.id FROM control_service_devices sd JOIN control_devices d ON d.id=sd.device_id AND d.user_id=sd.user_id
      JOIN control_users owner ON owner.id=$4 WHERE sd.service_id=$1 AND d.id=$2 AND d.user_id=$3 AND d.status='active' AND owner.status='active'`,[id,principal.deviceId,principal.userId,service.userId]);
    if(!device.rowCount)throw new ApiError(403,'device_not_trusted','Device is unavailable.');
    return {service_id:id,active:true};
  }
  async summary(principal,id) {
    const service=await this.service(principal,id);
    const db=this.db();
    const owner=service.userId===principal.userId;
    const members=await db.query(`SELECT u.id,u.display_name,m.joined_at FROM control_service_members m
      JOIN control_users u ON u.id=m.user_id WHERE m.service_id=$1 AND m.revoked_at IS NULL ORDER BY m.joined_at`,[id]);
    const devices=await db.query(`SELECT d.id,d.name,d.app_version,d.os_version,d.last_seen_at,sd.user_id
      FROM control_service_devices sd JOIN control_devices d ON d.id=sd.device_id AND d.user_id=sd.user_id
      WHERE sd.service_id=$1 AND d.status='active' AND ($2::boolean OR sd.user_id=$3) ORDER BY sd.bound_at`,[id,owner,principal.userId]);
    const invites=owner?await db.query(`SELECT id,expires_at FROM control_service_invites WHERE service_id=$1 AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at>now() ORDER BY created_at DESC`,[id]):{rows:[]};
    return {service_id:id,service_name:service.name,owner,device_limit:service.device_limit,
      slots_used:await this.repository.familySlots(id),members:members.rows.map(r=>({user_id:r.id,name:r.display_name||'کاربر گنج',current:r.id===principal.userId,joined_at:r.joined_at})),
      devices:devices.rows.map(r=>({id:r.id,name:r.name||'دستگاه اندروید',app_version:r.app_version,os_version:r.os_version,last_seen_at:r.last_seen_at,current:r.id===principal.deviceId,own:r.user_id===principal.userId})),invites:invites.rows};
  }
  async createInvite(principal,id) {
    await this.linked(principal);
    return this.repository.transaction(async()=>{
      const service=await this.service(principal,id,true); active(service,this.clock());
      if(await this.repository.familySlots(id)>=service.device_limit)throw new ApiError(409,'device_limit_reached','No free service slots.');
      const db=this.db();
      const count=await db.query('SELECT count(*) FROM control_service_invites WHERE service_id=$1 AND revoked_at IS NULL AND consumed_at IS NULL AND expires_at>now()',[id]);
      if(Number(count.rows[0].count)>=5)throw new ApiError(429,'invite_limit_reached','Revoke an older invitation first.');
      const token=randomBytes(32).toString('base64url'),inviteId=randomUUID(),expires=new Date(this.clock().getTime()+86400000).toISOString();
      await db.query('INSERT INTO control_service_invites(id,service_id,token_hash,expires_at) VALUES($1,$2,$3,$4)',[inviteId,id,tokenHash(token),expires]);
      return {id:inviteId,url:'https://bot.pedramhs.ir/app/join#'+token,expires_at:expires};
    });
  }
  async invitation(principal,token) {
    await this.linked(principal);
    if(!TOKEN.test(token??''))throw new ApiError(400,'invalid_invite','Invitation is invalid.');
    const r=await this.db().query(`SELECT i.*,s.name,s.user_id owner_id,s.status,s.tier,s.expires_at service_expires_at,
      s.traffic_limit_bytes,s.traffic_used_bytes,s.device_limit,u.display_name FROM control_service_invites i
      JOIN control_services s ON s.id=i.service_id JOIN control_users u ON u.id=s.user_id
      WHERE i.token_hash=$1 AND u.status='active'`,[tokenHash(token)]);
    const row=r.rows[0];
    if(!row || row.revoked_at || new Date(row.expires_at)<=this.clock())throw new ApiError(410,'invite_expired','Invitation expired or was revoked.');
    if(row.consumed_by && row.consumed_by!==principal.userId)throw new ApiError(409,'invite_used','Invitation has already been used.');
    active({...row,expires_at:row.service_expires_at},this.clock());
    return row;
  }
  async preview(principal,token) {
    const row=await this.invitation(principal,token);
    return {service_id:row.service_id,service_name:row.name,inviter_name:row.display_name||'کاربر گنج',device_limit:row.device_limit,expires_at:row.expires_at,own:row.owner_id===principal.userId};
  }
  async accept(principal,token) {
    const initial=await this.invitation(principal,token);
    return this.repository.transaction(async()=>{
      const db=this.db();
      // Service row serializes all joins, removals and device reservations.
      await db.query('SELECT id FROM control_services WHERE id=$1 FOR UPDATE',[initial.service_id]);
      const invite=await this.invitation(principal,token);
      if(invite.owner_id===principal.userId)throw new ApiError(409,'invite_self','The owner already has access.');
      const member=await db.query('SELECT user_id FROM control_service_members WHERE service_id=$1 AND user_id=$2 AND revoked_at IS NULL',[invite.service_id,principal.userId]);
      if(member.rowCount)return {joined:true,service_id:invite.service_id};
      if(invite.consumed_at)throw new ApiError(409,'invite_used','A removed member needs a new invitation.');
      if(await this.repository.familySlots(invite.service_id)>=invite.device_limit)throw new ApiError(409,'device_limit_reached','No free service slots.');
      await db.query(`INSERT INTO control_service_members(service_id,user_id) VALUES($1,$2)
        ON CONFLICT(service_id,user_id) DO UPDATE SET revoked_at=NULL,joined_at=now()`,[invite.service_id,principal.userId]);
      await db.query('UPDATE control_service_invites SET consumed_by=$2,consumed_at=now() WHERE id=$1',[invite.id,principal.userId]);
      return {joined:true,service_id:invite.service_id};
    });
  }
  async remove(principal,id,memberId) {
    requireUuid(memberId,'member_id');
    return this.repository.transaction(async()=>{
      const service=await this.service(principal,id);
      if(memberId===service.userId)throw new ApiError(409,'owner_cannot_leave','The owner cannot leave their own service.');
      if(principal.userId!==service.userId&&memberId!==principal.userId)throw new ApiError(403,'owner_required','Only the owner can remove other members.');
      const db=this.db();
      await db.query('UPDATE control_service_members SET revoked_at=COALESCE(revoked_at,now()) WHERE service_id=$1 AND user_id=$2',[id,memberId]);
      await db.query('DELETE FROM control_service_devices WHERE service_id=$1 AND user_id=$2',[id,memberId]);
      await db.query('DELETE FROM control_connection_profile_grants WHERE service_id=$1 AND user_id=$2',[id,memberId]);
      return {removed:true};
    });
  }
  async revokeInvite(principal,id,inviteId) {
    requireUuid(inviteId,'invite_id');
    return this.repository.transaction(async()=>{
      await this.service(principal,id,true);
      await this.db().query('UPDATE control_service_invites SET revoked_at=COALESCE(revoked_at,now()) WHERE id=$1 AND service_id=$2',[inviteId,id]);
      return {revoked:true};
    });
  }
}

export function createFamilyRouter({auth,repository,clock=()=>new Date()}) {
  const family=new FamilyService(repository,clock);
  return async({request,url,requestId})=>{
    const principal=await auth.authenticate(request);
    const path=url.pathname;
    const service=path.match(/^\/v1\/services\/([0-9a-f-]{36})\/family(?:\/(invites|members)(?:\/([0-9a-f-]{36}))?)?$/i);
    let data;
    const access=path.match(/^\/v1\/services\/([0-9a-f-]{36})\/family\/access$/i);
    if(request.method==='GET'&&access)data=await family.access(principal,access[1]);
    else if(request.method==='GET'&&service&&!service[2])data=await family.summary(principal,service[1]);
    else {
      if(request.method!=='POST')throw new ApiError(404,'route_not_found','Route not found.');
      const body=await parseBody(request,8192);
      if(!body||Array.isArray(body)||typeof body!=='object')throw new ApiError(400,'invalid_request','Expected an object.');
      const {device_proof:proof,...unsignedBody}=body;
      if(!await auth.verifyDeviceProof({principal,method:'POST',pathAndQuery:path,unsignedBody,proof}))throw new ApiError(403,'invalid_device_proof','Device proof is invalid.');
      if(path==='/v1/family/preview'||path==='/v1/family/accept') {
        rejectUnknown(unsignedBody,['token']);
        data=path.endsWith('/preview')?await family.preview(principal,body.token):await family.accept(principal,body.token);
      } else if(service) {
        rejectUnknown(unsignedBody,[]);
        if(service[2]==='invites'&&!service[3])data=await family.createInvite(principal,service[1]);
        else if(service[2]==='invites'&&service[3])data=await family.revokeInvite(principal,service[1],service[3]);
        else if(service[2]==='members'&&service[3])data=await family.remove(principal,service[1],service[3]);
        else throw new ApiError(404,'route_not_found','Route not found.');
      } else throw new ApiError(404,'route_not_found','Route not found.');
    }
    return {status:200,body:success(data,requestId,clock)};
  };
}
