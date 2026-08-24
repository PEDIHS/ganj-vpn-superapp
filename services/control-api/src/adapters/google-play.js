import { createPrivateKey, createSign } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const ENTITLED_STATES = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  'SUBSCRIPTION_STATE_CANCELED',
]);
const NON_ENTITLED_STATES = new Set([
  'SUBSCRIPTION_STATE_PENDING',
  'SUBSCRIPTION_STATE_PAUSED',
  'SUBSCRIPTION_STATE_ON_HOLD',
  'SUBSCRIPTION_STATE_EXPIRED',
  'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED',
]);

function base64url(value) { return Buffer.from(JSON.stringify(value)).toString('base64url'); }

export class GoogleServiceAccountAccessTokenProvider {
  constructor({ serviceAccount, fetchImpl = fetch, clock = () => new Date() }) {
    if (!serviceAccount?.client_email || !serviceAccount?.private_key || !serviceAccount?.token_uri) {
      throw new Error('Google service account is incomplete.');
    }
    const endpoint = new URL(serviceAccount.token_uri);
    if (endpoint.protocol !== 'https:') throw new Error('Google OAuth token endpoint must use HTTPS.');
    this.serviceAccount = serviceAccount;
    this.endpoint = endpoint;
    this.privateKey = createPrivateKey(serviceAccount.private_key);
    this.fetchImpl = fetchImpl;
    this.clock = clock;
    this.cached = null;
  }

  async getAccessToken() {
    const now = Math.floor(this.clock().getTime() / 1000);
    if (this.cached && this.cached.expiresAt > now + 60) return this.cached.value;
    const header = base64url({ alg: 'RS256', typ: 'JWT', kid: this.serviceAccount.private_key_id });
    const claims = base64url({
      iss: this.serviceAccount.client_email,
      scope: 'https://www.googleapis.com/auth/androidpublisher',
      aud: this.endpoint.href,
      iat: now,
      exp: now + 3600,
    });
    const signer = createSign('RSA-SHA256');
    signer.update(`${header}.${claims}`);
    const assertion = `${header}.${claims}.${signer.sign(this.privateKey).toString('base64url')}`;
    const response = await this.fetchImpl(this.endpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
      redirect: 'error',
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new Error(`Google OAuth exchange failed with status ${response.status}.`);
    const document = await response.json();
    if (typeof document.access_token !== 'string' || !Number.isFinite(document.expires_in)) {
      throw new Error('Google OAuth response is invalid.');
    }
    this.cached = { value: document.access_token, expiresAt: now + Math.min(document.expires_in, 3600) };
    return this.cached.value;
  }
}

function latestExpiry(document) {
  const expiries = (document.lineItems ?? [])
    .map((item) => item.expiryTime)
    .filter((value) => typeof value === 'string' && Number.isFinite(Date.parse(value)))
    .sort();
  return expiries.at(-1) ?? null;
}

function productIds(document) {
  return new Set((document.lineItems ?? []).map((item) => item.productId).filter((value) => typeof value === 'string'));
}

export class GooglePlayPurchaseVerifier {
  constructor({ packageName, accessTokenProvider, fetchImpl = fetch, publisherBaseUrl = 'https://androidpublisher.googleapis.com', clock = () => new Date() }) {
    if (!packageName || typeof accessTokenProvider?.getAccessToken !== 'function') {
      throw new Error('Google Play verifier dependencies are incomplete.');
    }
    this.kind = 'google-android-publisher-v2';
    this.packageName = packageName;
    this.accessTokenProvider = accessTokenProvider;
    this.fetchImpl = fetchImpl;
    this.clock = clock;
    this.baseUrl = new URL(publisherBaseUrl);
    if (this.baseUrl.protocol !== 'https:') throw new Error('Android Publisher endpoint must use HTTPS.');
  }

