# Observability و پشتیبانی با حفظ حریم خصوصی

این سند قرارداد اجرایی فاز Enterprise برای گزارش باگ، Diagnostics، Analytics و Log اندروید را تعریف می‌کند. هدف، قابل‌عیب‌یابی شدن محصول بدون جمع‌آوری کانفیگ VPN، Token خرید، credential، محتوای ترافیک یا اطلاعات اضافه است.

## اصل‌ها

- ارسال Bug report و Attachment فقط پس از رضایت صریح و یک‌بارمصرف کاربر انجام می‌شود.
- جمع‌آوری فیلدها Allowlist است؛ جمع‌آوری آزاد و سپس Redact کردن مجاز نیست.
- `installationId` قبل از خروج از Device به SHA-256 reference تبدیل می‌شود.
- شناسه سرور فقط Public ID است؛ IP، hostname خصوصی و Profile ممنوع‌اند.
- `vless://`، `vmess://`، `trojan://`، `ss://`، JWT، Bearer، purchase token، password و private key در Payload و Log ممنوع‌اند.
- Handle محلی Attachment هرگز داخل DTO شبکه، Log، Crash یا `toString()` قرار نمی‌گیرد.
- Analytics به‌صورت opt-in است و فقط Event/Propertyهای نسخه‌شده و Allowlist شده را می‌پذیرد.

## جریان گزارش باگ

1. کاربر صفحه Report a problem را باز می‌کند.
2. اپ فیلدهای قابل ارسال را دقیق نشان می‌دهد و رضایت یک‌بارمصرف می‌گیرد.
3. `BugReportEnvelopeFactory` داده Device/Connection را اعتبارسنجی و متن را Redact می‌کند.
4. Attachmentها جداگانه با URL کوتاه‌عمر و Content-Type/Size محدود Upload می‌شوند.
5. Backend گزارش را با Bug ID، Severity اولیه و Audit event ثبت می‌کند.
6. Crash reference فقط یک شناسه Opaque برای هم‌بستگی با Provider است؛ Stack trace خام در API عمومی قرار نمی‌گیرد.

## داده‌های مجاز

| دسته | مجاز | ممنوع |
|---|---|---|
| Device | مدل، نسخه OS، نسخه App، installation reference هش‌شده | Advertising ID، IMEI، شماره تلفن |
| Connection | وضعیت، Network type، Error code، Public server ID | کانفیگ، Token، IP خصوصی، DNS query |
| Attachment | نوع، نام پاک‌سازی‌شده، اندازه، Media type | Handle محلی، مسیر فایل، داده بدون رضایت |
| Analytics | Funnel event و ویژگی‌های coarse | Telegram ID، User ID خام، URL/کانفیگ، purchase token |
| Log | Code عملیاتی و Attributeهای Allowlist | Body درخواست، Authorization، credential، Profile |

## Retention و دسترسی

- Diagnostic payload: حداکثر ۳۰ روز، مگر اینکه کاربر Ticket باز داشته باشد.
- Attachment: حذف خودکار پس از ۱۴ روز یا زودتر با بستن Ticket.
- Crash aggregation: مطابق Consent و قرارداد پردازش داده Provider.
- دسترسی Support به Attachment و Developer به Crash باید جدا و Audit شده باشد.
- درخواست حذف حساب، لینک‌های User↔Bug و Attachmentها را طبق Privacy SLA پاک می‌کند.

## Adapterهای بعدی

ماژول `core:observability` Provider-neutral است. Adapterهای Sentry/Crashlytics، Upload، FCM و Analytics باید در ماژول جدا باشند و این Gateها را دور نزنند. فعال‌سازی Provider در Production نیازمند Data Safety review، Consent screen، retention policy و DPA است.

## Gate انتشار

- تست خودکار Redaction و Allowlist سبز باشد؛
- Secret scanning روی Fixture و Artifact اجرا شود؛
- گزارش بدون Consent ارسال نشود؛
- Profile/Token در Crash و Log مشاهده نشود؛
- حذف Report/Attachment در Staging تمرین و Evidence ثبت شود.
