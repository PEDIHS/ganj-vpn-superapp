import { createApplication } from './application.js';
import { createHttpServer } from './http.js';
import { createRuntime } from './runtime.js';

const runtime = await createRuntime(process.env);
const application = createApplication(runtime);
const port = Number(process.env.PORT ?? 8080);
const host = process.env.HOST ?? '127.0.0.1';
const server = createHttpServer(application);

server.listen(port, host, () => {
  console.info({ event: 'control_api_started', host, port, adapter_mode: process.env.CONTROL_API_ADAPTER_MODE ?? 'production' });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(async (error) => {
      await runtime.close?.();
      process.exitCode = error ? 1 : 0;
    });
  });
}
