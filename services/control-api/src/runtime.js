import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { createTestAuthAdapter } from './adapters/test-auth.js';
import { createTestPurchaseVerifier } from './adapters/test-purchase-verifier.js';
import { createTestTelegramAuthAdapter } from './adapters/test-telegram-auth.js';
import { FIXTURES, InMemoryRepository, createSeed } from './repository.js';

async function importFactory(specifier, exportName, environment) {
  if (!specifier) throw new Error(`${exportName} adapter module is required.`);
  const moduleSpecifier = specifier.startsWith('.') || specifier.startsWith('/')
    ? pathToFileURL(resolve(specifier)).href
    : specifier;
  const loaded = await import(moduleSpecifier);
  if (typeof loaded[exportName] !== 'function') {
    throw new Error(`${specifier} must export ${exportName}().`);
  }
  return loaded[exportName]({ environment });
}

export async function createRuntime(environment = process.env) {
  const mode = environment.CONTROL_API_ADAPTER_MODE ?? 'production';
  if (mode === 'test') {
    if (environment.NODE_ENV === 'production') {
      throw new Error('Test adapters are forbidden when NODE_ENV=production.');
    }
    const primarySecret = environment.CONTROL_API_TEST_DEVICE_SECRET ?? 'local-device-secret-change-me';
    const secondSecret = environment.CONTROL_API_TEST_SECOND_DEVICE_SECRET ?? 'local-second-device-secret-change-me';
    const purchaseToken = environment.CONTROL_API_TEST_PURCHASE_TOKEN ?? 'aaaaaaaaaaaaaaaa';
    return {
      repository: new InMemoryRepository(createSeed()),
      auth: createTestAuthAdapter({
        deviceSecrets: {
          [FIXTURES.devices.primary]: primarySecret,
          [FIXTURES.devices.second]: secondSecret,
          [FIXTURES.devices.secondary]: 'secondary-device-secret-change-me',
        },
      }),
      purchaseVerifier: createTestPurchaseVerifier({
        approvedTokens: { [purchaseToken]: 'ganj.premium.30d' },
      }),
      telegramAuth: createTestTelegramAuthAdapter(),
      playNotifications: { kind: 'test-only', async verifyAndDecode() { throw new Error('No test RTDN configured.'); } },
      async close() {},
    };
  }
  if (mode !== 'production') throw new Error(`Unsupported CONTROL_API_ADAPTER_MODE: ${mode}`);
  const [repository, auth, purchaseVerifier, telegramAuth, playNotifications] = await Promise.all([
    importFactory(environment.CONTROL_API_DATA_ADAPTER_MODULE, 'createDataAdapter', environment),
    importFactory(environment.CONTROL_API_AUTH_ADAPTER_MODULE, 'createAuthAdapter', environment),
    importFactory(environment.CONTROL_API_PURCHASE_ADAPTER_MODULE, 'createPurchaseVerifier', environment),
    importFactory(environment.CONTROL_API_TELEGRAM_AUTH_ADAPTER_MODULE, 'createTelegramAuthAdapter', environment),
    importFactory(environment.CONTROL_API_PLAY_NOTIFICATIONS_ADAPTER_MODULE, 'createPlayNotificationsAdapter', environment),
  ]);
  if (repository?.kind === 'test-only' || auth?.kind === 'test-only' || purchaseVerifier?.kind === 'test-only'
    || telegramAuth?.kind === 'test-only' || playNotifications?.kind === 'test-only') {
    throw new Error('Test adapters cannot be loaded in production mode.');
  }
  return {
    repository,
    auth,
    purchaseVerifier,
    telegramAuth,
    playNotifications,
    async close() {
      await Promise.allSettled(
        [repository, auth, purchaseVerifier, telegramAuth, playNotifications]
          .map((resource) => resource?.close?.()),
      );
    },
  };
}

export { FIXTURES };
