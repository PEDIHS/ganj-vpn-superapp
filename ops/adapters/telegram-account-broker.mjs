import { readFile } from 'node:fs/promises';

function required(environment, name) {
  const value = environment[name];
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

export async function createTelegramAccountBroker({ environment = process.env } = {}) {
  const endpoint = new URL(required(environment, 'TELEGRAM_ACCOUNT_BROKER_URL'));
  if (endpoint.protocol !== 'https:') throw new Error('Telegram account broker must use HTTPS.');
  const token = (await readFile(required(environment, 'TELEGRAM_ACCOUNT_BROKER_TOKEN_FILE'), 'utf8')).trim();
  if (!token) throw new Error('Telegram account broker credential is empty.');
  return {
    kind: 'remote-telegram-account-broker-v1',
    async linkAndIssueSession(input) {
      const response = await fetch(new URL('/v1/telegram/accounts:link-and-issue-session', endpoint), {
        method: 'POST',
        redirect: 'error',
        signal: AbortSignal.timeout(8_000),
        headers: { authorization: `Bearer ${token}`, accept: 'application/json', 'content-type': 'application/json' },
        body: JSON.stringify(input),
      });
      if (!response.ok) throw new Error(`Telegram account broker failed with status ${response.status}.`);
      const raw = await response.text();
      if (Buffer.byteLength(raw) > 65_536) throw new Error('Telegram account broker response is too large.');
      const value = JSON.parse(raw);
      if (!value || typeof value !== 'object' || typeof value.device_id !== 'string' || !value.tokens || typeof value.tokens !== 'object') {
        throw new Error('Telegram account broker response is invalid.');
      }
      return value;
    },
  };
}
