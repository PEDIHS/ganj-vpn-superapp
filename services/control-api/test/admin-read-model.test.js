import assert from 'node:assert/strict';
import test from 'node:test';
import { InMemoryAdminReadModel, PostgresAdminReadModel } from '../src/admin-read-model.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T11:30:00.000Z');

test('in-memory Shared Account view exposes ownership metadata without device keys or VPN material', async () => {
  const repository = new InMemoryRepository(createSeed(NOW));
  const model = new InMemoryAdminReadModel(repository);
  const account = await model.sharedAccount(FIXTURES.users.primary);
  assert.equal(account.user.id, FIXTURES.users.primary);
  assert.ok(account.services.some((item) => item.id === FIXTURES.services.premium));
  assert.equal(JSON.stringify(account).includes('signing_public_jwk'), false);
  assert.equal(/vless:\/\/|vmess:\/\/|trojan:\/\//.test(JSON.stringify(account)), false);
  assert.equal(await model.sharedAccount('00000000-0000-4000-8000-000000000099'), null);
  assert.equal(await model.findUpstreamBinding(FIXTURES.services.premium), null);
});

test('PostgreSQL Shared Account view maps safe user device service order and upstream binding state', async () => {
  const userId = FIXTURES.users.primary;
  const serviceId = FIXTURES.services.premium;
  const calls = [];
  const db = {
    async query(sql) {
      calls.push(sql);
      if (sql.includes('FROM control_users')) return { rowCount: 1, rows: [{ id: userId, status: 'active', display_name: 'Test', locale: 'fa-IR', telegram_subject: '12345', created_at: NOW, updated_at: NOW }] };
      if (sql.includes('FROM control_devices')) return { rowCount: 1, rows: [{ id: FIXTURES.devices.primary, status: 'active', key_version: 'v1', attestation_status: 'trusted', last_seen_at: NOW, revoked_at: null, created_at: NOW }] };
      if (sql.includes('FROM control_services') && sql.includes('control_plans')) return { rowCount: 1, rows: [{ id: serviceId, plan_id: FIXTURES.plans.premium, plan_code: 'premium-30d', name: 'Premium', status: 'active', tier: 'premium', country_code: null, traffic_limit_bytes: '1000', traffic_used_bytes: '10', expires_at: NOW, device_limit: 2, allowed_protocols: ['vless'], provider_type: 'pasarguard', source_key: 'ganj-bot', external_service_id: 'svc-1', external_service_username: 'user-1', connector_ref: 'pg-eu', created_at: NOW, updated_at: NOW }] };
      if (sql.includes('FROM control_orders')) return { rowCount: 1, rows: [{ id: '33333333-3333-4333-8333-333333333333', plan_id: FIXTURES.plans.premium, service_id: serviceId, channel: 'play', status: 'fulfilled', total_amount_minor: '7490000', total_currency: 'IRR', entitlement_service_id: serviceId, fulfilled_at: NOW, created_at: NOW, updated_at: NOW }] };
      if (sql.includes('FROM control_auth_sessions')) return { rowCount: 1, rows: [{ active_count: '1' }] };
      if (sql.includes('FROM control_service_upstream_bindings')) return { rowCount: 1, rows: [{ service_id: serviceId, user_id: userId, provider_type: 'pasarguard', source_key: 'ganj-bot', external_service_id: 'svc-1', external_service_username: 'user-1', connector_ref: 'pg-eu', service_status: 'active', service_tier: 'premium' }] };
      throw new Error(`Unexpected query: ${sql}`);
    },
  };
  const model = new PostgresAdminReadModel({ database: () => db });
  const account = await model.sharedAccount(userId);
  assert.equal(account.user.telegram_linked, true);
  assert.equal(account.active_session_count, 1);
  assert.equal(account.devices[0].attestation_status, 'trusted');
  assert.equal(account.services[0].upstream.connector_ref, 'pg-eu');
  assert.equal(account.orders[0].total.amount_minor, 7_490_000);
  assert.equal(Object.hasOwn(account.devices[0], 'signing_public_jwk'), false);
  const binding = await model.findUpstreamBinding(serviceId);
  assert.equal(binding.serviceUsername, 'user-1');
  assert.ok(calls.length >= 6);
});
