# Ganj Design System

> **Canonical UI/UX specification:** [`15-android-ui-ux-brand-system.fa.md`](15-android-ui-ux-brand-system.fa.md)  
> **Mandatory Liquid Glass standard:** [`16-liquid-glass-first-design-standard.fa.md`](16-liquid-glass-first-design-standard.fa.md)  
> این فایل خلاصه اجرایی Design System است. در موضوع Liquid Glass، Depth، Material hierarchy و Motion، سند 16 الزام اجرایی است.

# **اصل شماره ۱ — Liquid Glass-First در تمام اپلیکیشن**

## **تمام Ganj VPN باید از یک زبان طراحی Liquid Glass مدرن، یکپارچه و سراسری پیروی کند.**

این Requirement فقط برای چند Card یا Bottom Bar نیست. از Splash و Login تا Home، Servers، Connect، Store، Account، Settings، Wallet، Support و تمام Sheet/Dialog/Navigationها باید یک سیستم واحد از:

- layered depth؛
- edge-to-edge composition؛
- floating functional materials؛
- adaptive translucency؛
- fluid motion؛
- glass navigation/control؛
- hierarchy بین Content و Controls؛
- Emerald/Gold stained accents محدود؛
- accessibility/performance fallbacks

داشته باشند.

### **قاعده حیاتی**

> **Liquid Glass-first ≠ Blur Everywhere.**
>
> کل اپ باید زبان Liquid داشته باشد، اما طبق اصول حرفه‌ای Liquid Glass، Content layer برای خوانایی عمدتاً مات/پایدار می‌ماند و Glass به‌طور هدفمند در Navigation، Controls، Search، Sheets، Floating Actions و State transitions استفاده می‌شود.

هر UI PR که فقط چند `GlassCard` اضافه کند ولی Navigation، hierarchy، motion و depth آن همچنان Flat/Material-default باشد، Requirement طراحی را برآورده نکرده است.

## 1. Design Principles

1. **Liquid Glass-first:** تمام Screenها باید به یک Material/Depth/Motion system واحد تعلق داشته باشند.
2. **Connection first:** مهم‌ترین Action همیشه در یک نگاه قابل تشخیص است.
3. **Calm security:** امنیت با وضوح و ثبات منتقل می‌شود، نه هشدار و رنگ قرمز دائمی.
4. **Emerald identity:** سبز زمردی رنگ اصلی Brand و Action است.
5. **Refined gold:** طلایی فقط Premium/Signature Accent است، نه رنگ غالب صفحه.
6. **Neutral-first composition:** اکثر UI از Graphite/Warm Neutral surfaces ساخته می‌شود تا Emerald و Gold معنی داشته باشند.
7. **Meaningful depth:** هر سطح Glass/Elevation باید دلیل تعاملی داشته باشد.
8. **Fluid state motion:** Motion باید تغییر state را توضیح دهد، نه فقط تزئین باشد.
9. **Native Android behavior:** Gesture، Predictive Back، Haptic، Typography و Accessibility مطابق Android است.
10. **Honest premium:** Premium بودن از hierarchy، spacing، material و polish می‌آید؛ نه از طلایی‌کردن کل UI.

## 2. Brand Color Lock

لوگوی رسمی Ganj VPN از Emerald Green + Metallic Gold ساخته شده است. بنابراین:

- `#0A84FF` و `#007AFF` دیگر Brand Primary نیستند؛
- Material/iOS blue به‌عنوان رنگ اصلی ممنوع است؛
- Emerald برای Primary CTA، selection و connection accents استفاده می‌شود؛
- Gold برای premium badge، recommended plan، value/reward و hairline accent استفاده می‌شود؛
- Neutral dark/light فضای غالب را تشکیل می‌دهد.

### Representative Logo Palette

Emerald:

- `#012009`
- `#013A16`
- `#01481D`

Gold:

- `#B3710D`
- `#CF9221`
- `#D0B348`
- `#F8D162`
- `#FCF4B3`

