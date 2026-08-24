export function createTestPurchaseVerifier({ approvedTokens }) {
  const approvals = new Map(Object.entries(approvedTokens));
  return {
    kind: 'test-only',
    async verifyPlayPurchase({ productId, purchaseToken }) {
      const approvedProduct = approvals.get(purchaseToken);
      return approvedProduct === productId
        ? { valid: true, externalTransactionId: `test:${purchaseToken}`, purchasedAt: new Date().toISOString() }
        : { valid: false, reason: 'not_approved' };
    },
  };
}
