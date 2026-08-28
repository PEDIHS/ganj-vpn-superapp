import { createApplication } from './application.js';
import { createTelegramBotApprovalAdapter } from './adapters/telegram-bot-approval.js';
import { createHttpServer } from './http.js';
import { createRuntime } from './runtime.js';
import { createTelegramBotApprovalApplication } from './telegram-bot-routes.js';

const runtime = await createRuntime(process.env);
const baseApplication = createApplication(runtime);
const adapterMode = process.env.CONTROL_API_ADAPTER_MODE ?? 'production';
const telegramBotApproval = adapterMode === 'production'
  ? await createTelegramBotApprovalAdapter({ environment: process.env })
  : null;
const application = telegramBotApproval
  ? createTelegramBotApprovalApplication({
    baseApplication,
    auth: runtime.auth,
    telegramBotApproval,
  })
  : baseApplication;
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
  });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(async (error) => {
      await Promise.allSettled([
        runtime.close?.(),
        telegramBotApproval?.close?.(),
      ]);
      process.exitCode = error ? 1 : 0;
    });
  });
}
