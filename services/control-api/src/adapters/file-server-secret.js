import { readFile, realpath, stat } from 'node:fs/promises';
import { isAbsolute, relative, resolve, sep } from 'node:path';

const REFERENCE = /^file:([A-Za-z0-9][A-Za-z0-9._/-]{2,200})$/;
const MAX_SECRET_BYTES = 16 * 1024;

function within(root, target) {
  const rel = relative(root, target);
  return rel !== '' && rel !== '..' && !rel.startsWith(`..${sep}`) && !isAbsolute(rel);
}

function validatePermissions(info) {
  // Secret files may be owner-readable/writable only. Group/world bits fail closed.
  if ((info.mode & 0o077) !== 0) {
    throw new Error('Server secret file permissions must not grant group/world access.');
  }
}

function validatePayload(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Server secret payload must be a JSON object.');
  const allowed = new Set(['endpoint', 'port', 'protocol', 'credential', 'transport', 'security', 'flow', 'shadowsocks_method']);
  if (Object.keys(value).some((key) => !allowed.has(key))) throw new Error('Server secret payload contains unsupported fields.');
  if (typeof value.endpoint !== 'string' || typeof value.credential !== 'string') {
    throw new Error('Server secret payload is incomplete.');
  }
  return structuredClone(value);
}

export async function createServerSecretResolver({ environment = process.env } = {}) {
  const configuredRoot = environment.CONTROL_API_SERVER_SECRET_DIR;
  if (!configuredRoot) throw new Error('CONTROL_API_SERVER_SECRET_DIR is required.');
  const root = await realpath(resolve(configuredRoot));
  const rootInfo = await stat(root);
  if (!rootInfo.isDirectory()) throw new Error('CONTROL_API_SERVER_SECRET_DIR must be a directory.');

  return {
    kind: 'file-secret-v1',
    async resolveServerConnection({ secretRef }) {
      const match = REFERENCE.exec(secretRef ?? '');
      if (!match) throw new Error('Server secret reference must use the file:<relative-path> scheme.');
      const requested = resolve(root, match[1]);
      if (!within(root, requested)) throw new Error('Server secret reference escaped the configured root.');
      const canonical = await realpath(requested);
      if (!within(root, canonical)) throw new Error('Server secret symlink escaped the configured root.');
      const info = await stat(canonical);
      if (!info.isFile()) throw new Error('Server secret reference does not resolve to a regular file.');
      if (info.size < 2 || info.size > MAX_SECRET_BYTES) throw new Error('Server secret file size is invalid.');
      validatePermissions(info);
      const raw = await readFile(canonical, { encoding: 'utf8' });
      let parsed;
      try {
        parsed = JSON.parse(raw);
      } catch {
        throw new Error('Server secret file is not valid JSON.');
      }
      return validatePayload(parsed);
    },
    async close() {},
  };
}