رنگ‌های Logo مستقیماً برای همه UI surfaces استفاده نمی‌شوند؛ tokenهای UI برای Contrast و Accessibility تنظیم شده‌اند.

## 3. Color Tokens

### Dark

| Token | Value | Use |
|---|---|---|
| `bg.canvas` | `#070A08` | صفحه اصلی |
| `bg.elevated` | `#0B0F0C` | elevated canvas |
| `surface.primary` | `#101712` | Card |
| `surface.secondary` | `#151F18` | section/control surface |
| `text.primary` | `#F4F7F3` | متن اصلی |
| `text.secondary` | `#B3BDB5` | متن توضیح |
| `brand.primary` | `#1DA15D` | Emerald CTA/Selection |
| `brand.primaryBright` | `#2DC774` | active/highlight |
| `brand.primaryDeep` | `#075C31` | container/depth |
| `brand.gold` | `#D5A63A` | Premium accent |
| `brand.goldBright` | `#F0CD70` | small highlight |
| `state.connected` | `#3ACB7B` | اتصال موفق |
| `state.info` | `#3AA58D` | Jade info |
| `state.warning` | `#E0A33C` | warning |
| `state.danger` | `#EA6269` | destructive/error |

### Light

| Token | Value |
|---|---|
| `bg.canvas` | `#F7F8F4` |
| `bg.elevated` | `#FBFCF8` |
| `surface.primary` | `#FFFFFF` |
| `surface.secondary` | `#F0F4EF` |
| `text.primary` | `#121713` |
| `text.secondary` | `#5E6961` |
| `brand.primary` | `#08693A` |
| `brand.primaryBright` | `#0F8B4C` |
| `brand.primaryDeep` | `#044B2A` |
| `brand.gold` | `#B98318` |
| `brand.goldBright` | `#D8AA3D` |
| `state.connected` | `#11834A` |
| `state.info` | `#267E6D` |
| `state.warning` | `#A86510` |
| `state.danger` | `#C7434B` |

رنگ به‌تنهایی حامل وضعیت نیست؛ Icon، Label و Shape هم باید state را منتقل کنند.

## 4. Color Composition Rule

### Dark

- 68–74% Neutral؛
- 16–20% Emerald؛
- 4–7% Gold؛
- 3–6% Semantic/illustration.

### Light

- 74–80% Neutral؛
- 12–17% Emerald؛
- 3–5% Gold؛
- 3–5% Semantic.

این قانون مانع «فول طلایی» یا «فول سبز» شدن محصول می‌شود.

## 5. Liquid Glass Material Roles

به‌جای یک `GlassCard` عمومی، Material roleهای زیر الزامی‌اند:

### `Glass.Clear`

برای کنترل‌های کوچک شناور روی background ساده؛ transparency بیشتر و content کم.

### `Glass.Regular`

Material اصلی Bottom Navigation، Toolbar، Search، Filter، Segmented Control و floating controls.

### `Glass.Dense`

برای Bottom Sheet، Modal، Permission explanation و سطح‌هایی که readability بیشتری نیاز دارند.

### `Glass.Prominent`

برای Primary Actionهای محدود مثل Connect و Confirm Purchase؛ معمولاً Emerald stained glass.

### `Glass.OpaqueFallback`

برای Reduce Transparency، High Contrast، low-performance device، Battery Saver یا محیطی که blur مناسب نیست.

### قوانین سخت

- **Bottom Navigation باید Glass باشد.**
- Search/Filter/Floating Controls باید از Glass role مناسب استفاده کنند.
- Sheet shell باید Dense Glass یا fallback معادل داشته باشد.
- Content rows/cards به‌طور پیش‌فرض Glass نیستند.
- **Nested Glass به‌صورت عمومی ممنوع است.**
- raw blur/alpha پراکنده در Screen layer ممنوع است؛ همه از token/component system می‌آیند.
- Gold tint عمومی Glass ممنوع؛ Gold فقط Premium micro-accent.
- Emerald tint برای selected/active/primary state استفاده می‌شود.

## 6. Material 3 / M3 Expressive Policy

