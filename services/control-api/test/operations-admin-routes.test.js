import assert from 'node:assert/strict';
import test from 'node:test';
import { createTestAuthAdapter } from '../src/adapters/test-auth.js';
import { createOperationsAdminApplication } from '../src/operations-admin-routes.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T11:35:00.000Z');

function request(path, scope = '') {
  return new Request(`https://control.ganj.test${path}`, {
    headers: {
      'x-test-user-id': FIXTURES.users.primary,
      'x-test-device-id': FIXTURES.devices.primary,
      'x-test-subject': 'admin:test',
      'x-test-scopes': scope,
    },
  });
}

function setup() {
  const repository = new InMemoryRepository(createSeed(NOW));
  const auth = createTestAuthAdapter({ deviceSecrets: { [FIXTURES.devices.primary]: 'secret' } });
  const connector = {
    baseUrl: 'https://panel.example.com/pasarguard',
    adminUsername: 'admin-user',
    adminPassword: 'super-secret-password',
    subscriptionOrigins: ['https://subscriptions.example.com'],
  };
  const entry = { connector, meta: { countryCode: 'DE', city: 'Frankfurt' } };
  const paidRuntime = {
    connectorRegistry: {
      connectors: new Map([['pg-eu', entry]]),
      resolve(ref) { if (ref !== 'pg-eu') throw new Error('missing'); return entry; },
    },
    liveAdapter: {
      async authenticate() { return 'upstream-token-must-never-leak'; },
      async readLiveService() {
        return { username: 'bot-user-1', status: 'active', expiresAt: '2026-09-10T00:00:00Z', dataLimitBytes: 1000, usedTrafficBytes: 100, subscriptionUrl: 'https://subscriptions.example.com/private/secret-path' };
      },
      async listSafeNodes() {
        return { service: {}, nodes: [{ id: 'a'.repeat(64), name: 'DE 1', protocol: 'vless' }, { id: 'b'.repeat(64), name: 'DE 2', protocol: 'trojan' }] };
      },
    },
  };
  const readModel = {
    async sharedAccount(userId) { return { user: { id: userId, telegram_linked: true }, active_session_count: 1, devices: [], services: [], orders: [] }; },
    async findUpstreamBinding(serviceId) {
      return { serviceId, userId: FIXTURES.users.primary, providerType: 'pasarguard', sourceKey: 'ganj-bot', externalServiceId: 'legacy-1', serviceUsername: 'bot-user-1', connectorRef: 'pg-eu', serviceStatus: 'active', serviceTier: 'premium' };
    },
  };
  const app = createOperationsAdminApplication({
    baseApplication: async () => ({ status: 404, body: {} }), repository, auth, paidRuntime, readModel,
    clock: () => new Date(NOW),
  });
  return app;
}

test('Shared Account admin view is ownership scoped and requires its dedicated scope', async () => {
  const app = setup();
  const denied = await app(request(`/v1/admin/shared-account/users/${FIXTURES.users.primary}`));
  assert.equal(denied.status, 403);
  const allowed = await app(request(`/v1/admin/shared-account/users/${FIXTURES.users.primary}`, 'admin:account:read'));
  assert.equal(allowed.status, 200);
  assert.equal(allowed.body.data.user.id, FIXTURES.users.primary);
});

test('PasarGuard connector inventory and health never expose admin credential or access token', async () => {
  const app = setup();
  const connectors = await app(request('/v1/admin/pasarguard/connectors', 'admin:pasarguard:read'));
  assert.equal(connectors.status, 200);
  assert.equal(connectors.body.data[0].connector_ref, 'pg-eu');
  assert.equal(connectors.body.data[0].credential_configured, true);
  const serialized = JSON.stringify(connectors.body);
  assert.equal(serialized.includes('super-secret-password'), false);
  assert.equal(serialized.includes('admin-user'), false);

  const health = await app(request('/v1/admin/pasarguard/connectors/pg-eu/health', 'admin:pasarguard:read'));
  assert.equal(health.status, 200);
  assert.equal(health.body.data.status, 'healthy');
  assert.equal(JSON.stringify(health.body).includes('upstream-token-must-never-leak'), false);
});

test('PasarGuard service diagnostic exposes live state and safe inventory summary only', async () => {
  const app = setup();
  const response = await app(request(`/v1/admin/pasarguard/services/${FIXTURES.services.premium}/diagnostic`, 'admin:pasarguard:read'));
  assert.equal(response.status, 200);
  assert.equal(response.body.data.live.status, 'active');
  assert.equal(response.body.data.inventory.node_count, 2);
  assert.deepEqual(response.body.data.inventory.protocols, { vless: 1, trojan: 1 });
  const serialized = JSON.stringify(response.body);
  assert.equal(serialized.includes('private/secret-path'), false);
  assert.equal(serialized.includes('subscriptionUrl'), false);
  assert.equal(serialized.includes('credential'), false);
});
