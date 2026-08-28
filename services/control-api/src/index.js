import { createApplication } from './application.js';
import { createTelegramBotApprovalAdapter } from './adapters/telegram-bot-approval.js';
import { createFreeAdminApplication } from './free-admin-routes.js';
import { createHttpServer } from './http.js';
import { createOperationsAdminApplication } from './operations-admin-routes.js';
import { createPasarGuardPaidApplication, createPasarGuardPaidRuntime } from './pasarguard-paid-routes.js';
import { createRuntime } from './runtime.js';
import { createSafeApplicationBoundary } from './safe-application-boundary.js';
import { createTelegramBotApprovalApplication } from './telegram-bot-routes.js';

const runtime = await createRuntime(process.env);
const baseApplication = createApplication(runtime);
const adapterMode = process.env.CONTROL_API_ADAPTER_MODE ?? 'production';
const [telegramBotApproval, paidRuntime] = adapterMode === 'production'
  ? await Promise.all([
    createTelegramBotApprovalAdapter({ environment: process.env }),
    createPasarGuardPaidRuntime({ environment: process.env }),
  ])
  : [null, null];

const paidApplication = paidRuntime
  ? createPasarGuardPaidApplication({
    baseApplication,
    repository: runtime.repository,
    auth: runtime.auth,
    bindingStore: paidRuntime.bindingStore,
    connectorRegistry: paidRuntime.connectorRegistry,
    liveAdapter: paidRuntime.liveAdapter,
    internalToken: paidRuntime.internalToken,
  })
  : baseApplication;

const telegramApplication = telegramBotApproval
  ? createTelegramBotApprovalApplication({
    baseApplication: paidApplication,
    auth: runtime.auth,
    telegramBotApproval,
  })
  : paidApplication;

const freeAdminApplication = createFreeAdminApplication({
  baseApplication: telegramApplication,
  repository: runtime.repository,
  auth: runtime.auth,
});
const routedApplication = createOperationsAdminApplication({
  baseApplication: freeAdminApplication,
  repository: runtime.repository,
  auth: runtime.auth,
  paidRuntime,
});
const application = createSafeApplicationBoundary(routedApplication);

const port = Number(process.env.PORT ?? 8080);
const host = process.env.HOST ?? '127.0.0.1';
const server = createHttpServer(application);

server.listen(port, host, () => {
  console.info({
    event: 'control_api_started',
    host,
    port,
    adapter_mode: adapterMode,
    telegram_login_primary: telegramBotApproval ? 'bot-approval' : 'test-adapter',
    paid_runtime: paidRuntime ? 'pasarguard-live' : 'test-adapter',
    free_admin: 'control-api',
    operations_admin: 'control-api',
  });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(async (error) => {
      await Promise.allSettled([
        runtime.close?.(),
        telegramBotApproval?.close?.(),
        paidRuntime?.close?.(),
      ]);
      process.exitCode = error ? 1 : 0;
    });
  });
}
