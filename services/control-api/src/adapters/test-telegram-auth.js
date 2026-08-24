import { randomUUID } from 'node:crypto';
import { ApiError } from '../errors.js';
import { FIXTURES } from '../repository.js';

export function createTestTelegramAuthAdapter({ acceptedCode = 'test-telegram-code', acceptedState = 's'.repeat(32) } = {}) {
  const consumed = new Set();
  return {
    kind: 'test-only',
    async exchangeAuthorizationCode({ code, state, codeVerifier }) {
      if (code !== acceptedCode || state !== acceptedState || codeVerifier.length < 43 || consumed.has(state)) {
        throw new ApiError(400, 'invalid_login_state', 'Test Telegram exchange was rejected.');
      }
      consumed.add(state);
      return {
        user: {
          id: FIXTURES.users.primary,
          status: 'active',
          display_name: 'Test Telegram User',
          locale: 'fa-IR',
          telegram_linked: true,
          telegram_username: 'test_user',
        },
        device_id: FIXTURES.devices.primary,
        tokens: {
          access_token: `test-only-${randomUUID()}`,
          access_expires_in: 600,
          refresh_token: `test-only-${randomUUID()}-${randomUUID()}`,
          refresh_expires_in: 2_592_000,
        },
      };
    },
  };
}
