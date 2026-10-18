import test from 'node:test';
import assert from 'node:assert/strict';
import { subscriptionNotices, expiryNotices, paymentNotice } from '../src/ganj-bot-notice-policy.js';
const id = '11111111-1111-4111-8111-111111111111';
const cycle = '22222222-2222-4222-8222-222222222222';
const base = { serviceId: id, cycleId: cycle, label: 'اکونومی', observedAt: '2026-10-18T10:00:00Z' };
test('usage milestone fires on crossing 80% once per cycle, never for unlimited or unknown consumption',()=>{
  const a=subscriptionNotices({...base,quotaBytes:1000,previousUsedBytes:799,usedBytes:801});
  assert.equal(a.length,1);assert.equal(a[0].kind,'subscription_usage');assert.match(a[0].title,/۸۰٪/);
  assert.equal(subscriptionNotices({...base,quotaBytes:1000,previousUsedBytes:801,usedBytes:900}).length,0);
  assert.equal(subscriptionNotices({...base,quotaBytes:null,previousUsedBytes:799,usedBytes:900}).length,0);
  assert.equal(subscriptionNotices({...base,quotaBytes:1000,previousUsedBytes:799,usedBytes:1000})[0].eventKey.includes('used_all'),true);
});
test('two-day and expired windows trigger exactly on crossing, not each repeated observation',()=>{
 const input={serviceId:id,cycleId:cycle,label:'VIP',expiresAt:'2026-10-20T12:00:00Z'};
 assert.equal(expiryNotices({...input,previousObservedAt:'2026-10-18T11:00:00Z',observedAt:'2026-10-18T13:00:00Z'})[0].eventKey.endsWith('48hr'),true);
 assert.equal(expiryNotices({...input,previousObservedAt:'2026-10-18T13:00:00Z',observedAt:'2026-10-18T14:00:00Z'}).length,0);
 assert.equal(expiryNotices({...input,previousObservedAt:'2026-10-20T11:00:00Z',observedAt:'2026-10-20T13:00:00Z'})[0].eventKey.endsWith('expired'),true);
});
test('payment transitions only; duplicate or unknown status does not notify',()=>{
 assert.equal(paymentNotice({paymentId:id,previousStatus:'pending',status:'approved'})[0].kind,'purchase_success');
 assert.equal(paymentNotice({paymentId:id,previousStatus:'reviewing',status:'rejected'})[0].kind,'payment_failure');
 assert.deepEqual(paymentNotice({paymentId:id,previousStatus:'approved',status:'approved'}),[]);
 assert.deepEqual(paymentNotice({paymentId:id,previousStatus:'pending',status:'unknown'}),[]);
});
