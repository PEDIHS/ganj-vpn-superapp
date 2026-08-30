import { FIXTURES } from '../repository.js';

export function createTestAuthSessionAdapter() {
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
    async linkTelegram({ deviceId }) {
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
    async refresh({ deviceId }) {
      return this.guest({ deviceId });
    },
    async logout() { return { logged_out: true }; },
    async close() {},
  };
}
