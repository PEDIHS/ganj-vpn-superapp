import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { withFreeAccess } from '../src/free-access.js';
import { withFreePolicy } from '../src/free-policy.js';
import { withFreeServerRegistry } from '../src/free-server-registry.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T11:20:00.000Z');

function setup() {
  const seed = createSeed(NOW);
  seed.services.find((item) => item.id === FIXTURES.services.exhausted).traffic_used_bytes = 0;
  const base = new InMemoryRepository(seed);
  const repository = withFreeServerRegistry(withFreePolicy(withFreeAccess(base, { clock: () => new Date(NOW) }), {
    clock: () => new Date(NOW),
    policy: {
      enabled: true, maintenance: false, emergencyDisabled: false, issueWindowSeconds: 3600,
      maxIssuesPerUser: 100, maxIssuesPerDevice: 100, maxActiveGrantsPerUser: 100,
      maxActiveGrantsPerDevice: 100, maxActiveGrantsPerServer: 100,
    },
  }), { clock: () => new Date(NOW) });
  return { repository, base };
}

function grant(userId, deviceId, serviceId) {
  return {
    profileId: randomUUID(), userId, deviceId, serviceId, serverId: FIXTURES.servers.free,
    clientNonceDigest: randomUUID().replaceAll('-', ''), expiresAt: '2026-08-28T11:25:00.000Z',
  };
}

test('registry priority sorts eligible Free servers without reordering paid policy semantics', async () => {
  const { repository } = setup();
  const original = await repository.findManagedFreeServer(FIXTURES.servers.free);
  await repository.updateManagedFreeServer(FIXTURES.servers.free, { priority: 50 });
  await repository.createManagedFreeServer({
    ...original, id: randomUUID(), code: 'nl-free-00', name: 'Netherlands Free 00', priority: 10,
    secretRef: 'vault://free/nl-00', status: 'active', emergencyDisabled: false, maxLoadRatio: 1,
  });
  const free = (await repository.listServers()).filter((item) => item.tier === 'free');
  assert.equal(free[0].code, 'nl-free-00');
  assert.equal(free[1].id, FIXTURES.servers.free);
});

test('per-server active profile grant cap is enforced atomically across Free users', async () => {
  const { repository } = setup();
  await repository.updateManagedFreeServer(FIXTURES.servers.free, { maxActiveProfileGrants: 1 });
  const secondary = (await repository.listServices(FIXTURES.users.secondary)).find((item) => item.tier === 'free');
  const primary = (await repository.listServices(FIXTURES.users.primary)).find((item) => item.tier === 'free');
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(
    grant(FIXTURES.users.secondary, FIXTURES.devices.secondary, secondary.id),
  )), true);
  await assert.rejects(
    () => repository.transaction(() => repository.reserveConnectionProfile(
      grant(FIXTURES.users.primary, FIXTURES.devices.primary, primary.id),
    )),
    (error) => error?.code === 'free_server_capacity_reached' && error?.details?.dimension === 'server',
  );
});

test('unknown production adapters cannot silently bypass the Free Server Registry', () => {
  const delegate = {
    kind: 'custom-production', transaction: async (work) => work(), listServers: async () => [],
    findServer: async () => null, findOwnedService: async () => null, reserveConnectionProfile: async () => true,
  };
  assert.throws(() => withFreeServerRegistry(delegate), /FreeServerRegistry store is required/);
});
