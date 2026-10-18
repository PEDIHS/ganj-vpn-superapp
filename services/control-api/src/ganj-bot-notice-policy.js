/**
 * Pure, server-side notification decisions. Source must be authenticated bot
 * mutation data, never an Android client's own claim of usage or payment.
 * Nothing here changes a wallet, subscription or entitlement.
 */
const finite = (n) => typeof n === 'number' && Number.isFinite(n) && n >= 0;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function validId(value) {
  return typeof value === 'string' && UUID.test(value);
}
function cleanLabel(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= 70
    ? value.replace(/[\u0000-\u001f<>]/g, '').slice(0, 70) : 'اشتراک گنج';
}
function notice(key, kind, title, body, serviceId) {
  return { eventKey: key, kind, title, body, action: serviceId ? { type: 'open_subscription', id: serviceId } : { type: 'open_wallet' } };
}

/** Alert once per service billing cycle; caller persists the key under a unique constraint. */
export function subscriptionNotices({ serviceId, cycleId, label, quotaBytes, previousUsedBytes, usedBytes, previousExpiresAt, expiresAt, observedAt }) {
  if (!validId(serviceId) || !validId(cycleId)) throw new Error('invalid_service_cycle');
  const result = [];
  const base = 'subscription:' + serviceId + ':' + cycleId;
  const name = cleanLabel(label);
  const now = Date.parse(observedAt);
  if (!Number.isFinite(now)) throw new Error('invalid_observation_time');
  if (finite(quotaBytes) && quotaBytes > 0 && finite(previousUsedBytes) && finite(usedBytes) && usedBytes >= previousUsedBytes) {
    const threshold = quotaBytes * 0.80;
    if (previousUsedBytes < threshold && usedBytes >= threshold && usedBytes < quotaBytes) {
      result.push(notice(base + ':usage80', 'subscription_usage',
        '۸۰٪ حجم اشتراک مصرف شد', 'از حجم «' + name + '» بیش از ۸۰٪ استفاده شده است. برای جلوگیری از قطع سرویس، وضعیت را بررسی کنید.', serviceId));
    }
    if (previousUsedBytes < quotaBytes && usedBytes >= quotaBytes) {
      result.push(notice(base + ':used_all', 'subscription_expiry',
        'حجم اشتراک تمام شد', 'حجم «' + name + '» به پایان رسید. برای ادامه اتصال، سرویس را تمدید کنید.', serviceId));
    }
  }
  return result;
}

/** Absolute time alerts use prior/current observation time; no hourly scanner is created. */
export function expiryNotices({ serviceId, cycleId, label, expiresAt, previousObservedAt, observedAt }) {
  if (!validId(serviceId) || !validId(cycleId)) throw new Error('invalid_service_cycle');
  const previous = Date.parse(previousObservedAt);
  const current = Date.parse(observedAt);
  const expiration = Date.parse(expiresAt);
  if (![previous, current, expiration].every(Number.isFinite) || current < previous) throw new Error('invalid_expiry_window');
  const base = 'subscription:' + serviceId + ':' + cycleId;
  const name = cleanLabel(label);
  const output = [];
  const before = expiration - previous;
  const now = expiration - current;
  if (before > 48 * 3_600_000 && now <= 48 * 3_600_000 && now > 0) {
    output.push(notice(base + ':48hr', 'subscription_expiry', 'فقط ۲ روز تا پایان اشتراک',
      'اعتبار «' + name + '» تا دو روز آینده تمام می‌شود. برای تمدید آماده باشید.', serviceId));
  }
  if (before > 0 && now <= 0) {
    output.push(notice(base + ':expired', 'subscription_expiry', 'اعتبار اشتراک به پایان رسید',
      'زمان سرویس «' + name + '» تمام شده است. برای اتصال دوباره آن را تمدید کنید.', serviceId));
  }
  return output;
}

export function paymentNotice({ paymentId, previousStatus, status }) {
  if (!validId(paymentId)) throw new Error('invalid_payment_id');
  if (previousStatus === status || !['pending', 'reviewing'].includes(previousStatus)) return [];
  const eventKey = 'payment:' + paymentId + ':' + status;
  if (status === 'approved') return [notice(eventKey, 'purchase_success',
    'پرداخت شما تأیید شد', 'پرداخت شما تأیید شد. وضعیت سرویس و فاکتور را در گنج مشاهده کنید.', null)];
  if (status === 'rejected') return [notice(eventKey, 'payment_failure',
    'پرداخت شما تأیید نشد', 'رسید پرداخت رد شد. دلیل را در فاکتور بررسی کنید و در صورت نیاز با پشتیبانی در تماس باشید.', null)];
  return [];
}
