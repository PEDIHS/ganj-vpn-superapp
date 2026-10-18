import test from 'node:test';
import assert from 'node:assert/strict';
import { createDecipheriv } from 'node:crypto';
import { encryptFcmToken, updatePushDevice } from '../src/phone-push-devices.js';
const key=Buffer.from('a'.repeat(64),'hex');
const token='fcm:_'+'a'.repeat(140);
const principal={userId:'11111111-1111-4111-8111-111111111111',
 deviceId:'22222222-2222-4222-8222-222222222222'};
test('FCM tokens AES-256-GCM encrypted with unique nonces',()=>{
 const one=encryptFcmToken(token,key), two=encryptFcmToken(token,key);
 assert.notDeepEqual(one.encrypted,two.encrypted);
 const decipher=createDecipheriv('aes-256-gcm',key,one.encrypted.subarray(0,12));
 decipher.setAuthTag(one.encrypted.subarray(12,28));
 assert.equal(Buffer.concat([decipher.update(one.encrypted.subarray(28)),decipher.final()]).toString(),token);
 assert.equal(one.digest.length,64);
});
test('reject malformed FCM token and unconfigured key',()=>{
 assert.throws(()=>encryptFcmToken('short',key),e=>e.status===400);
 assert.throws(()=>encryptFcmToken(token,Buffer.alloc(0)),e=>e.status===503);
});
test('account-switch token registration is transactional',async()=>{
 const calls=[];
 const client={async query(sql,args){calls.push({sql,args});return {rowCount:1}},release(){}};
 await updatePushDevice({database:{async connect(){return client}},principal,token,secretHex:'a'.repeat(64)});
 assert.equal(calls[0].sql,'BEGIN');assert.match(calls[1].sql,/SELECT id FROM control_devices/);
 assert.match(calls[2].sql,/DELETE FROM control_phone_push_devices/);
 assert.match(calls[3].sql,/INSERT INTO control_phone_push_devices/);
 assert.equal(calls[4].sql,'COMMIT');
 assert.equal(calls[3].args[4],principal.userId);
});

test('unowned device cannot alter any phone registration',async()=>{
 const calls=[];
 const client={async query(sql,args){calls.push({sql,args});return {rowCount:0,rows:[]}},release(){}};
 await assert.rejects(updatePushDevice({database:{async connect(){return client}},principal,token,secretHex:'a'.repeat(64)}),
   e=>e.status===403);
 assert.equal(calls[0].sql,'BEGIN');
 assert.match(calls[1].sql,/SELECT id FROM control_devices/);
 assert.equal(calls[2].sql,'ROLLBACK');
 assert.equal(calls.some(x=>/DELETE FROM control_phone_push_devices/.test(x.sql)),false);
});