Jetpack Compose Material 3 / Material 3 Expressive پایه رفتار Native Android است.

- component behavior، motion و accessibility از Android گرفته می‌شود؛
- palette پیش‌فرض Material استفاده نمی‌شود؛
- Dynamic Color برای Brand Core به‌صورت پیش‌فرض خاموش است؛
- Android 16 edge-to-edge، predictive back و system UI behavior رعایت می‌شود؛
- M3 Expressive زبان Ganj را پشتیبانی می‌کند ولی جایگزین Brand/Glass system نمی‌شود.

## 7. Navigation

پنج Tab ثابت:

| Tab | مسئولیت | Visual |
|---|---|---|
| Home | داشبورد سرویس و وضعیت | neutral + Emerald selected |
| Servers | Search/Filter/Favorite/Smart recommendation | globe/location |
| Connect | کنترل اتصال و آمار زنده | center emphasis، Emerald state |
| Store | پلن‌ها، تمدید و خرید | Gold فقط premium micro-accent |
| Account | هویت، دستگاه‌ها، تراکنش و Settings | person |

Bottom Navigation باید floating `Glass.Regular` با edge-to-edge content underneath باشد. Connect در مرکز prominence بیشتری دارد ولی نباید giant Gold orb شود.

## 8. Typography

- Persian: Vazirmatn Variable یا Persian UI font تاییدشده با license مناسب؛
- Latin/numeric: Roboto/System Sans؛
- اعداد شبکه با `tabularNums`؛
- Body پیش‌فرض 15–16sp؛
- user-facing production text کمتر از 14sp نشود؛
- Dynamic font scaling تا 200% بدون critical clipping؛
- text روی Glass باید contrast کافی و weight مناسب داشته باشد؛
- Gold body text ممنوع است.

| Style | Size/Line | Weight |
|---|---|---|
| Hero | 36/44 | 700 |
| Display | 32/40 | 700 |
| Title 1 | 28/36 | 700 |
| Title 2 | 24/32 | 650 |
| Headline | 20/28 | 600 |
| Body Large | 16/25 | 450 |
| Body | 15/23 | 400 |
| Label | 13–14/18–20 | 550–600 |
| Caption | 12/18 | 500 |

## 9. Icon System

- Grid: 24×24؛
- Stroke: 1.8–2.0dp، round cap/join؛
- filled variant فقط برای Selected/important state؛
- Minimum touch target: 48dp؛
- icons عمدتاً Neutral/Emerald؛
- Gold فقط premium/reward؛
- Blue icon family برای Brand ممنوع؛
- Flag کشور همیشه همراه نام کشور.

## 10. Logo Variants Required

برای Production باید چهار variant وجود داشته باشد:

1. Full 3D Emerald/Gold برای Hero/Marketing؛
2. Simplified flat product mark؛
3. Android Adaptive App Icon؛
4. Monochrome/Themed + Notification silhouette.

Full 3D logo نباید در 24dp UI icon استفاده شود.

## 11. Connect Control

Connect باید Signature Liquid component محصول باشد.

### Disconnected

- neutral canvas؛
- Emerald glass ring/control؛
- label «اتصال»؛
- secondary server context.

### Connecting

- staged liquid morph؛
- subtle refraction/highlight movement؛
- مراحل متنی «بررسی شبکه» → «انتخاب بهترین مسیر» → «ایجاد اتصال امن»؛
- cancel تا جایی که runtime اجازه می‌دهد.

### Connected

- Emerald stable glass state؛
- soft glow که سریع settle می‌شود؛
- Server، Duration و local stats در صورت policy؛
- Disconnect واضح؛
- animation دائمی ممنوع.

### Reconnecting

- همان identity component با pulse کم؛
- Jade/Amber cue؛
- no panic red.

### Failed

- Glass opacity بیشتر و decoration کمتر؛
- Danger محدود؛
- Retry، Change Server، Diagnostics/Support.

## 12. Motion

