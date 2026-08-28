import { createApplication } from './application.js';
import { createHttpServer } from './http.js';
import { loadProductionEnvironment } from './production-environment.js';
import { createRuntime } from './runtime.js';

const environment = process.env.NODE_ENV === 'production'
  ? await loadProductionEnvironment(process.env)
  : process.env;
const runtime = await createRuntime(environment);
const application = createApplication(runtime);
const port = Number(environment.PORT ?? 8080);
const host = environment.HOST ?? '127.0.0.1';
const server = createHttpServer(application);

server.listen(port, host, () => {
  console.info({ event: 'control_api_started', host, port, adapter_mode: environment.CONTROL_API_ADAPTER_MODE ?? 'production' });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    server.close(async (error) => {
      await runtime.close?.();
      process.exitCode = error ? 1 : 0;
    });
  });
}
