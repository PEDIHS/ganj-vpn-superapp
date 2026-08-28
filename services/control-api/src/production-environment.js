import { readFile, stat } from 'node:fs/promises';

const FILE_BINDINGS = Object.freeze([
  ['AUTH_SESSION_SIGNING_PRIVATE_JWK_FILE', 'AUTH_SESSION_SIGNING_PRIVATE_JWK'],
  ['TELEGRAM_OIDC_CLIENT_SECRET_FILE', 'TELEGRAM_OIDC_CLIENT_SECRET'],
  ['TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN_FILE', 'TELEGRAM_BOT_APPROVAL_INTERNAL_TOKEN'],
  ['TELEGRAM_BOT_APPROVAL_CODE_KEY_FILE', 'TELEGRAM_BOT_APPROVAL_CODE_KEY'],
  ['PASARGUARD_CONNECTORS_JSON_FILE', 'PASARGUARD_CONNECTORS_JSON'],
  ['GANJ_BOT_SYNC_INTERNAL_TOKEN_FILE', 'GANJ_BOT_SYNC_INTERNAL_TOKEN'],
]);

async function readSecretFile(path, name) {
  const info = await stat(path);
  if (!info.isFile()) throw new Error(`${name} must reference a regular file.`);
  if (info.size < 1 || info.size > 64 * 1024) throw new Error(`${name} secret file size is invalid.`);
  if ((info.mode & 0o077) !== 0) throw new Error(`${name} secret file must not grant group/world permissions.`);
  const value = (await readFile(path, 'utf8')).trim();
  if (!value) throw new Error(`${name} secret file is empty.`);
  return value;
}

/**
 * Resolve only explicitly approved secret-file bindings. The returned object is a shallow copy and
 * is never logged. Direct environment values remain supported for managed secret-injection systems,
 * but a process must not set both the direct value and its *_FILE counterpart.
 */
export async function loadProductionEnvironment(source = process.env) {
  const environment = { ...source };
  for (const [fileName, valueName] of FILE_BINDINGS) {
    const file = source[fileName];
    const direct = source[valueName];
    if (file && direct) throw new Error(`${valueName} and ${fileName} cannot both be configured.`);
    if (file) environment[valueName] = await readSecretFile(file, fileName);
    delete environment[fileName];
  }
  return environment;
}
