const MAX_CONFIG_LENGTH = 16_384;
const MIN_REASON_LENGTH = 8;
const MAX_REASON_LENGTH = 500;

export function clearSensitiveInput(input) {
  if (input && typeof input === 'object' && 'value' in input) input.value = '';
}

export function requireAuditReason(value) {
  const reason = String(value ?? '').trim();
  if (reason.length < MIN_REASON_LENGTH || reason.length > MAX_REASON_LENGTH) {
    throw new Error('دلیل تغییر باید بین ۸ تا ۵۰۰ نویسه باشد.');
  }
  return reason;
}

export async function consumeFreeConfig(input, operation) {
  if (!input || typeof input !== 'object' || typeof operation !== 'function') {
    throw new Error('ورودی امن کانفیگ در دسترس نیست.');
  }

  let config = String(input.value ?? '').trim();
  clearSensitiveInput(input);

  try {
    if (config.length < 12 || config.length > MAX_CONFIG_LENGTH) {
      throw new Error('کانفیگ رایگان کامل و معتبر را وارد کنید.');
    }
    return await operation(config);
  } finally {
    config = '';
    clearSensitiveInput(input);
  }
}
