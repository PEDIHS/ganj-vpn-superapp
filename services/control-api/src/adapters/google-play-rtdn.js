import { createHash } from 'node:crypto';
import { ApiError } from '../errors.js';
import { JwksJwtVerifier } from '../security/jwt.js';

export class GooglePlayRtdnAdapter {
  constructor({ jwtVerifier, expectedServiceAccount, expectedSubscription, packageName }) {
    if (typeof jwtVerifier?.verify !== 'function' || !expectedServiceAccount || !expectedSubscription || !packageName) {
      throw new Error('Google Play RTDN dependencies are incomplete.');
    }
    this.kind = 'google-pubsub-oidc-rtdn-v1';
    this.jwtVerifier = jwtVerifier;
    this.expectedServiceAccount = expectedServiceAccount;
    this.expectedSubscription = expectedSubscription;
    this.packageName = packageName;
  }

  async verifyAndDecode({ request, body }) {
    const authorization = request.headers.get('authorization');
    if (!authorization?.startsWith('Bearer ')) throw new ApiError(401, 'invalid_callback_signature', 'Signed callback token is required.');
    let claims;
    try { claims = await this.jwtVerifier.verify(authorization.slice(7)); }
    catch { throw new ApiError(401, 'invalid_callback_signature', 'Signed callback token is invalid.'); }
    if (claims.email !== this.expectedServiceAccount || claims.email_verified !== true) {
      throw new ApiError(401, 'invalid_callback_identity', 'Callback service account is invalid.');
    }
    if (body.subscription !== this.expectedSubscription || !body.message || typeof body.message !== 'object') {
      throw new ApiError(400, 'invalid_rtdn_envelope', 'Pub/Sub push envelope is invalid.');
    }
    const { message } = body;
    if (typeof message.messageId !== 'string' || typeof message.data !== 'string') {
      throw new ApiError(400, 'invalid_rtdn_envelope', 'Pub/Sub message ID or data is missing.');
    }
    let notification;
    try { notification = JSON.parse(Buffer.from(message.data, 'base64').toString('utf8')); }
    catch { throw new ApiError(400, 'invalid_rtdn_payload', 'RTDN payload is invalid.'); }
    const subscription = notification.subscriptionNotification;
    if (notification.packageName !== this.packageName || !subscription
      || typeof subscription.purchaseToken !== 'string' || typeof subscription.subscriptionId !== 'string') {
      throw new ApiError(400, 'invalid_rtdn_payload', 'RTDN subscription payload is invalid.');
    }
    return {
      eventId: message.messageId,
      eventType: `subscription:${subscription.notificationType}`,
      purchaseToken: subscription.purchaseToken,
      productId: subscription.subscriptionId,
      payloadDigest: createHash('sha256').update(JSON.stringify(body)).digest('hex'),
      receivedAt: message.publishTime ? new Date(message.publishTime) : new Date(),
    };
  }
}

export function createPlayNotificationsAdapter({ environment = process.env } = {}) {
  for (const required of ['PLAY_RTDN_AUDIENCE', 'PLAY_RTDN_SERVICE_ACCOUNT', 'PLAY_RTDN_SUBSCRIPTION']) {
    if (!environment[required]) throw new Error(`${required} is required.`);
  }
  return new GooglePlayRtdnAdapter({
    expectedServiceAccount: environment.PLAY_RTDN_SERVICE_ACCOUNT,
    expectedSubscription: environment.PLAY_RTDN_SUBSCRIPTION,
    packageName: environment.GOOGLE_PLAY_PACKAGE_NAME ?? 'com.ganj.vpn',
    jwtVerifier: new JwksJwtVerifier({
      jwksUri: environment.PLAY_RTDN_JWKS_URI ?? 'https://www.googleapis.com/oauth2/v3/certs',
      issuer: ['https://accounts.google.com', 'accounts.google.com'],
      audience: environment.PLAY_RTDN_AUDIENCE,
      allowedAlgorithms: ['RS256'],
    }),
  });
}
