import { FIXTURES } from '../repository.js';

export function createTestAuthSessionAdapter() {
  let telegramLinked = false;
  let displayName = null;

  return {
    kind: 'test-only',
    jwks() { return { keys: [] }; },
    async guest({ deviceId }) {
      return {
        user_id: FIXTURES.users.primary,
        device_id: deviceId,
        access_token: 'test-access-token-not-for-production',
        access_token_expires_at: new Date(Date.now() + 900_000).toISOString(),
        refresh_token: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        refresh_token_expires_at: new Date(Date.now() + 86_400_000).toISOString(),
        token_type: 'Bearer',
      };
    },
    async beginTelegram() {
      return {
        authorization_url: 'https://oauth.telegram.org/auth?test=1',
        state: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        expires_at: new Date(Date.now() + 600_000).toISOString(),
      };
    },
    async linkTelegram({ deviceId, displayName: linkedDisplayName, username }) {
      telegramLinked = true;
      displayName = linkedDisplayName ?? username ?? 'Telegram User';
      return {
        user_id: FIXTURES.users.primary,
        device_id: deviceId,
        access_token: 'test-telegram-linked-access-token',
        access_token_expires_at: new Date(Date.now() + 900_000).toISOString(),
        refresh_token: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
        refresh_token_expires_at: new Date(Date.now() + 86_400_000).toISOString(),
        token_type: 'Bearer',
      };
    },
    async currentUser(principal) {
      return {
        id: principal.userId,
        status: 'active',
        display_name: displayName,
        locale: 'fa-IR',
        telegram_linked: telegramLinked,
        telegram_username: null,
      };
    },
    async refresh({ deviceId }) {
      return this.guest({ deviceId });
    },
    async logout() {
      telegramLinked = false;
      displayName = null;
      return { logged_out: true };
    },
    async close() {},
  };
}
