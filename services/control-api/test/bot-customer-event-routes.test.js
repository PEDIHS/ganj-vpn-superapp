import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { createBotCustomerEventRouter } from '../src/bot-customer-event-routes.js';

const now=new Date('2026-10-18T12:00:00Z');
const secret='x'.repeat(48);
const payload={
 event_id:'33333333-3333-4333-8333-333333333333',
 telegram_subject:'987654321',
 type:'subscription_usage',
 data:{
  serviceId:'11111111-1111-4111-8111-111111111111',
  cycleId:'22222222-2222-4222-8222-222222222222',
  label:'VIP', quotaBytes:1000, previousUsedBytes:700, usedBytes:810,
  observedAt:now.toISOString(),
 },
};
function event(overrides={},signSecret=secret){
 const data=JSON.stringify({...payload,...overrides});
 const timestamp=String(Math.floor(now.getTime()/1000));
 const signature=createHmac('sha256',signSecret).update(timestamp+'.'+data).digest('hex');
 const request=new Request('https://api.example.invalid/v1/internal/ganj-bot/customer-event',{
  method:'POST',
  headers:{'content-type':'application/json','x-ganj-event-time':timestamp,
    'x-ganj-event-signature':signature},
  body:data,
 });
 return {request,url:new URL(request.url),requestId:'90000000-0000-4000-8000-000000000001'};
}
function setup(notificationInserted=true){
 const calls=[];
 const client={
  async query(sql,args) {
   calls.push({sql,args});
   if (sql.startsWith('SELECT id FROM control_users')) return {
    rowCount:1, rows:[{id:'44444444-4444-4444-8444-444444444444'}] };
   if(sql.startsWith('INSERT INTO control_notifications'))
    return {rowCount: notificationInserted?1:0,rows:notificationInserted?[{id:args[0]}]:[]};
   if(sql.startsWith('INSERT INTO control_customer_event_journal'))
    return {rowCount:1,rows:[{event_seq:12}]};
   return {rowCount:0,rows:[]};
  },
  release() {},
 };
 const router=createBotCustomerEventRouter({
  repository:{kind:'postgres-v1', database(){return {async connect(){return client}}}},
  secret,clock:()=>now,
 });
 return {router,calls};
}
test('valid authenticated threshold event inserts notice, durable replay row, and commit-scoped NOTIFY',async()=>{
 const {router,calls}=setup();
 const response=await router(event());
 assert.equal(response.status,202);
 assert.equal(response.body.data.events_created,1);
 assert.equal(calls[0].sql,'BEGIN');
 assert.match(calls[1].sql,/telegram_subject = \$1/);
 assert.match(calls[2].sql,/ON CONFLICT \(user_id,event_key\)/);
 assert.equal(calls[2].args[2],'subscription_usage');
 assert.match(calls[3].sql,/INSERT INTO control_customer_event_journal/);
 assert.match(calls[4].sql,/pg_notify/);
 assert.equal(calls[5].sql,'COMMIT');
});
test('duplicate milestone saves no new notification or journal or NOTIFY',async()=>{
 const {router,calls}=setup(false);
 const response=await router(event());
 assert.equal(response.body.data.events_created,0);
 assert.equal(calls.some(c=>/event_journal|pg_notify/.test(c.sql)),false);
});
test('unsigned or incorrectly signed event is rejected before any database call',async()=>{
 const {router,calls}=setup();
 await assert.rejects(router(event({},'bad'.repeat(16))),e=>e.status===401);
 assert.equal(calls.length,0);
});
test('unknown payment state does not generate a made-up confirmation',async()=>{
 const {router,calls}=setup();
 const response=await router(event({
  type:'payment_transition',
  data:{paymentId:'11111111-1111-4111-8111-111111111111',
   previousStatus:'pending',status:'processing'},
 }));
 assert.equal(response.body.data.events_created,0);
 assert.equal(calls.some(c=>c.sql.startsWith('INSERT INTO control_notifications')),false);
});
