import assert from 'node:assert/strict';
import test from 'node:test';
import { FreeAccessRepository } from '../src/free-access.js';
import { FIXTURES, InMemoryRepository, createSeed } from '../src/repository.js';

const NOW = new Date('2026-08-28T05:45:00.000Z');

function freshUserSeed() {
  const seed = createSeed(NOW);
  seed.services = seed.services.filter((service) => service.userId !== FIXTURES.users.secondary);
  return seed;
}

test('silent free access creates exactly one managed free service for a user with none', async () => {
  const repository = new FreeAccessRepository(new InMemoryRepository(freshUserSeed()), { clock: () => new Date(NOW) });

  const first = await repository.listServices(FIXTURES.users.secondary);
  const second = await repository.listServices(FIXTURES.users.secondary);

  const free = first.filter((service) => service.tier === 'free');
  assert.equal(free.length, 1);
  assert.equal(free[0].status, 'active');
  assert.equal(free[0].device_limit, 1);
  assert.deepEqual(free[0].allowed_protocols, ['vless']);
  assert.deepEqual(free[0].deviceIds, []);
  assert.equal(second.filter((service) => service.tier === 'free').length, 1);
  assert.equal(second.find((service) => service.tier === 'free').id, free[0].id);
});

test('existing free allowance is never silently reset or duplicated', async () => {
  const seed = createSeed(NOW);
  const existing = seed.services.find((service) => service.id === FIXTURES.services.exhausted);
  assert.ok(existing);
  const repository = new FreeAccessRepository(new InMemoryRepository(seed), { clock: () => new Date(NOW) });

  const services = await repository.listServices(FIXTURES.users.primary);
  const free = services.filter((service) => service.tier === 'free');

  assert.equal(free.length, 1);
  assert.equal(free[0].id, FIXTURES.services.exhausted);
  assert.equal(free[0].traffic_used_bytes, free[0].traffic_limit_bytes);
});

test('free access fails closed when no active direct free plan is configured', async () => {
  const seed = freshUserSeed();
  seed.plans = seed.plans.filter((plan) => plan.tier !== 'free');
  const repository = new FreeAccessRepository(new InMemoryRepository(seed), { clock: () => new Date(NOW) });

  await assert.rejects(
    () => repository.listServices(FIXTURES.users.secondary),
    (error) => error?.code === 'free_plan_unavailable',
  );
});

test('free wrapper delegates unrelated repository methods unchanged', async () => {
  const delegate = new InMemoryRepository(createSeed(NOW));
  const repository = new FreeAccessRepository(delegate, { clock: () => new Date(NOW) });

  assert.equal(repository.kind, 'test-only');
  const servers = await repository.listServers();
  assert.equal(servers.length, 4);
  assert.equal(repository.findPlan(FIXTURES.plans.premium).id, FIXTURES.plans.premium);
});
