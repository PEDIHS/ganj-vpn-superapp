import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { DEFAULT_FREE_POLICY, FreePolicyRepository } from '../src/free-policy.js';
import { withFreeAccess } from '../src/free-access.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const START = new Date('2026-08-28T10:40:00.000Z');

function repositoryWith(policy = {}, mutateSeed = () => {}) {
  const seed = createSeed(START);
  const exhausted = seed.services.find((service) => service.id === FIXTURES.services.exhausted);
  exhausted.traffic_used_bytes = 0;
  mutateSeed(seed);
  let now = new Date(START);
  const base = new InMemoryRepository(seed);
  const repository = new FreePolicyRepository(withFreeAccess(base, { clock: () => new Date(now) }), {
    clock: () => new Date(now),
    policy: { ...DEFAULT_FREE_POLICY, ...policy },
  });
  return {
    repository,
    base,
    setNow(value) { now = new Date(value); },
  };
}

function grant({ userId, deviceId, serviceId, serverId = FIXTURES.servers.free, expiresAt = '2026-08-28T10:45:00.000Z' }) {
  return {
    profileId: randomUUID(), userId, deviceId, serviceId, serverId,
    clientNonceDigest: randomUUID().replaceAll('-', ''), expiresAt,
  };
}

async function secondaryFreeService(repository) {
  return (await repository.listServices(FIXTURES.users.secondary)).find((service) => service.tier === 'free');
}

test('default policy keeps managed Free servers visible while preserving paid catalog', async () => {
  const { repository } = repositoryWith();
  await secondaryFreeService(repository);
  const servers = await repository.listServers();
  assert.ok(servers.some((server) => server.id === FIXTURES.servers.free));
  assert.ok(servers.some((server) => server.id === FIXTURES.servers.premium));
});

test('disabled, maintenance and emergency policy hide only Free servers and reject stale Free selection', async () => {
  for (const [patch, code] of [
    [{ enabled: false }, 'free_access_disabled'],
    [{ maintenance: true }, 'free_access_maintenance'],
    [{ emergencyDisabled: true }, 'free_access_emergency_disabled'],
  ]) {
    const { repository } = repositoryWith(patch);
    const servers = await repository.listServers();
    assert.equal(servers.some((server) => server.tier === 'free'), false);
    assert.equal(servers.some((server) => server.tier === 'premium'), true);
    await assert.rejects(() => repository.findServer(FIXTURES.servers.free), (error) => error?.code === code);
    assert.equal((await repository.findServer(FIXTURES.servers.premium)).id, FIXTURES.servers.premium);
  }
});

test('Free issuance rate is enforced per user and device inside the repository transaction', async () => {
  const { repository } = repositoryWith({ maxIssuesPerUser: 1, maxIssuesPerDevice: 1, maxActiveGrantsPerUser: 10, maxActiveGrantsPerDevice: 10 });
  const service = await secondaryFreeService(repository);
  const first = grant({ userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id, expiresAt: '2026-08-28T10:40:01.000Z' });
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(first)), true);
  await assert.rejects(
    () => repository.transaction(() => repository.reserveConnectionProfile(grant({
      userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id,
    }))),
    (error) => error?.code === 'free_rate_limit_exceeded' && ['user', 'device'].includes(error?.details?.dimension),
  );
});

test('expired grants leave active capacity and rate window expiry permits a later issuance', async () => {
  const { repository, setNow } = repositoryWith({
    issueWindowSeconds: 60,
    maxIssuesPerUser: 1,
    maxIssuesPerDevice: 1,
    maxActiveGrantsPerUser: 1,
    maxActiveGrantsPerDevice: 1,
  });
  const service = await secondaryFreeService(repository);
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id,
    expiresAt: '2026-08-28T10:40:10.000Z',
  }))), true);
  setNow('2026-08-28T10:42:00.000Z');
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id,
    expiresAt: '2026-08-28T10:47:00.000Z',
  }))), true);
});