| Motion | Duration | Behavior |
|---|---:|---|
| press | 90–140ms | tonal/scale subtle |
| micro feedback | 140–190ms | ease-out |
| tab/chip | 180–240ms | smooth |
| content | 220–320ms | fade/slide |
| glass transform | 280–420ms | fluid |
| sheet | 300–480ms | spring low bounce |
| connection morph | 450–750ms | staged |

- هدف 60fps؛ 120Hz compatible؛
- Blur و shader با performance class کاهش می‌یابد؛
- Reduce Motion → fade/short transition؛
- animation شروع واقعی اتصال را معطل نمی‌کند؛
- perpetual connected pulse ممنوع؛
- motion باید interruptible و state-driven باشد.

## 13. Haptics

- selection: انتخاب سرور/فیلتر؛
- light impact: Connect؛
- success: اتصال/خرید موفق؛
- warning: attention-worthy network state؛
- error: Failure نهایی؛
- در Scroll، heartbeat یا reconnect loop ممنوع.

## 14. Key Screen Specs

### Home

- service status first؛
- quick connect؛
- expiry؛
- recommended server؛
- quick actions؛
- floating glass nav/actions؛
- content cards mostly neutral/matte.

### Servers

- Glass search/filter؛
- Smart recommendation؛
- row حداقل 64–72dp؛
- server rows mostly solid/tonal؛
- ping فقط rate-limited در foreground.

### Connect

- strongest Liquid treatment؛
- responsive hero control 176–228dp بر اساس viewport؛
- real runtime state؛
- no raw profile.

### Store

- plan cards neutral؛
- glass period/filter controls؛
- recommended plan Gold rim/badge؛
- CTA Emerald؛
- Terms/renewal/cancel قبل از خرید واضح.

### Account

- Profile؛
- Telegram status؛
- Services؛
- Devices؛
- Transactions؛
- Privacy؛
- Support؛
- Settings؛
- content-first + glass nav/actions.

## 15. RTL and Localization

- فارسی RTL و English LTR؛
- IP/UUID/protocol/speed unit داخل Bidi isolation؛
- هیچ user-facing hardcoded English در Production؛
- strings از resources؛
- pricing/date/latency locale-safe.

## 16. Accessibility Checklist

- Touch target حداقل 48dp؛
- Contrast WCAG AA target؛
- TalkBack order مطابق hierarchy؛
- Connect state با live region کنترل‌شده؛
- همه gestureها جایگزین Button دارند؛
- Reduce Motion؛
- **Reduce Transparency → `Glass.OpaqueFallback`**؛
- High Contrast؛
- font scale تا 200%؛
- Gold-on-white text فقط با contrast معتبر.

## 17. Performance / Battery

- Glass effect باید performance-aware باشد؛
- low-end/battery-saver fallback الزامی؛
- no continuous expensive shader؛
- no blur on offscreen content؛
- Connected animation بعد از settle تقریباً static؛
- Final UI نیازمند frame/jank evidence است.

## 18. Current Migration Requirement

Branch `phase-7/android-ui-accessibility-v2` قبل از Final UI merge باید:

- `GanjBlue` حذف کند؛
- Emerald/Gold/Neutral scheme وارد کند؛
- semantic `GlassRole` system بسازد؛
- Bottom Navigation را Liquid Glass کند؛
- Search/Filter/Sheet/Connect را به Material roleهای سند 16 migrate کند؛
- hardcoded Glass alpha/blur را حذف کند؛
- Light/Dark + fa/en + RTL + Accessibility review شود؛
- performance/screenshot tests داشته باشد.

## 19. منابع

- Apple Liquid Glass / HIG Materials / Color / Design Principles؛
- Android Developers — Material 3 / Material 3 Expressive؛
- Android Developers — Accessibility / Core App Quality.

جزئیات Brand در [`15-android-ui-ux-brand-system.fa.md`](15-android-ui-ux-brand-system.fa.md) و استاندارد اجباری Liquid Glass در [`16-liquid-glass-first-design-standard.fa.md`](16-liquid-glass-first-design-standard.fa.md) ثبت شده است.
