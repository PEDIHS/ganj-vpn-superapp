# Ganj Design System

## 1. Design Principles

1. **Connection first:** مهم‌ترین Action همیشه در یک نگاه قابل تشخیص است.
2. **Calm security:** امنیت با وضوح و ثبات منتقل می‌شود، نه هشدار و رنگ قرمز دائمی.
3. **Progressive power:** قابلیت‌های حرفه‌ای بعد از نیاز کاربر آشکار می‌شوند.
4. **Native behavior:** Gesture، Back، Haptic، Typography و Accessibility مطابق Platform است.
5. **Honest premium:** Gold فقط برای Entitlement و Value واقعی است، نه تزئین همه صفحه.

## 2. Liquid Glass Usage

طبق HIG، Liquid Glass یک لایهٔ Functional برای Navigation و Control است، نه Background تمام Cardها. در Ganj:

- Floating bottom bar و Connect control از Glass regular استفاده می‌کنند.
- Sheetهای transient می‌توانند Glass داشته باشند.
- Content cards از Surface مات و با کنتراست ثابت استفاده می‌کنند.
- Clear glass فقط روی Hero background غنی و با Scrim کنترل‌شده مجاز است.
- Reduce Transparency و Increase Contrast باید ظاهر جایگزین کامل داشته باشند.

