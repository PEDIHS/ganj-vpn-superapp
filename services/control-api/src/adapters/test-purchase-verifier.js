export function createTestPurchaseVerifier({ approvedTokens }) {
  const approvals = new Map(Object.entries(approvedTokens));
  return {
    kind: 'test-only',
    async verifyPlayPurchase({ productId, purchaseToken }) {
      const approvedProduct = approvals.get(purchaseToken);
      return approvedProduct === productId
        ? {
          valid: true,
          entitled: true,
          state: 'SUBSCRIPTION_STATE_ACTIVE',
          productId,
          expiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
          externalTransactionId: `test:${purchaseToken}`,
          requiresAcknowledgement: false,
        }
        : { valid: false, reason: 'not_approved' };
    },
    async getPlaySubscriptionState({ productId, purchaseToken }) {
      return this.verifyPlayPurchase({ productId, purchaseToken });
    },
    async acknowledgePlayPurchase() {},
    async cancelPlaySubscription() {},
    async revokePlaySubscription() {},
  };
}
