import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)));
const port = Number(process.env.ADMIN_CONSOLE_PORT ?? 4173);
const host = process.env.ADMIN_CONSOLE_HOST ?? '127.0.0.1';
const types = new Map([['.html','text/html; charset=utf-8'],['.css','text/css; charset=utf-8'],['.js','text/javascript; charset=utf-8'],['.json','application/json; charset=utf-8']]);

function safeFile(pathname) {
  const requested = pathname === '/' ? 'index.html' : decodeURIComponent(pathname.slice(1));
  if (requested.includes('\0')) return null;
  const file = resolve(root, requested);
  const rel = relative(root, file);
  if (!rel || rel === '.') return null;
  if (rel.startsWith('..') || rel.includes(`..${process.platform === 'win32' ? '\\' : '/'}`)) return null;
  return file;
}

const server = createServer((request, response) => {
  const url = new URL(request.url, `http://${request.headers.host ?? 'localhost'}`);
  let file = null;
  try {
    file = safeFile(url.pathname);
  } catch {
    file = null;
  }
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('Pragma', 'no-cache');
  response.setHeader('Referrer-Policy', 'no-referrer');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('X-Frame-Options', 'DENY');
  response.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  response.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  response.setHeader('Cross-Origin-Resource-Policy', 'same-origin');
  response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' https:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  if (!file || !existsSync(file) || !statSync(file).isFile()) {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Not found');
    return;
  }
  response.writeHead(200, { 'Content-Type': types.get(extname(file)) ?? 'application/octet-stream' });
  createReadStream(file).pipe(response);
});

server.listen(port, host, () => console.info({ event: 'ganj_admin_console_started', host, port }));
