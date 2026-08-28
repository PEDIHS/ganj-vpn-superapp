import assert from 'node:assert/strict';
import test from 'node:test';
import { ControlApiUpstreamBindingSink, LegacyProjectionPipeline, normalizeLegacyUpstreamBinding } from '../src/legacy-upstream-binding.js';

const ITEM = Object.freeze({
  source_key: 'ganj-bot-primary',
  external_service_id: 'legacy-service-1',
  upstream_binding: {
    provider_type: 'pasarguard',
    external_service_username: 'ganj_10001',
    connector_ref: 'pg-main',
  },
});

test('legacy upstream binding accepts locator metadata only', () => {
  assert.deepEqual(normalizeLegacyUpstreamBinding(ITEM.upstream_binding), {
    providerType: 'pasarguard', serviceUsername: 'ganj_10001', connectorRef: 'pg-main',
  });
  assert.throws(() => normalizeLegacyUpstreamBinding({ ...ITEM.upstream_binding, subscription_url: 'https://x' }), /unsupported/);
  assert.throws(() => normalizeLegacyUpstreamBinding({ ...ITEM.upstream_binding, provider_type: 'marzban' }), /provider/);
  assert.throws(() => normalizeLegacyUpstreamBinding({ ...ITEM.upstream_binding, external_service_username: 'bad/../name' }), /username/);
});

test('pipeline binds PasarGuard only after entitlement reconciliation resolves a service', async () => {
  const calls = [];
  const pipeline = new LegacyProjectionPipeline({
    reconciler: { async reconcile() { return { outcome: 'applied', replay: false, serviceId: '40000000-0000-4000-8000-000000000001' }; } },
    bindingSink: { async bind(value) { calls.push(value); } },
  });
  const result = await pipeline.reconcile(ITEM);
  assert.equal(result.upstreamBound, true);
  assert.deepEqual(calls, [{
    serviceId: '40000000-0000-4000-8000-000000000001',
    sourceKey: 'ganj-bot-primary',
    externalServiceId: 'legacy-service-1',
    providerType: 'pasarguard',
    serviceUsername: 'ganj_10001',
    connectorRef: 'pg-main',
  }]);
});

test('pipeline does not publish binding for ownership conflicts or missing binding metadata', async () => {
  let calls = 0;
  const sink = { async bind() { calls += 1; } };
  const conflict = new LegacyProjectionPipeline({
    reconciler: { async reconcile() { return { outcome: 'conflict', replay: false, serviceId: null }; } }, bindingSink: sink,
  });
  await conflict.reconcile(ITEM);
  const noBinding = new LegacyProjectionPipeline({
    reconciler: { async reconcile() { return { outcome: 'applied', replay: false, serviceId: '40000000-0000-4000-8000-000000000001' }; } }, bindingSink: sink,
  });
  await noBinding.reconcile({ source_key: 'ganj-bot-primary', external_service_id: 'legacy-service-2' });
  assert.equal(calls, 0);
});

test('Control API binding sink sends only the internal safe locator contract', async () => {
  const token = 'b'.repeat(48);
  let sent;
  const sink = new ControlApiUpstreamBindingSink({
    endpoint: 'https://control.example/v1/internal/bot/service-upstream',
    bearerToken: token,
    fetchImpl: async (url, options) => {
      sent = { url: String(url), options, body: JSON.parse(options.body) };
      return new Response('{}', { status: 200 });
    },
  });
  await sink.bind({
    serviceId: '40000000-0000-4000-8000-000000000001',
    sourceKey: 'ganj-bot-primary', externalServiceId: 'legacy-service-1',
    providerType: 'pasarguard', serviceUsername: 'ganj_10001', connectorRef: 'pg-main',
  });
  assert.equal(sent.options.redirect, 'error');
  assert.equal(sent.options.headers.authorization, `Bearer ${token}`);
  assert.deepEqual(sent.body, {
    service_id: '40000000-0000-4000-8000-000000000001',
    source_key: 'ganj-bot-primary',
    external_service_id: 'legacy-service-1',
    external_service_username: 'ganj_10001',
    connector_ref: 'pg-main',
  });
  const serialized = JSON.stringify(sent.body);
  for (const forbidden of ['subscription_url', 'credential', 'password', 'vless://']) assert.equal(serialized.includes(forbidden), false);
  assert.equal(sink.toString().includes(token), false);
});

test('Control API binding sink fails closed on unsafe endpoint and retryable upstream failure', async () => {
  const token = 'b'.repeat(48);
  assert.throws(() => new ControlApiUpstreamBindingSink({
    endpoint: 'http://control.example/v1/internal/bot/service-upstream', bearerToken: token,
  }), /HTTPS/);
  const sink = new ControlApiUpstreamBindingSink({
    endpoint: 'https://control.example/v1/internal/bot/service-upstream', bearerToken: token,
    fetchImpl: async () => new Response('{}', { status: 503 }),
  });
  await assert.rejects(sink.bind({
    serviceId: '40000000-0000-4000-8000-000000000001', sourceKey: 'ganj-bot-primary',
    externalServiceId: 'legacy-service-1', providerType: 'pasarguard',
    serviceUsername: 'ganj_10001', connectorRef: 'pg-main',
  }), (error) => error.status === 503 && error.retryable === true);
});
