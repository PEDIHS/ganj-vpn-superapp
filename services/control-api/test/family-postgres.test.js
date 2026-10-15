import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID,createHash } from 'node:crypto';
import { PostgresRepository } from '../src/adapters/postgres.js';
import { FamilyService,createFamilyRouter } from '../src/family-routes.js';

const url=process.env.FAMILY_TEST_DATABASE_URL;
test('family sharing enforces real PostgreSQL ownership, capacity, replay and device rules',{skip:!url},async t=>{
  const {Pool}=await import('pg');
  const pool=new Pool({connectionString:url,max:8});
  const repo=new PostgresRepository({pool,serverSecretResolver:{async resolveServerConnection(){throw new Error('unused');}}});
  const family=new FamilyService(repo);
  async function fixture(limit=2) {
    const people=[];
    for(let i=0;i<4;i++) {
      const userId=randomUUID(),deviceId=randomUUID();
      await pool.query("INSERT INTO control_users(id,status,display_name,telegram_subject) VALUES($1,'active',$2,$3)",[userId,'Family test '+i,i===3?null:'tg-'+userId]);
      await pool.query("INSERT INTO control_devices(id,user_id,status,signing_public_jwk,encryption_public_jwk,key_version) VALUES($1,$2,'active','{}','{}','v1')",[deviceId,userId]);
      people.push({userId,deviceId});
    }
    const plan=randomUUID(),id=randomUUID();
    await pool.query("INSERT INTO control_plans(id,code,name,tier,device_limit,price_amount_minor,price_currency,channels) VALUES($1,$2,'Family test','premium',$3,1000,'IRR','{direct}')",[plan,plan,limit]);
    await pool.query("INSERT INTO control_services(id,user_id,plan_id,name,status,tier,traffic_limit_bytes,device_limit,allowed_protocols,expires_at) VALUES($1,$2,$3,'Economy','active','premium',1000000,$4,'{vless}',now()+interval '30 days')",[id,people[0].userId,plan,limit]);
    return {id,owner:people[0],member:people[1],other:people[2],guest:people[3]};
  }
  const token=x=>new URL(x.url).hash.slice(1);
  try {
    await t.test('preview never joins; acceptance is explicit and one-use; removal prevents old-link rejoin',async()=>{
      const f=await fixture();
      const invite=await family.createInvite(f.owner,f.id),raw=token(invite);
      assert.equal((await family.preview(f.member,raw)).service_name,'Economy');
      assert.equal(await repo.findAccessibleService(f.member.userId,f.id),null);
      assert.equal((await family.accept(f.member,raw)).joined,true);
      assert.equal((await family.accept(f.member,raw)).joined,true);
      assert.equal((await repo.findAccessibleService(f.member.userId,f.id)).userId,f.owner.userId);
      assert.equal(await repo.findOwnedService(f.member.userId,f.id),null);
      await assert.rejects(family.createInvite(f.member,f.id),e=>e.code==='owner_required');
      await assert.rejects(family.accept(f.other,raw),e=>e.code==='invite_used');
      await assert.rejects(family.preview(f.guest,raw),e=>e.code==='telegram_link_required');
      const stored=(await pool.query('SELECT token_hash FROM control_service_invites WHERE id=$1',[invite.id])).rows[0];
      assert.equal(stored.token_hash,createHash('sha256').update(raw).digest('hex'));assert.notEqual(stored.token_hash,raw);
      await family.remove(f.owner,f.id,f.member.userId);
      assert.equal(await repo.findAccessibleService(f.member.userId,f.id),null);
      await assert.rejects(family.accept(f.member,raw),e=>e.code==='invite_used');
      assert.equal((await pool.query('SELECT status,telegram_subject FROM control_users WHERE id=$1',[f.member.userId])).rows[0].status,'active');
    });
    await t.test('simultaneous acceptance cannot oversell the last slot',async()=>{
      const f=await fixture();
      const first=token(await family.createInvite(f.owner,f.id)),second=token(await family.createInvite(f.owner,f.id));
      const result=await Promise.allSettled([family.accept(f.member,first),family.accept(f.other,second)]);
      assert.equal(result.filter(x=>x.status==='fulfilled').length,1);
      assert.equal(result.find(x=>x.status==='rejected').reason.code,'device_limit_reached');
      assert.equal(await repo.familySlots(f.id),2);
    });
    await t.test('owner and member devices share the exact limit and leave removes only service access',async()=>{
      const f=await fixture();
      await family.accept(f.member,token(await family.createInvite(f.owner,f.id)));
      for(const p of [f.owner,f.member]) await repo.transaction(async()=>{
        const service=await repo.findAccessibleService(p.userId,f.id);await repo.bindAccessibleDevice(p,service);
      });
      const extra=randomUUID();await pool.query("INSERT INTO control_devices(id,user_id,status,signing_public_jwk,encryption_public_jwk,key_version) VALUES($1,$2,'active','{}','{}','v1')",[extra,f.owner.userId]);
      await assert.rejects(repo.transaction(async()=>repo.bindAccessibleDevice({...f.owner,deviceId:extra},await repo.findAccessibleService(f.owner.userId,f.id))),e=>e.code==='device_limit_reached');
      assert.equal((await family.access(f.member,f.id)).active,true);
      assert.equal((await family.summary(f.owner,f.id)).devices.length,2);
      assert.equal((await family.summary(f.member,f.id)).devices.length,1);
      await assert.rejects(family.remove(f.member,f.id,f.owner.userId),e=>e.code==='owner_cannot_leave');
      await family.remove(f.member,f.id,f.member.userId);
      assert.equal((await pool.query('SELECT count(*) FROM control_service_devices WHERE service_id=$1',[f.id])).rows[0].count,'1');
      assert.equal(await repo.familySlots(f.id),1);
      await assert.rejects(family.access(f.member,f.id),e=>e.code==='service_not_found');
    });
    await t.test('unrelated accounts and revoked/expired invitations fail closed',async()=>{
      const f=await fixture();const invite=await family.createInvite(f.owner,f.id),raw=token(invite);
      await assert.rejects(family.summary(f.other,f.id),e=>e.code==='service_not_found');
      await assert.rejects(family.revokeInvite(f.other,f.id,invite.id),e=>e.code==='service_not_found');
      await family.revokeInvite(f.owner,f.id,invite.id);
      await assert.rejects(family.accept(f.member,raw),e=>e.code==='invite_expired');
      const expired=await family.createInvite(f.owner,f.id);await pool.query("UPDATE control_service_invites SET expires_at=now()-interval '1 second' WHERE id=$1",[expired.id]);
      await assert.rejects(family.preview(f.member,token(expired)),e=>e.code==='invite_expired');
      await assert.rejects(family.preview(f.member,'bad'),e=>e.code==='invalid_invite');
    });
    await t.test('mutation without valid device proof never writes membership',async()=>{
      const f=await fixture(),raw=token(await family.createInvite(f.owner,f.id));
      const router=createFamilyRouter({repository:repo,auth:{async authenticate(){return f.member},async verifyDeviceProof(){return false}}});
      const request=new Request('https://test.invalid/v1/family/accept',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({token:raw,device_proof:'forged'})});
      await assert.rejects(router({request,url:new URL(request.url),requestId:randomUUID()}),e=>e.code==='invalid_device_proof');
      assert.equal(await repo.findAccessibleService(f.member.userId,f.id),null);
    });
  } finally { await pool.end(); }
});