مرجع: [Apple HIG — Materials](https://developer.apple.com/design/human-interface-guidelines/materials)

## 3. Navigation

پنج Tab ثابت:

| Tab | مسئولیت | Navigation icon |
|---|---|---|
| Home | داشبورد سرویس، Wallet shortcut و Banner | house |
| Servers | Search/Filter/Favorite/Smart recommendation | globe/location |
| Connect | کنترل اتصال و آمار زنده | power/tunnel |
| Store | پلن‌ها، تمدید و خرید | bag |
| Account | هویت، دستگاه‌ها، تراکنش و Settings | person |

Connect در مرکز با اندازه و elevation بیشتر است، اما semantics آن همچنان یک Tab است و با Button لحظه‌ای اشتباه نمی‌شود.

## 4. Color Tokens

### Dark

| Token | Value | Use |
|---|---|---|
| `bg.canvas` | `#060A12` | صفحه اصلی |
| `bg.elevated` | `#0D1424` | Surface |
| `surface.primary` | `#111B2E` | Card |
| `text.primary` | `#F5F7FB` | متن اصلی |
| `text.secondary` | `#A8B3C7` | متن توضیح |
| `brand.primary` | `#0A84FF` | CTA/Selection |
| `state.connected` | `#30D158` | اتصال موفق |
| `state.warning` | `#FFB340` | Load/Expiry |
| `state.danger` | `#FF453A` | Error/Destructive |
| `vip.start` | `#F8DF8B` | Gold gradient |
| `vip.end` | `#B7791F` | Gold gradient |

### Light

| Token | Value |
|---|---|
| `bg.canvas` | `#F6F8FC` |
| `bg.elevated` | `#FFFFFF` |
| `surface.primary` | `#FFFFFF` |
| `text.primary` | `#0A1020` |
| `text.secondary` | `#5F6B7A` |
| `brand.primary` | `#007AFF` |
| `state.connected` | `#248A3D` |

رنگ به‌تنهایی حامل وضعیت نیست؛ Icon، Label و Shape هم تغییر می‌کنند.

## 5. Typography

- Persian: Vazirmatn variable، fallback سیستم؛
- Latin/numeric: Roboto/System Sans در Android؛
- اعداد شبکه با `tabularNums`؛
- Minimum body: 14sp؛ default body: 16sp؛
- Dynamic font scaling تا 200% بدون clip؛
- SF Pro و SF Symbols در Android توزیع نمی‌شوند.

| Style | Size/Line | Weight |
|---|---|---|
| Display | 34/42 | 700 |
| Title 1 | 28/36 | 700 |
| Title 2 | 22/28 | 650 |
| Headline | 17/24 | 600 |
| Body | 16/24 | 400 |
| Callout | 15/22 | 500 |
| Caption | 12/18 | 500 |

## 6. Icon System

- Grid: 24×24؛ optical bounding box 20×20؛
- Stroke: 1.8–2.0dp، round cap/join؛
- Filled variant فقط برای Tab انتخاب‌شده و State مهم؛
- Minimum touch target: 48dp؛
- مجموعه اختصاصی از SVGهای هم‌خانواده؛ مخلوط‌کردن کتابخانه‌های نامرتبط ممنوع؛
- Flag کشور دارایی تزئینی است و همیشه نام کشور کنار آن می‌آید.

## 7. Connect Control

### Disconnected

- Surface خنثی، حلقه آبی کم‌رنگ؛
- Label: «اتصال»؛
- Secondary: آخرین یا Smart Server.

### Connecting

- Ring progress نامعین فقط در مرحله کوتاه؛
- مراحل متنی: «بررسی شبکه»، «انتخاب بهترین سرور»، «ساخت تونل»؛
- Cancel تا قبل از Commit اتصال فعال است.

### Connected

- Emerald state با Check/Tunnel icon؛
- Haptic success یک‌بار؛
- نمایش Server، Duration و transfer local؛
- Disconnect یک Action واضح اما بدون قرمز هشداردهنده.

### Failed

- Error code فنی پنهان در Details؛
- پیام قابل اقدام: Retry، Change server، Check time، Support؛
- هیچ Loop بی‌نهایت بدون اطلاع کاربر وجود ندارد.

## 8. Motion

| Motion | Duration | Curve |
|---|---:|---|
| Micro feedback | 120–180ms | ease-out |
| Tab/content | 220–300ms | standard |
| Sheet | 320–420ms | spring, low bounce |
| Connection morph | 450–700ms | staged spring |

- هدف 60fps؛ روی دستگاه 120Hz هماهنگ با Frame clock.
- Blur و shader با Performance class کاهش می‌یابد.
- Reduce Motion: Liquid morph به fade/scale کوتاه تبدیل می‌شود.
- Animation هیچ‌گاه شروع واقعی اتصال را معطل نمی‌کند.

## 9. Haptics

- selection: انتخاب سرور/فیلتر؛
- light impact: Connect press؛
- success: اتصال کامل؛
- warning: تغییر شبکه یا Reconnect؛
- error: Failure نهایی؛
- در Scroll، heartbeat یا هر frame ممنوع.

## 10. RTL and Localization

- فارسی RTL و English LTR از روز اول؛
- IP، UUID، protocol و speed unit داخل Bidi isolation؛
- واحدها: `MB/s`, `ms`, `GB` با NumberFormatter؛
- متن داخل Bitmap و Icon ممنوع؛
- طول German/Arabic برای QA layout در نظر گرفته می‌شود.

## 11. Accessibility Checklist

- Contrast حداقل WCAG AA؛
- TalkBack order مطابق hierarchy؛
- Connect state با live region کنترل‌شده؛
- server row description شامل Country, access tier, latency, load و favorite state؛
- همه gestureها جایگزین Button دارند؛
- Reduce transparency/motion و high contrast تست می‌شوند.

## 12. Key Screen Specs

### Home

Header 64dp، Service card 156dp، Quick actions 2×2، Banner aspect 16:7، safe padding 20dp.

### Servers

Sticky search، chip filters، recommended section، country groups، row حداقل 64dp. Ping هر 30–60 ثانیه فقط در foreground تازه می‌شود.

### Store

سه Plan card قابل مقایسه؛ VIP gradient محدود؛ Terms/renewal/cancel همیشه قبل از CTA قابل مشاهده.

### Account

Profile header، Plan/expiry، Wallet، Devices، Transactions، Telegram status، Settings و Privacy.