test('active grant cap is enforced independently for device and user admission', async () => {
  const deviceCase = repositoryWith({ maxActiveGrantsPerDevice: 1, maxActiveGrantsPerUser: 10, maxIssuesPerUser: 10, maxIssuesPerDevice: 10 });
  const deviceService = await secondaryFreeService(deviceCase.repository);
  assert.equal(await deviceCase.repository.transaction(() => deviceCase.repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: deviceService.id,
  }))), true);
  await assert.rejects(
    () => deviceCase.repository.transaction(() => deviceCase.repository.reserveConnectionProfile(grant({
      userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: deviceService.id,
    }))),
    (error) => error?.code === 'free_profile_capacity_reached' && error?.details?.dimension === 'device',
  );

  const userCase = repositoryWith({ maxActiveGrantsPerDevice: 10, maxActiveGrantsPerUser: 1, maxIssuesPerUser: 10, maxIssuesPerDevice: 10 });
  const userService = await secondaryFreeService(userCase.repository);
  assert.equal(await userCase.repository.transaction(() => userCase.repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: userService.id,
  }))), true);
  await assert.rejects(
    () => userCase.repository.transaction(() => userCase.repository.reserveConnectionProfile(grant({
      userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: userService.id,
    }))),
    (error) => error?.code === 'free_profile_capacity_reached' && error?.details?.dimension === 'user',
  );
});

test('Free server admission cap is global across users without blocking paid profile grants', async () => {
  const { repository } = repositoryWith({
    maxActiveGrantsPerServer: 1,
    maxActiveGrantsPerUser: 10,
    maxActiveGrantsPerDevice: 10,
    maxIssuesPerUser: 10,
    maxIssuesPerDevice: 10,
  });
  const secondary = await secondaryFreeService(repository);
  const primary = (await repository.listServices(FIXTURES.users.primary)).find((service) => service.tier === 'free');
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: secondary.id,
  }))), true);
  await assert.rejects(
    () => repository.transaction(() => repository.reserveConnectionProfile(grant({
      userId: FIXTURES.users.primary, deviceId: FIXTURES.devices.primary, serviceId: primary.id,
    }))),
    (error) => error?.code === 'free_server_capacity_reached' && error?.details?.dimension === 'server',
  );

  await repository.updateFreePolicy({ emergencyDisabled: true });
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.primary, deviceId: FIXTURES.devices.primary,
    serviceId: FIXTURES.services.premium, serverId: FIXTURES.servers.premium,
  }))), true);
});

test('consumed active grant no longer counts toward active admission capacity', async () => {
  const { repository, base } = repositoryWith({ maxActiveGrantsPerDevice: 1, maxActiveGrantsPerUser: 1, maxIssuesPerUser: 10, maxIssuesPerDevice: 10 });
  const service = await secondaryFreeService(repository);
  const first = grant({ userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id });
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(first)), true);
  assert.equal(base.consumeConnectionProfile({ profileId: first.profileId, deviceId: first.deviceId, now: START }), true);
  assert.equal(await repository.transaction(() => repository.reserveConnectionProfile(grant({
    userId: FIXTURES.users.secondary, deviceId: FIXTURES.devices.secondary, serviceId: service.id,
  }))), true);
});

test('policy updates are validated and metadata is retained for management surfaces', async () => {
  const { repository } = repositoryWith();
  const actorId = FIXTURES.users.primary;
  const updated = await repository.updateFreePolicy({ maintenance: true, maxIssuesPerDevice: 7 }, { actorId });
  assert.equal(updated.maintenance, true);
  assert.equal(updated.maxIssuesPerDevice, 7);
  assert.equal(updated.updatedBy, actorId);
  await assert.rejects(() => repository.updateFreePolicy({ maxIssuesPerDevice: 0 }), (error) => error?.code === 'invalid_free_policy');
  await assert.rejects(() => repository.updateFreePolicy({ raw_config: 'forbidden' }), (error) => error?.code === 'invalid_free_policy');
});

test('unknown production data adapters cannot silently bypass Free policy', () => {
  const delegate = {
    kind: 'custom-production', transaction: async (work) => work(), listServers: async () => [],
    findServer: async () => null, findOwnedService: async () => null, reserveConnectionProfile: async () => true,
  };
  assert.throws(() => new FreePolicyRepository(delegate), /FreeAccessPolicy store is required/);
});
