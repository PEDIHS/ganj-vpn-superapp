import { ApiError } from '../errors.js';

function unavailable(code, message) {
  throw new ApiError(503, code, message, { retryable: true });
}

export function createPurchaseVerifier() {
  return {
    kind: 'disabled-fail-closed-v1',
    async verifyPlayPurchase() { return unavailable('play_billing_unavailable', 'Google Play billing is not configured yet.'); },
    async getPlaySubscriptionState() { return unavailable('play_billing_unavailable', 'Google Play billing is not configured yet.'); },
    async acknowledgePlayPurchase() { return unavailable('play_billing_unavailable', 'Google Play billing is not configured yet.'); },
    async cancelPlaySubscription() { return unavailable('play_billing_unavailable', 'Google Play billing is not configured yet.'); },
    async revokePlaySubscription() { return unavailable('play_billing_unavailable', 'Google Play billing is not configured yet.'); },
    async close() {},
  };
}

export function createPlayNotificationsAdapter() {
  return {
    kind: 'disabled-fail-closed-v1',
    async verifyAndDecode() { return unavailable('play_rtdn_unavailable', 'Google Play notifications are not configured yet.'); },
    async close() {},
  };
}

export function createTelegramAuthAdapter() {
  return {
    kind: 'disabled-fail-closed-v1',
    async exchangeAuthorizationCode() { return unavailable('telegram_auth_unavailable', 'Telegram account linking is not configured yet.'); },
    async close() {},
  };
}
