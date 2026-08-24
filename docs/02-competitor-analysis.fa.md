# تحلیل رقبا و فرصت محصول

## روش

تحلیل بر اساس قابلیت‌های رسمی منتشرشده تا 2026-08-24 و الگوهای UX شناخته‌شدهٔ اپ‌های Mobile VPN انجام شده است. هدف کپی UI نیست؛ الگوهای موفق به Requirement قابل سنجش تبدیل شده‌اند.

## ماتریس قابلیت

| محصول | نقطه قوت UX/Product | محدودیت یا فرصت برای Ganj |
|---|---|---|
| NordVPN | Quick Connect، Specialty Servers، Kill Switch، Threat Protection و تجربهٔ ساده برای کاربر عادی | Home می‌تواند شلوغ شود؛ Ganj باید فروش/کیف پول را از اتصال جدا نگه دارد. |
| Proton VPN | Free بدون تبلیغ/محدودیت داده، Secure Core، Profile و شفافیت Open Source | Ganj Free می‌تواند ساده‌تر باشد، ولی محدودیت باید شفاف و Server-side باشد. |
| Surfshark | Dynamic MultiHop، IP Rotator، Pause VPN و دستگاه نامحدود | Feature discovery قوی؛ Ganj باید قابلیت Advanced را Progressive Disclosure کند. |
| ExpressVPN | Fastest Location/Smart Location و شاخص کیفیت قابل فهم | الگوی مناسب برای Smart Connect چندکاندیدایی و Confidence Label. |
| Windscribe | Free شفاف، Build-a-Plan، Firewall و Split Tunneling | مدل Free quota + ارتقای واضح برای بازار ایران مناسب است. |
| Outline | ورود بسیار ساده با Access Key و یک دکمه اتصال | بهترین مرجع برای Import/Recovery؛ اما Store و Account ندارد. |
| v2rayNG | پشتیبانی گسترده Xray، Import و Routing حرفه‌ای | UX تخصصی و Configuration-first؛ Ganj باید Service-first باشد. |

## تحلیل صفحات

### Home

الگوی برنده: خلاصهٔ وضعیت، Quick Action و یک Upsell متناسب؛ نه فهرست همه قابلیت‌ها.

Ganj Home:

- Greeting و وضعیت Account؛
- کارت سرویس فعال با حجم/روز باقی‌مانده؛
- آخرین سرور و پیشنهاد Smart Connect؛
- یک Banner هدفمند؛
- Shortcutهای Wallet، Renew، Support و Speed Test.

### Connect

مرکز اپ باید یک State Machine شفاف باشد:

`Disconnected → Preparing → Probing → Authorizing → Connecting → Connected → Reconnecting/Failed`

برای هر State متن انسانی و Action مشخص نمایش داده می‌شود. نمایش صرف Spinner ممنوع است.

### Server Selection

- Search روی نام کشور، شهر و Tag؛
- فیلتر Free/Premium/VIP، Protocol و Streaming؛
- Sort: Recommended، Latency، Load، Favorite؛
- Ping رنگی به‌تنهایی کافی نیست؛ Labelهای Excellent/Good/Busy با Accessibility Text لازم‌اند؛
- VIP Lock باید دلیل و CTA Upgrade داشته باشد.

### Subscription Flow

بهترین الگو: مقایسهٔ 3 پلن، قیمت مؤثر ماهانه، Savings، Restore و Terms شفاف. Dark Pattern، Countdown جعلی و Plan از پیش انتخاب‌شدهٔ گمراه‌کننده ممنوع.

برای Ganj دو Checkout Surface تعریف می‌شود:

- Play build: Google Play Billing؛
- Direct build: Wallet، Telegram و Gateway با Receipt/Audit.

### Account

Account محل تنظیم VPN نیست. Profile، Telegram، Plan، Devices، Wallet، Transactions و Security Session در این Tab قرار می‌گیرند؛ Auto Connect/Kill Switch در Settings زیر Account است.

## فرصت تمایز Ganj VPN

1. **Service-first connection:** کاربر سرویس خریداری‌شده را انتخاب می‌کند، نه کانفیگ خام.
2. **Telegram-native ownership:** OIDC و Bot هر دو یک Account را نمایش می‌دهند.
3. **Smart Connect قابل توضیح:** نمایش «بهترین برای شبکه شما» همراه Latency/Load و زمان آخرین سنجش.
4. **Local market payments:** در نسخه Direct، Wallet و درگاه‌های فعلی حفظ می‌شوند.
5. **Transparent Free:** محدودیت سرعت/حجم و نوع تبلیغ قبل از اتصال روشن است.
6. **No traffic monetization:** تبلیغ از Context حساب و پلن هدف می‌گیرد، نه مقصدهای مرور.
7. **Pro controls without clutter:** Import، Routing و Per-app VPN در Advanced Mode قرار می‌گیرند.

## منابع رسمی

- [NordVPN features](https://nordvpn.com/features/)
- [Proton VPN Free](https://protonvpn.com/free-vpn)
- [Proton VPN features](https://protonvpn.com/features)
- [Surfshark features](https://support.surfshark.com/hc/en-us/articles/10448039999122-Surfshark-Features)
- [ExpressVPN Fastest Location](https://www.expressvpn.com/support/knowledge-hub/fastest-location/)
- [Windscribe Free](https://windscribe.net/features/use-for-free)
- [Outline access key model](https://support.getoutline.org/en-GB/client/getting-started/get-access-key/)
- [v2rayNG repository and GPL-3.0 license](https://github.com/2dust/v2rayNG)