  subscriptionUrl(purchaseToken, action = '') {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}${action}`;
    return new URL(path, this.baseUrl);
  }

  acknowledgementUrl(purchaseToken, productId) {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
    return new URL(path, this.baseUrl);
  }

  async publisherRequest(url, { method = 'GET', body } = {}) {
    const accessToken = await this.accessTokenProvider.getAccessToken();
    const response = await this.fetchImpl(url, {
      method,
      headers: {
        authorization: `Bearer ${accessToken}`,
        accept: 'application/json',
        ...(body ? { 'content-type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      redirect: 'error',
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new Error(`Android Publisher request failed with status ${response.status}.`);
    if (response.status === 204) return {};
    return response.json();
  }

  async getPlaySubscriptionState({ purchaseToken, productId }) {
    const document = await this.publisherRequest(this.subscriptionUrl(purchaseToken));
    const state = document.subscriptionState;
    const products = productIds(document);
    const expiresAt = latestExpiry(document);
    const packageMatches = !document.packageName || document.packageName === this.packageName;
    const productMatches = products.has(productId);
    const timeValid = expiresAt && Date.parse(expiresAt) > this.clock().getTime();
    const entitled = packageMatches && productMatches && ENTITLED_STATES.has(state) && timeValid;
    if (!ENTITLED_STATES.has(state) && !NON_ENTITLED_STATES.has(state)) {
      throw new Error('Android Publisher returned an unknown subscription state.');
    }
    return {
      valid: entitled,
      entitled,
      state,
      productId,
      productIds: [...products],
      expiresAt,
      externalTransactionId: document.latestOrderId ?? null,
      requiresAcknowledgement: entitled && document.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING',
      linkedPurchaseToken: document.linkedPurchaseToken ?? null,
    };
  }

  async verifyPlayPurchase({ packageName, productId, purchaseToken }) {
    if (packageName !== this.packageName) return { valid: false, reason: 'package_mismatch' };
    const state = await this.getPlaySubscriptionState({ purchaseToken, productId });
    if (!state.valid || !state.externalTransactionId) return { ...state, valid: false };
    return state;
  }

  async acknowledgePlayPurchase({ purchaseToken, productId }) {
    await this.publisherRequest(this.acknowledgementUrl(purchaseToken, productId), {
      method: 'POST',
      body: {},
    });
  }

  async cancelPlaySubscription({ purchaseToken, initiatedBy = 'developer' }) {
    if (!['developer', 'user'].includes(initiatedBy)) throw new Error('Unsupported Google Play cancellation initiator.');
    await this.publisherRequest(this.subscriptionUrl(purchaseToken, ':cancel'), {
      method: 'POST',
      body: {
        cancellationContext: initiatedBy === 'user'
          ? { userInitiatedCancellation: {} }
          : { developerInitiatedCancellation: {} },
      },
    });
  }

  async revokePlaySubscription({ purchaseToken, revocationType = 'FULL_REFUND' }) {
    const allowed = new Set(['FULL_REFUND', 'PRORATED_REFUND']);
    if (!allowed.has(revocationType)) throw new Error('Unsupported Google Play revocation type.');
    await this.publisherRequest(this.subscriptionUrl(purchaseToken, ':revoke'), {
      method: 'POST',
      body: { revocationContext: revocationType === 'PRORATED_REFUND' ? { proratedRefund: {} } : { fullRefund: {} } },
    });
  }
}

async function readServiceAccount(environment) {
  if (environment.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE) {
    return JSON.parse(await readFile(environment.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE, 'utf8'));
  }
  if (environment.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return JSON.parse(environment.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON);
  throw new Error('Google Play service account secret reference is required.');
}

export async function createPurchaseVerifier({ environment = process.env } = {}) {
  const packageName = environment.GOOGLE_PLAY_PACKAGE_NAME ?? 'com.ganj.vpn';
  const serviceAccount = await readServiceAccount(environment);
  return new GooglePlayPurchaseVerifier({
    packageName,
    accessTokenProvider: new GoogleServiceAccountAccessTokenProvider({ serviceAccount }),
  });
}
