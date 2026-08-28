# Ganj VPN — Android UI/UX, Visual Brand & Interaction System

> **وضعیت سند:** Canonical UI/UX & Visual Brand Specification  
> **Scope:** Android-only برای چرخه فعلی محصول  
> **مخزن:** `PEDIHS/ganj-vpn-superapp`  
> **جایگاه:** این سند مرجع اصلی تمام تصمیم‌های بصری، رنگ، Layout، Motion، Components، Accessibility و Screen Design در Ganj VPN است.  
> **قانون تعارض:** در موضوع UI/UX و Brand، این سند بر tokenهای آبی قدیمی، Mockupهای قبلی و implementationهای موقت مقدم است.  
> **لوگوی مرجع:** لوگوی رسمی ارائه‌شده توسط Product Owner؛ فرم G/Shield با Diamond مرکزی، Emerald Green و Metallic Gold.  
> **اصل مهم:** Ganj VPN نباید «فول طلایی»، «فول سبز» یا «لوکسِ شلوغ» شود. هویت باید Premium، مدرن، آرام و حرفه‌ای باشد؛ Emerald و Gold نقش امضا دارند و Neutral surfaces فضای اصلی تجربه را می‌سازند.

---

# 0. چرا این سند وجود دارد؟

UI فعلی پروژه برای Functional Development ساخته شده و Final Product Design نیست. بخشی از Design System قبلی نیز از رنگ آبی iOS/Material به‌عنوان Primary استفاده می‌کرد که با لوگو و هویت رسمی Ganj VPN هم‌خوان نیست.

از این سند به بعد:

- **Blue دیگر Brand Primary نیست.**
- هیچ Developer/AI نباید `#0A84FF`، `#007AFF` یا رنگ آبی مشابه را به‌عنوان Primary Ganj استفاده کند.
- Material 3 / M3 Expressive چارچوب Native Android است، نه هویت برند.
- Apple HIG و Liquid Glass فقط برای clarity، hierarchy، motion و material inspiration استفاده می‌شوند؛ نه برای کپی رنگ آبی یا ساخت ظاهر iOS clone.
- لوگوی Emerald + Gold منبع اصلی Visual DNA است.
- رنگ‌های برند باید با Neutralهای گرم/گرافیتی ترکیب شوند تا اپ شبیه «ویترین طلافروشی» یا «کازینو» نشود.

---

# 1. Design North Star

## 1.1 تعریف شخصیت بصری

Ganj VPN باید در نگاه اول این حس‌ها را منتقل کند:

- امن؛
- سریع؛
- Premium؛
- قابل اعتماد؛
- مدرن؛
- کنترل‌شده و آرام؛
- دارای ارزش مالی/اشتراک بدون نمایش اغراق‌آمیز ثروت؛
- تکنولوژیک، اما نه Developer-looking؛
- نزدیک به کیفیت محصولات جهانی، اما با هویت مستقل.

سه کلمه کلیدی:

> **Emerald Security — Refined Gold — Calm Technology**

## 1.2 چیزی که نمی‌خواهیم

UI نباید:

- تمام‌صفحه طلایی باشد؛
- Gradient طلایی در همه Cardها داشته باشد؛
- هر Button را طلایی کند؛
- Background سبز اشباع داشته باشد؛
- از آبی به‌عنوان رنگ اصلی استفاده کند؛
- شبیه App مالی/کریپتو/کازینو شود؛
- بیش‌ازحد Glass و Blur داشته باشد؛
- Text contrast را فدای لوکس بودن کند؛
- Glow و Neon دائمی داشته باشد؛
- لوگوی 3D را در هر ردیف و دکمه تکرار کند؛
- شبیه کپی NordVPN/Proton/Surfshark/iOS باشد.

---

# 2. منابع طراحی روز و روش استفاده در Ganj

## 2.1 Android Native Foundation

پایه Interaction و Component behavior:

- Jetpack Compose؛
- Material 3؛
- Material 3 Expressive؛
- Android 16 system UI behavior؛
- edge-to-edge؛
- predictive back؛
- proper system bars؛
- haptics؛
- TalkBack semantics؛
- adaptive layout.

M3 Expressive برای Ganj به معنی استفاده از:

- hierarchy واضح؛
- motion purpose-driven؛
- shape variation کنترل‌شده؛
- component emphasis؛
- typography expressive در Heroها؛
- interaction feedback طبیعی

است؛ نه پذیرش Dynamic Color تصادفی یا palette پیش‌فرض Material.

## 2.2 Apple HIG Inspiration

از Apple HIG این اصول الهام گرفته می‌شوند:

- clarity؛
- agency؛
- hierarchy؛
- content-first composition؛
- restrained use of materials؛
- meaningful motion؛
- glass برای control/navigation نه برای پوشاندن همه content.

## 2.3 Dynamic Color Policy

**Material You Dynamic Color برای Brand Core به‌صورت پیش‌فرض خاموش است.**

دلیل:

- Ganj هویت رنگی مشخص دارد؛
- Dynamic Color ممکن است اپ را بنفش، آبی یا صورتی کند؛
- Brand recognition را از بین می‌برد؛
- Store screenshots و cross-device consistency را خراب می‌کند.

در آینده Dynamic Color فقط می‌تواند برای tertiary decorative accent و آن هم opt-in/controlled بررسی شود.

---

# 3. Visual DNA استخراج‌شده از لوگو

لوگوی رسمی از سه خانواده بصری تشکیل شده:

1. Emerald / Deep Forest Green؛
2. Metallic Warm Gold؛
3. Dark/Black negative space.

نمونه‌های representative استخراج‌شده از خود لوگو:

### Emerald family

- `#012009` — Forest Black Green؛
- `#013A16` — Deep Emerald؛
- `#01481D` — Core Logo Emerald؛
- `#0A6A35` — UI-adjusted Emerald؛
- `#1A8A4E` — Mid Emerald؛
- `#2CB36B` — Bright Interaction Emerald.

### Gold family

- `#734204` — Gold Shadow؛
- `#B3710D` — Deep Antique Gold؛
- `#CF9221` — Core Logo Gold؛
- `#D0B348` — Muted Gold؛
- `#F8D162` — Gold Highlight؛
- `#FCF4B3` — Champagne Highlight.

نکته: مقادیر فوق رنگ‌های بصری Logo هستند. برای UI تمام آن‌ها مستقیماً استفاده نمی‌شوند؛ UI palette باید contrast، readability و accessibility مناسب داشته باشد.

---

# 4. Color Strategy — قانون اصلی

## 4.1 مدل توزیع رنگ

برای جلوگیری از «فول سبز/طلایی» شدن:

### Dark Theme

- 68–74% Neutral dark surfaces؛
- 16–20% Emerald family؛
- 4–7% Gold accent؛
- 3–6% Semantic colors/illustrations/data.

### Light Theme

- 74–80% Warm neutral surfaces؛
- 12–17% Emerald family؛
- 3–5% Gold accent؛
- 3–5% Semantic colors.

این درصدها راهنمای composition هستند، نه قانون pixel-perfect.

## 4.2 نقش Emerald

Emerald رنگ اصلی Brand و Action است:

- Primary CTA؛
- selected navigation؛
- active toggle؛
- connection accent؛
- primary progress؛
- focus state؛
- links داخلی مهم؛
- key illustration accent.

## 4.3 نقش Gold

Gold **رنگ Secondary Premium Accent** است، نه Primary Action عمومی.

استفاده مجاز:

- Premium/VIP label؛
- plan recommendation؛
- subscription value accent؛
- thin highlight/rim؛
- selected premium service؛
- small badge؛
- reward/referral earned؛
- subtle decorative line؛
- logo-related moments؛
- Store hero accent.

استفاده ممنوع:

- تمام Background؛
- تمام Buttonها؛
- متن Body؛
- تمام Cardها؛
- warningهای عمومی صرفاً چون Gold است؛
- navbar کامل طلایی؛
- glow سنگین دائمی.

## 4.4 Blue Ban

در Brand/Product surfaces:

- `#0A84FF` ممنوع به‌عنوان Primary؛
- `#007AFF` ممنوع به‌عنوان Primary؛
- Material default blue ممنوع؛
- iOS blue ممنوع؛
- لینک‌ها نیز ترجیحاً Emerald/Jade هستند، نه آبی.

تنها استفاده از blue-family در آینده باید دلیل semantic خاص و Design approval داشته باشد؛ در وضعیت فعلی هیچ نیاز Product به آن وجود ندارد.

---

# 5. Canonical Semantic Color Tokens

## 5.1 Dark Theme

| Token | Value | نقش |
|---|---|---|
| `bg.canvas` | `#070A08` | Canvas اصلی، نزدیک مشکی با undertone سبز |
| `bg.canvasElevated` | `#0B0F0C` | سطح مرتفع |
| `surface.primary` | `#101712` | Card اصلی |
| `surface.secondary` | `#151F18` | Card/section ثانویه |
| `surface.tertiary` | `#1B2720` | interactive/elevated |
| `surface.glass` | `rgba(20,30,24,.72)` | Glass کنترل‌شده |
| `border.subtle` | `#26332A` | Border عمومی |
| `border.strong` | `#3A493E` | Border focus/important |
| `text.primary` | `#F4F7F3` | متن اصلی |
| `text.secondary` | `#B3BDB5` | متن ثانویه |
| `text.tertiary` | `#879188` | متن کم‌اهمیت |
| `text.inverse` | `#09100B` | روی Bright CTA |
| `brand.emerald` | `#1DA15D` | Brand core UI |
| `brand.emeraldBright` | `#2DC774` | Active/pressed/connected highlights |
| `brand.emeraldDeep` | `#075C31` | container/gradient depth |
| `brand.emeraldMuted` | `#163C29` | selected muted background |
| `brand.gold` | `#D5A63A` | Premium accent |
| `brand.goldBright` | `#F0CD70` | highlight small areas |
| `brand.goldDeep` | `#8C6417` | depth/border |
| `brand.champagne` | `#F6E8B0` | high-end subtle highlight |
| `state.connected` | `#3ACB7B` | Connected state |
| `state.info` | `#3AA58D` | Jade info؛ نه blue |
| `state.warning` | `#E0A33C` | Warning amber |
| `state.danger` | `#EA6269` | Error/destructive |
| `state.disabled` | `#566058` | disabled content |

## 5.2 Light Theme

| Token | Value | نقش |
|---|---|---|
| `bg.canvas` | `#F7F8F4` | Warm light canvas |
| `bg.canvasElevated` | `#FBFCF8` | elevated neutral |
| `surface.primary` | `#FFFFFF` | card |
| `surface.secondary` | `#F0F4EF` | soft green-neutral |
| `surface.tertiary` | `#E9EFEA` | control container |
| `surface.glass` | `rgba(255,255,252,.80)` | translucent surface |
| `border.subtle` | `#DCE3DC` | border |
| `border.strong` | `#B8C4BA` | important border |
| `text.primary` | `#121713` | primary text |
| `text.secondary` | `#5E6961` | secondary |
| `text.tertiary` | `#7C887F` | tertiary |
| `text.inverse` | `#FFFFFF` | on dark/emerald |
| `brand.emerald` | `#08693A` | primary action |
| `brand.emeraldBright` | `#0F8B4C` | hover/selected |
| `brand.emeraldDeep` | `#044B2A` | dark emphasis |
| `brand.emeraldMuted` | `#E0F1E6` | selected background |
| `brand.gold` | `#B98318` | premium accent contrast-safe |
| `brand.goldBright` | `#D8AA3D` | highlight |
| `brand.goldDeep` | `#76510D` | premium text/border |
| `brand.champagne` | `#F5E8B8` | decorative surface |
| `state.connected` | `#11834A` | connected |
| `state.info` | `#267E6D` | jade info |
| `state.warning` | `#A86510` | warning |
| `state.danger` | `#C7434B` | error |
| `state.disabled` | `#9AA29C` | disabled |

## 5.3 Color Contrast Rule

- Body text حداقل WCAG AA؛
- Small text هدف `4.5:1` یا بهتر؛
- Large text/icon هدف `3:1` یا بهتر؛
- Gold text روی white فقط اگر contrast کافی دارد؛ در غیر این صورت Gold برای border/icon استفاده شود و متن `text.primary` بماند؛
- `#F8D162` روی white برای متن مناسب نیست؛ فقط highlight/decorative؛
- color هیچ‌وقت تنها signal وضعیت نیست.

---

# 6. Gradient System

Gradient باید محدود و premium باشد.

## 6.1 Emerald Hero Gradient

```text
#075C31 → #0B7440 → #101712
```

استفاده:

- Connect hero background؛
- occasional Home hero؛
- large visual moment.

## 6.2 Emerald-Gold Signature Gradient

فقط برای Brand/Premium highlight:

```text
Emerald Deep → Emerald Core → thin Gold highlight
```

نباید کل surface را به سبز→طلایی تبدیل کرد.

## 6.3 Gold Gradient

استفاده صرفاً در:

- premium icon؛
- badge؛
- logo-adjacent treatment؛
- recommended plan border.

نمونه:

```text
#8C6417 → #D5A63A → #F0CD70
```

## 6.4 ممنوعیت

- rainbow gradients؛
- neon green؛
- blue-purple gradients؛
- full-page metallic gold؛
- animated rainbow borders.

---

# 7. Neutral Palette Philosophy

Neutralهای Ganj باید pure gray سرد نباشند. undertone بسیار کم Emerald باعث consistency می‌شود.

Dark:

- Black-ish green؛
- Graphite green؛
- Charcoal olive-neutral.

Light:

- Warm ivory؛
- soft stone؛
- very pale green-gray.

هدف: وقتی Emerald و Gold حذف شوند هم UI باید polished و premium بماند.

---

# 8. Logo Usage System

لوگوی رسمی 3D Metallic برای همه اندازه‌ها مناسب نیست. چهار Variant باید وجود داشته باشد:

## 8.1 Hero/Marketing Logo

- همان Logo سه‌بعدی Emerald/Gold؛
- Splash premium moment؛
- Store listing؛
- Website/marketing؛
- large empty state branded moments.

## 8.2 Product Flat Mark

نسخه Simplified 2D از G/Shield/Diamond:

- top app bar؛
- about page؛
- compact branding؛
- small cards.

## 8.3 Adaptive App Icon

Android Adaptive Icon:

- foreground simplified G/Shield؛
- safe zone رعایت شود؛
- background Deep Emerald/Graphite؛
- Gold فقط در foreground accent؛
- detail Diamond ساده‌تر شود.

## 8.4 Monochrome Themed Icon

برای Android themed icons:

- تک‌رنگ؛
- بدون gradient؛
- silhouette واضح؛
- جزئیات کوچک diamond حذف/ساده شود.

## 8.5 Notification Icon

- pure white mask؛
- بدون رنگ/gradient؛
- symbol ساده G/Shield یا tunnel mark؛
- readable در 24dp.

## 8.6 Logo Misuse

ممنوع:

- stretch؛
- rotate؛
- shadow جدید تصادفی؛
- recolor آبی؛
- گذاشتن روی background با contrast ضعیف؛
- تبدیل تمام UI به texture metallic لوگو.

---

# 9. Typography System

## 9.1 Font Family

### Persian

Primary recommendation:

- `Vazirmatn Variable` یا یک Persian UI family با license مناسب و readability بالا؛
- اعداد فارسی/لاتین بر اساس locale و context؛
- fallback system sans.

### Latin / Numbers

- Roboto / Android system sans؛
- tabular numerals برای latency، speed، timer، price table؛
- protocol names در LTR isolation.

## 9.2 Scale

| Style | Size | Line height | Weight | Usage |
|---|---:|---:|---:|---|
| Hero | 36sp | 44sp | 700 | Connect/Store Hero محدود |
| Display | 32sp | 40sp | 700 | numbers/key value |
| H1 | 28sp | 36sp | 700 | page title |
| H2 | 24sp | 32sp | 650 | sections |
| H3 | 20sp | 28sp | 600 | card title |
| Title | 18sp | 26sp | 600 | rows/cards |
| Body Large | 16sp | 25sp | 450 | primary body |
| Body | 15sp | 23sp | 400 | secondary body |
| Label Large | 14sp | 20sp | 600 | buttons/chips |
| Label | 13sp | 18sp | 550 | metadata |
| Caption | 12sp | 18sp | 500 | tertiary metadata |

## 9.3 Rules

- Body production کمتر از 14sp نشود؛
- قیمت/مدت/latency برای scanning واضح باشد؛
- ALL CAPS برای Persian ممنوع/بی‌معنی؛
- متن توضیحی خاکستری بیش‌ازحد کمرنگ نشود؛
- font scale تا 200% critical clipping نداشته باشد؛
- طول German/Arabic/English در QA در نظر گرفته شود.

---

# 10. Spacing & Grid

Base unit: `4dp`.

Recommended tokens:

- `space.2xs = 2dp` فقط optical adjustment؛
- `space.xs = 4dp`؛
- `space.sm = 8dp`؛
- `space.md = 12dp`؛
- `space.lg = 16dp`؛
- `space.xl = 20dp`؛
- `space.2xl = 24dp`؛
- `space.3xl = 32dp`؛
- `space.4xl = 40dp`؛
- `space.5xl = 48dp`.

Screen horizontal padding:

- compact phone: 16dp؛
- standard phone: 20dp؛
- large phone/foldable pane: 24–32dp.

Section spacing:

- related content: 8–12dp؛
- card-to-card: 12–16dp؛
- major section: 24–32dp.

---

# 11. Shape & Radius System

Ganj باید shape مدرن داشته باشد اما Bubble-heavy نشود.

- `radius.xs = 8dp`؛
- `radius.sm = 12dp`؛
- `radius.md = 16dp`؛
- `radius.lg = 20dp`؛
- `radius.xl = 28dp`؛
- `radius.hero = 36dp`؛
- pill = full.

Use:

- buttons: 14–18dp یا pill بسته به component؛
- cards: 18–24dp؛
- bottom nav: 26–32dp؛
- hero surfaces: 28–36dp.

Avoid:

- هر چیز pill؛
- radiusهای تصادفی؛
- 40dp+ در rowهای ساده.

---

# 12. Elevation, Shadow & Border

## 12.1 Dark Theme

Shadow باید بسیار subtle باشد؛ separation بیشتر از surface tone + border می‌آید.

- 1px/1dp border subtle؛
- soft shadow opacity پایین؛
- Emerald ambient glow فقط در Connect active state؛
- Gold glow فقط premium spotlight، نه persistent.

## 12.2 Light Theme

- low elevation؛
- border + soft ambient shadow؛
- heavy drop shadow ممنوع.

## 12.3 Focus/Selected

Selected normal:

- Emerald muted container + emerald icon/text؛
- premium selected: همین + hairline Gold accent.

---

# 13. Liquid Glass / Glassmorphism Policy

## 13.1 فلسفه

Glass باید Functional Layer باشد، نه Theme کل App.

## 13.2 مکان‌های مجاز

- bottom navigation floating container؛
- transient bottom sheet header؛
- Connect floating control layer؛
- compact floating action group؛
- optional top toolbar روی rich hero background.

## 13.3 مکان‌های نامناسب

- body text cards متعدد؛
- settings list؛
- transaction table؛
- support messages؛
- همه server rows؛
- همه Store cards.

## 13.4 Glass Recipe Dark

- base surface `#101712` با 68–78% opacity؛
- blur متناسب performance class؛
- 1dp white/emerald-tinted hairline با opacity بسیار پایین؛
- shadow نرم؛
- selected item Emerald؛
- Gold فقط premium badge.

## 13.5 Accessibility Fallback

اگر:

- Reduce Transparency؛
- device performance پایین؛
- contrast insufficient؛
- blur unsupported/expensive

بود، Glass باید به opaque elevated surface تبدیل شود بدون تغییر hierarchy.

---

# 14. Motion Language

Motion Ganj باید حس «precision + confidence» داشته باشد، نه bounce کودکانه.

| Motion | Duration | Behavior |
|---|---:|---|
| press feedback | 90–140ms | scale/tonal |
| micro state | 140–190ms | ease-out |
| chip/tab | 180–240ms | smooth |
| content transition | 220–320ms | fade/slide subtle |
| sheet | 300–420ms | spring low bounce |
| connect morph | 450–700ms | staged |
| success settle | 300–500ms | glow→stable |

## 14.1 Connect Motion

Disconnected:

- static calm ring؛
- tiny breathing highlight اختیاری بسیار کم.

Preparing:

- ring progressive؛
- label stage update.

Connecting:

- Emerald arc؛
- no infinite aggressive spinner؛
- subtle radial light.

Connected:

- one success haptic؛
- glow settles within ~500ms؛
- no permanent pulsing battery-heavy effect.

Reconnecting:

- warning/jade transition؛
- informative text؛
- no red unless failure final.

## 14.2 Reduce Motion

- morph → fade؛
- spring → short linear/ease؛
- no continuous decorative animation.

---

# 15. Haptic Language

- selection: server/chip؛
- light impact: Connect press؛
- success: VPN connected / purchase completed؛
- warning: network change requiring attention؛
- error: final failure only؛
- destructive confirmation: controlled stronger feedback.

ممنوع:

- haptic در هر scroll؛
- repeated reconnect haptic loop؛
- haptic برای تبلیغ/marketing.

---

# 16. Iconography

## 16.1 Style

- geometric؛
- rounded joins؛
- 24×24 base grid؛
- stroke 1.8–2.0dp؛
- optical consistency؛
- filled variant فقط selected/important state.

## 16.2 Brand Icon Accent

- normal icons: neutral/emerald؛
- premium icons: small Gold accent؛
- destructive: danger؛
- info: jade؛
- blue icon set ممنوع.

## 16.3 Country Flags

Flag تنها identifier نباشد؛ Country name همیشه کنار آن باشد.

---

# 17. Buttons

## 17.1 Primary Button

- height: 52–56dp؛
- Emerald fill؛
- text سفید یا near-black بر اساس contrast token؛
- radius 16–18dp؛
- pressed state tonal darker/lighter؛
- loading indicator داخل button بدون width jump.

## 17.2 Secondary Button

- neutral elevated/outlined؛
- Emerald text/icon؛
- no Gold fill.

## 17.3 Premium CTA

در Store فقط:

- Emerald base + thin Gold accent یا Gold outline؛
- نباید full metallic gold button باشد مگر campaign خاص و review شده.

## 17.4 Destructive

- danger tone؛
- confirmation برای revoke/delete؛
- Gold هرگز برای destructive استفاده نشود.

---

# 18. Cards

Cardها باید content-first باشند.

## 18.1 Standard Card

- neutral surface؛
- 18–22dp radius؛
- padding 16–20dp؛
- optional subtle border؛
- no gradient by default.

## 18.2 Active Service Card

- neutral surface؛
- Emerald status strip/dot؛
- plan name clear؛
- expiry prominent؛
- Premium badge Gold کوچک.

## 18.3 Premium Plan Card

- neutral/dark surface؛
- 1dp Gold rim یا top accent؛
- Gold badge؛
- CTA همچنان Emerald؛
- no full gold background.

## 18.4 Free Card

- neutral؛
- no fake gray/degraded look؛
- upgrade opportunity واضح ولی محترمانه.

---

# 19. Chips, Filters & Segmented Controls

Default:

- neutral surface؛
- text secondary؛
- subtle border.

Selected:

- Emerald muted background؛
- Emerald text/icon؛
- optional check.

Premium filter:

- Gold tiny crown/gem mark، نه gold fill.

Minimum touch target: 48dp interactive area.

---

# 20. Bottom Navigation

پنج Tab:

1. Home؛
2. Servers؛
3. Connect؛
4. Store؛
5. Account.

## 20.1 Visual

- floating/elevated glass surface مجاز؛
- height حدود 68–76dp + safe inset؛
- selected icon/text Emerald؛
- unselected neutral؛
- center Connect می‌تواند visual prominence بیشتری داشته باشد ولی همچنان Tab semantic بماند؛
- center button نباید giant Gold orb شود.

## 20.2 Connect Tab

Disconnected:

- Emerald outline/mark.

Connected:

- Emerald bright state + small success indicator.

Premium user:

- Gold فقط micro rim/badge؛ نه تغییر کامل center control.

---

# 21. App Bar / Header

- large title فقط صفحه‌هایی که hierarchy نیاز دارد؛
- sticky collapse برای Server/Store lists؛
- Logo در همه headerها ممنوع؛
- Search و actions واضح؛
- transparent header روی hero فقط اگر contrast کامل باشد.

---

# 22. Splash Screen

## 22.1 هدف

Brand recognition بدون کند کردن startup.

## 22.2 Design

Dark:

- `#070A08` background؛
- logo centered؛
- subtle emerald ambient light؛
- Gold logo highlight خود asset کافی است؛
- no extra particles.

Light:

- warm ivory؛
- logo centered؛
- minimal.

## 22.3 Timing

- از Splash API Android استفاده شود؛
- custom delay برای نمایش Logo ممنوع؛
- startup باید به‌خاطر animation معطل نشود.

---

# 23. Onboarding

حداکثر 2–3 مرحله ارزش‌محور:

1. اتصال امن و ساده؛
2. سرویس‌های خودت از Ganj/Telegram؛
3. Free/Premium/Smart Connect بسته به Product policy.

نباید:

- 6–8 صفحه معرفی؛
- permissionهای غیرضروری upfront؛
- privacy claimهای مبهم؛
- fake speed/security promises.

CTA Primary Emerald.

Gold فقط در visual illustration/brand detail.

---

# 24. Authentication UI

## 24.1 Guest

Guest path باید کم‌اصطکاک باشد.

## 24.2 Telegram Login

- Telegram branding لازم است ولی صفحه نباید آبی شود؛
- Telegram logo official color می‌تواند فقط در خود provider button/icon ظاهر شود چون متعلق به Provider است، نه Brand Ganj؛
- surrounding UI Neutral/Emerald باقی می‌ماند.

## 24.3 Security States

- loading؛
- callback received؛
- verification؛
- conflict؛
- expired login؛
- retry؛
- cancellation.

Error copy نباید technical JWT/PKCE terms را به کاربر عادی نشان دهد.

---

# 25. Home Screen — Final Specification

## 25.1 Hierarchy

1. account/service status؛
2. connection summary / quick connect؛
3. remaining subscription؛
4. selected/recommended server؛
5. quick actions؛
6. contextual banner/maintenance.

## 25.2 Hero Card

- neutral/emerald depth؛
- service plan؛
- expiry؛
- connection state؛
- CTA؛
- Premium Gold micro accent.

## 25.3 Quick Actions

حداکثر 3–4:

- Servers؛
- My Services؛
- Renew؛
- Support/Speed Test بسته به state.

Avoid dashboard clutter.

---

# 26. Connect Screen — Core Product Moment

این صفحه باید قوی‌ترین هویت بصری App را داشته باشد.

## 26.1 Layout

- top: service/server context؛
- center: Connect Control؛
- below: status + latency/duration؛
- bottom: secondary controls.

## 26.2 Connect Control Size

- compact phone: 176–188dp؛
- standard phone: 196–212dp؛
- large phone: 216–228dp.

باید responsive باشد، نه fixed giant circle.

## 26.3 Disconnected

- graphite/neutral center؛
- Emerald ring؛
- Gold hidden unless premium label؛
- text «اتصال» / localized equivalent.

## 26.4 Connecting

- Emerald staged ring؛
- stage copy:
  - «بررسی شبکه»؛
  - «انتخاب بهترین مسیر»؛
  - «ایجاد اتصال امن».

## 26.5 Connected

- Emerald bright success؛
- soft radial glow؛
- duration؛
- country/server؛
- latency؛
- disconnect affordance clear.

## 26.6 Reconnecting

- Jade/amber secondary cue؛
- «در حال بازیابی اتصال…»؛
- no panic red.

## 26.7 Failed

- subtle danger state؛
- Retry؛
- Change server؛
- Diagnostics/Support.

---

# 27. Servers Screen

## 27.1 Header

- title؛
- search؛
- Smart Connect shortcut؛
- filter/sort.

## 27.2 Row

Minimum 64–72dp.

Fields:

- flag؛
- country/server label؛
- premium/free marker؛
- latency؛
- capacity/availability hint؛
- favorite.

## 27.3 Latency Colors

از gradient رنگی شدید پرهیز شود.

- good: Emerald/Jade؛
- medium: amber neutral؛
- poor: danger muted؛
- unavailable: neutral disabled.

## 27.4 Premium Server

- Gold gem/crown 14–16dp؛
- no gold row background.

## 27.5 Smart Recommendation

Recommended row می‌تواند:

- Emerald muted surface؛
- small «پیشنهادی» badge؛
- explanation کوتاه مثل «پینگ بهتر».

---

# 28. Store Screen

## 28.1 هدف

Premium value بدون فشار تبلیغاتی آزاردهنده.

## 28.2 Plan Cards

- neutral backgrounds؛
- compare-friendly؛
- price large/readable؛
- period clear؛
- features concise؛
- recommended plan Gold outline/badge؛
- CTA Emerald.

## 28.3 Premium Visual Language

Gold برای:

- «پیشنهاد ویژه»؛
- VIP/Premium label؛
- annual saving؛
- subtle edge.

نه:

- full gold surface؛
- golden body text؛
- blinking discount.

## 28.4 Billing States

- Loading؛
- Pending؛
- Purchased؛
- Restored؛
- Error؛
- Grace؛
- Expired؛
- Refund/revoke.

هر state UI باید با backend truth sync باشد.

---

# 29. My Services

## 29.1 Service Card

- plan name؛
- status؛
- expiry / remaining time؛
- device slots؛
- country/server context optional؛
- Connect CTA؛
- Renew/Upgrade.

## 29.2 Status Visuals

Active:

- Emerald indicator.

Expiring:

- amber/gold warning but text explains remaining days.

Expired:

- neutral/danger limited.

Premium:

- Gold badge only.

---

# 30. Account Screen

Sections:

- identity؛
- Telegram link status؛
- My Services؛
- devices؛
- transactions؛
- privacy؛
- notifications؛
- support؛
- diagnostics؛
- settings؛
- about؛
- logout؛
- delete account.

Style:

- list sections؛
- neutral surfaces؛
- destructive items danger؛
- no Gold overload.

---

# 31. Device Management

Device row:

- device name/model؛
- current device label؛
- last active approximate؛
- status؛
- revoke action.

Current device:

- Emerald `این دستگاه` chip.

Premium slot info:

- Gold tiny capacity accent.

Revoke:

- danger؛
- confirmation؛
- server-side truth.

---

# 32. Direct Wallet UI

Wallet متعلق به Direct flavor است.

## 32.1 Balance

- number `text.primary` یا Emerald؛
- coin/wallet accent Gold؛
- full Gold balance card ممنوع.

## 32.2 Transactions

- neutral list؛
- credit Emerald؛
- debit neutral/primary text با sign؛
- refund/info Jade؛
- failed Danger؛
- no confetti for every transaction.

## 32.3 Smart Banking Verification

- separate payment flow؛
- receipt/verification status clear؛
- no banking permission iconography in VPN Home؛
- user باید بداند verification متعلق به payment است، نه VPN traffic.

---

# 33. Speed Test UI

- controlled endpoint؛
- big readable Download/Upload/Ping؛
- chart minimal؛
- Emerald/Jade line؛
- Gold فقط benchmark/best indicator؛
- no rainbow speedometer.

Animation باید performance safe باشد.

---

# 34. Support & Ticket UI

- calm neutral interface؛
- chat/list readability مهم‌تر از Brand flourish؛
- ticket status chips semantic؛
- attachment clear؛
- diagnostics consent explicit؛
- no Glass behind long text.

---

# 35. Bug Report & Diagnostics UI

Flow:

1. describe problem؛
2. optional screenshot/log consent؛
3. diagnostic scope summary؛
4. submit؛
5. report ID/status.

Privacy copy باید توضیح دهد چه چیزهایی ارسال می‌شود و چه چیزهایی ارسال نمی‌شود.

---

# 36. Notifications UI

Categories:

- service/expiry؛
- billing؛
- security؛
- maintenance؛
- support؛
- marketing.

Marketing toggle مستقل و clear.

Security/payment notifications نباید visually شبیه ad باشند.

---

# 37. Settings Screen

گروه‌ها:

- Appearance؛
- Language؛
- VPN behavior؛
- Notifications؛
- Privacy؛
- Diagnostics؛
- About.

Appearance:

- System؛
- Light؛
- Dark.

Dynamic brand color در MVP ارائه نشود.

---

# 38. Appearance Modes

## 38.1 Dark

Dark حالت Signature Ganj است:

- near-black green canvas؛
- Emerald action؛
- Gold accents؛
- high contrast off-white text.

## 38.2 Light

Light باید premium و تمیز باشد، نه سبز کم‌رنگ در همه جا:

- warm ivory/white؛
- Emerald buttons؛
- Gold accents؛
- subtle pale-green selected containers.

## 38.3 System

Default recommendation: `System`.

---

# 39. Free vs Premium Visual Differentiation

هدف: Premium ارزشمند باشد بدون تحقیر Free.

Free:

- neutral + Emerald standard؛
- no intentionally ugly gray theme.

Premium:

- Gold micro-accent؛
- premium badge؛
- advanced features unlocked؛
- richer but restrained hero treatment.

نباید کل Premium app طلایی شود.

---

# 40. Status & Semantic Color Rules

چون Brand اصلی Green است، status فقط با رنگ تفکیک نمی‌شود.

Connected:

- green + shield/check + label.

Active Subscription:

- green dot + «فعال».

Premium:

- Gold diamond/crown + label.

Warning:

- amber icon + text.

Error:

- red/coral icon + text.

Info:

- Jade icon + text.

---

# 41. Error UX

Errorها به سه سطح:

## Inline

- form validation؛
- recoverable small issue.

## Screen State

- server list unavailable؛
- store unavailable؛
- auth failed.

## Blocking

- mandatory update؛
- account disabled؛
- security failure requiring re-auth.

Error copy باید:

- human readable؛
- actionable؛
- بدون stack/JWT/provider jargon؛
- error code فقط در Details/Support.

---

# 42. Empty States

هر Empty State:

- کوتاه؛
- icon/illustration ساده؛
- reason؛
- CTA.

Examples:

- هیچ سرویس فعالی نیست → مشاهده پلن‌ها؛
- Favorite ندارد → «سرورهای موردعلاقه‌ات اینجا نمایش داده می‌شوند»؛
- Ticket ندارد → «هنوز درخواستی ثبت نکرده‌ای».

Gold illustration only if related to premium/reward.

---

# 43. Loading & Skeleton

- skeleton neutral؛
- shimmer بسیار subtle؛
- Emerald shimmer ممنوع در همه lists؛
- layout shift کم؛
- button loading in-place؛
- VPN connection از generic skeleton استفاده نمی‌کند؛ state machine خودش را دارد.

---

# 44. Offline State

App باید distinction داشته باشد بین:

- Control API offline؛
- internet offline؛
- VPN reconnecting؛
- server unavailable.

همه به یک «خطا در اینترنت» تقلیل داده نشوند.

Offline screen:

- neutral؛
- Jade/Amber status؛
- retry؛
- cached content اگر امن/معتبر است.

---

# 45. Maintenance State

- signed runtime config-driven؛
- short explanation؛
- estimated window only if source trustworthy؛
- support link؛
- no panic styling.

Gold/amber می‌تواند maintenance accent باشد، نه full background.

---

# 46. Forced Update UI

- version reason؛
- security/compatibility context؛
- single primary update CTA؛
- Gold only if brand decoration؛
- danger only if critical security phrasing لازم است؛
- no dismiss اگر policy truly forced.

---

# 47. Accessibility

## 47.1 Touch Targets

حداقل interactive target `48dp × 48dp`.

Primary buttons: ترجیحاً 52–56dp height.

## 47.2 TalkBack

- meaningful content descriptions؛
- decorative Logo/icon hidden if redundant؛
- connect state announced کنترل‌شده؛
- row semantics aggregate شود تا TalkBack تکه‌تکه و آزاردهنده نشود.

## 47.3 Contrast

- WCAG AA target؛
- color-only state ممنوع؛
- Gold-on-white text محدود.

## 47.4 Font Scale

- 200% QA؛
- no critical clipping؛
- card height flexible؛
- no fixed text box.

## 47.5 Motion/Transparency

- Reduce Motion؛
- Reduce Transparency equivalent behavior؛
- blur fallback؛
- no seizure-risk flashing.

---

# 48. RTL / LTR

## 48.1 Persian

- LayoutDirection RTL؛
- navigation logical mirror where appropriate؛
- back system behavior native؛
- chevrons directional؛
- text alignment semantic.

## 48.2 Bidi Isolation

این مقادیر باید LTR-isolated باشند:

- IP؛
- UUID؛
- VLESS/VMess/Trojan names؛
- version؛
- bandwidth units؛
- transaction/order IDs.

## 48.3 Numbers

قیمت، زمان، speed، latency باید locale-safe format شوند؛ تصمیم Persian/Latin digits per locale UX باید consistent باشد.

---

# 49. Responsive & Adaptive Layout

Target اصلی phone است، اما layout نباید روی width خاص قفل شود.

Breakpoints پیشنهادی:

- Compact `< 600dp`؛
- Medium `600–839dp`؛
- Expanded `≥ 840dp`.

Phone:

- bottom navigation.

Large/foldable:

- navigation rail قابل بررسی؛
- two-pane Servers/Details؛
- max content width برای readability.

No tablet-specific feature requirement برای MVP، اما UI نباید کشیده و خراب شود.

---

# 50. System Bars & Edge-to-Edge

- edge-to-edge؛
- content safe insets؛
- transparent system bars where suitable؛
- icon appearance بر اساس luminance؛
- no content under gesture area without padding؛
- bottom nav handles navigation inset.

---

# 51. Predictive Back

- Android native predictive back؛
- no custom back hijacking؛
- checkout/auth flows state-safe؛
- destructive unsaved state confirmation فقط وقتی واقعاً لازم است.

---

# 52. Forms

- label همیشه visible/understandable؛
- placeholder جای label نیست؛
- error inline؛
- keyboard type مناسب؛
- autofill فقط non-sensitive supported fields؛
- password/secret raw fields عملاً در user VPN config وجود ندارند؛
- OTP اگر future flow باشد accessibility-safe.

---

# 53. Search

Servers search:

- sticky؛
- clear button؛
- immediate filtering؛
- Persian/English country names؛
- transliteration tolerance optional؛
- empty result helpful.

Search bar neutral؛ focus ring Emerald.

---

# 54. Data Visualization

Charts فقط وقتی decision را بهتر می‌کنند.

Allowed:

- usage/quota؛
- speed test؛
- connection history aggregate local if product decides؛
- Admin analytics خارج mobile app.

Colors:

- Emerald primary series؛
- Gold secondary/premium benchmark؛
- Jade tertiary؛
- neutral grid.

Avoid blue default chart palette.

---

# 55. Illustrations

Style:

- minimal 3D/2.5D یا vector hybrid؛
- dark emerald/graphite؛
- small gold highlights؛
- simple lighting؛
- no generic stock cyber shields everywhere.

Use cases:

- onboarding؛
- empty state؛
- Store hero؛
- error/maintenance limited.

---

# 56. Microcopy — Persian Tone

Tone:

- کوتاه؛
- طبیعی؛
- مطمئن؛
- غیررباتیک؛
- بدون اصطلاح فنی غیرضروری.

Examples:

بد:

- «Establishing secure VLESS tunnel…»

خوب:

- «در حال ایجاد اتصال امن…»

بد:

- «Authentication token expired»

خوب:

- «برای ادامه دوباره وارد حسابت شو.»

بد:

- «No entitlement found»

خوب:

- «سرویس فعالی روی این حساب پیدا نکردیم.»

CTAها:

- «اتصال»؛
- «تلاش دوباره»؛
- «انتخاب سرور»؛
- «تمدید سرویس»؛
- «مشاهده پلن‌ها»؛
- «اتصال تلگرام».

---

# 57. Security UX

Security نباید با ترساندن کاربر نمایش داده شود.

## 57.1 Sensitive Actions

- revoke device؛
- logout all؛
- delete account؛
- billing adjustment not user side؛
- privacy consent changes.

نیازمند confirmation متناسب.

## 57.2 No Secret Exposure

UI هرگز نشان ندهد:

- raw VPN profile؛
- server UUID/password؛
- access token؛
- refresh token؛
- purchase token؛
- server secret.

## 57.3 Screenshot Protection

فقط روی screenهایی که واقعاً sensitive هستند، نه کل App بدون دلیل؛ UX و support را خراب نکنیم.

---

# 58. Privacy UX

Consentها purpose-specific:

- Analytics؛
- Crash diagnostics؛
- Marketing؛
- Support diagnostics.

Toggleها واضح و مستقل.

نباید dark pattern وجود داشته باشد.

---

# 59. Ads UI Policy — اگر Free Ads فعال شوند

- ads داخل Tunnel/traffic ممنوع؛
- DNS/ad injection ممنوع؛
- system VPN permission screen هیچ ad ندارد؛
- Connect critical state هیچ interstitial مزاحم ندارد؛
- ad card clearly labeled؛
- Gold/Premium visuals برای disguise ad استفاده نشود؛
- frequency cap؛
- consent/region policy.

---

# 60. Component State Matrix

هر component interactive باید stateهای زیر را تعریف کند:

- default؛
- pressed؛
- focused؛
- selected؛
- disabled؛
- loading؛
- error اگر applicable.

Color alone کافی نیست؛ shape/icon/label/opacity نیز باید state را منتقل کند.

---

# 61. Performance Budget for UI

## 61.1 Goals

- smooth 60fps baseline؛
- 120Hz compatible؛
- connection animation بدون block networking؛
- blur/shader adaptive؛
- no perpetual heavy animation؛
- list recycling/lazy components؛
- image asset optimized.

## 61.2 Glass Budget

در هر screen تعداد blur layers محدود باشد.

Low-end device:

- opaque fallback؛
- reduced glow؛
- simpler motion.

---

# 62. Battery-Safe UI

ممنوع:

- infinite pulse در Connected؛
- high-frequency animated charts؛
- background particle systems؛
- ping refresh بسیار سریع در background.

Server ping:

- foreground only؛
- rate-limited؛
- cached state visible.

---

# 63. Localization Architecture

هیچ user-facing string در Compose hardcoded نماند.

Structure:

```text
res/values/strings.xml        → English/default
res/values-fa/strings.xml     → Persian
```

در صورت انتخاب default فارسی، ساختار locale strategy باید صریح و تست‌شده باشد.

Format args برای:

- price؛
- duration؛
- latency؛
- GB/MB؛
- days remaining؛
- date/time.

---

# 64. Current Implementation Migration — مهم

Phase 7 branch `android-ui-accessibility-v2` در حال حاضر `GanjBlue` و palette آبی دارد. **این implementation قبل از merge نهایی باید با این Brand System همگام شود.**

موارد لازم:

- حذف `GanjBlue` به‌عنوان Primary؛
- تعریف `GanjEmerald`, `GanjGold` و neutral tokens؛
- Light/Dark scheme جدید؛
- selected navigation Emerald؛
- premium accent Gold؛
- warning/danger/info semantic جدا؛
- عدم استفاده از default blue links؛
- UI screenshots review.

این تغییر Brand migration است و نباید Device/Auth/VPN logic را تغییر دهد.

---

# 65. Machine Design Tokens

`design/tokens.json` باید با این سند sync باشد.

حداقل families:

```text
color.dark.*
color.light.*
color.brand.emerald.*
color.brand.gold.*
color.semantic.*
space.*
radius.*
motion.*
touch.*
```

Tokenهای `brandPrimary = blue` legacy محسوب می‌شوند و باید حذف/جایگزین شوند.

---

# 66. Component Library Target

پیشنهاد package structure:

```text
ui/theme/
  GanjColors.kt
  GanjTypography.kt
  GanjShapes.kt
  GanjTheme.kt
ui/components/
  GanjButton.kt
  GanjCard.kt
  GanjGlassSurface.kt
  GanjBadge.kt
  GanjChip.kt
  GanjBottomBar.kt
  GanjTopBar.kt
  GanjStateView.kt
  GanjDialog.kt
  GanjSheet.kt
  GanjServerRow.kt
  GanjPlanCard.kt
  GanjServiceCard.kt
  GanjConnectControl.kt
```

UI monolith نباید تا Production در یک فایل بزرگ باقی بماند.

---

# 67. Screen Package Target

```text
ui/screens/home/
ui/screens/servers/
ui/screens/connect/
ui/screens/store/
ui/screens/account/
ui/screens/services/
ui/screens/auth/
ui/screens/settings/
ui/screens/support/
```

State/logic باید از visual components جدا باشد.

---

# 68. Design QA Matrix

هر Release Candidate باید حداقل این matrix را داشته باشد:

## Theme

- Dark؛
- Light؛
- System.

## Locale

- fa-IR RTL؛
- en LTR؛
- long text stress locale.

## Font Scale

- 100%؛
- 130%؛
- 160%؛
- 200%.

## Screen Width

- small phone؛
- standard؛
- large؛
- foldable/tablet sanity.

## Accessibility

- TalkBack؛
- contrast؛
- touch targets؛
- reduce motion؛
- high contrast/transparency fallback.

## VPN States

- disconnected؛
- preparing؛
- connecting؛
- connected؛
- reconnecting؛
- failed.

## Commerce States

- guest؛
- free؛
- premium؛
- pending purchase؛
- grace؛
- expired؛
- refunded.

---

# 69. Screenshot Acceptance Checklist

قبل از Final UI merge:

- هیچ صفحه اصلی آبی dominant نباشد؛
- Gold کمتر و هوشمندتر از Emerald استفاده شده باشد؛
- Neutral area غالب باشد؛
- Logo درست و بدون stretch؛
- text clipping صفر در مسیرهای اصلی؛
- Persian RTL طبیعی؛
- Bottom nav safe؛
- Connect state واضح؛
- Store نه شبیه casino؛
- Premium نه شبیه full gold skin؛
- Free نه عمداً زشت؛
- light mode واقعاً طراحی شده باشد؛
- dark mode فقط inversion نباشد؛
- accessibility pass؛
- screenshots روی device واقعی review شود.

---

# 70. UX Acceptance Criteria — Home

Home Done نیست مگر:

- user ظرف چند ثانیه وضعیت سرویس را بفهمد؛
- action اصلی واضح باشد؛
- expiry readable باشد؛
- loading/error/empty/offline وجود داشته باشد؛
- TalkBack hierarchy درست باشد؛
- RTL و LTR pass؛
- no hardcoded English.

---

# 71. UX Acceptance Criteria — Connect

Connect Done نیست مگر:

- UI state با runtime state یکی باشد؛
- connecting fake success نمایش ندهد؛
- reconnect قابل فهم باشد؛
- disconnect واقعی و واضح باشد؛
- profile/credential دیده نشود؛
- animation battery-safe باشد؛
- failure actionable باشد؛
- accessibility live region کنترل‌شده باشد.

---

# 72. UX Acceptance Criteria — Servers

- search/filter؛
- free/premium distinction؛
- latency readable؛
- unavailable reason؛
- favorite state؛
- Smart Connect recommendation؛
- no arbitrary config import؛
- list smooth؛
- row >= 64dp visually and >=48dp touch targets.

---

# 73. UX Acceptance Criteria — Store

- pricing واضح؛
- renewal terms؛
- current plan؛
- pending state؛
- restore؛
- provider error؛
- purchase state server-truth driven؛
- Gold restrained؛
- no dark patterns.

---

# 74. UX Acceptance Criteria — Account

- linked identity؛
- My Services؛
- devices؛
- support؛
- privacy؛
- logout؛
- delete account؛
- destructive actions confirmations؛
- no sensitive data display.

---

# 75. Do / Don’t Examples

## Color

DO:

- graphite card + Emerald CTA + Gold premium badge.

DON’T:

- gold background + green text + gold border + glow all together.

## Connect

DO:

- subtle Emerald ring with clear state.

DON’T:

- giant metallic gold power button.

## Store

DO:

- Gold recommendation rim.

DON’T:

- three full-gold plan cards.

## Navigation

DO:

- glass/neutral bar + Emerald selected item.

DON’T:

- blue iOS-style selected tabs.

## Premium

DO:

- small diamond/gold identity.

DON’T:

- change entire theme to gold.

---

# 76. Brand Consistency Rules

یک screen Ganj باید حتی بدون Logo قابل تشخیص باشد از طریق:

- Emerald interaction color؛
- graphite/warm neutral surfaces؛
- refined Gold micro-accent؛
- shape system؛
- typography؛
- Connect visual language؛
- motion precision.

اگر تنها راه تشخیص Brand، گذاشتن Logo بزرگ در هر screen باشد، Design System شکست خورده است.

---

# 77. Marketing vs Product UI

Marketing می‌تواند:

- Logo 3D بزرگ؛
- Metallic Gold بیشتر؛
- dramatic lighting؛
- richer gradients

داشته باشد.

Product UI باید:

- calmer؛
- more neutral؛
- higher readability؛
- less decoration؛
- interaction-first

باشد.

نباید Visual زبان Poster مستقیماً به تمام App منتقل شود.

---

# 78. App Store / Play Listing Visual Direction

Screenshots:

- dark signature background؛
- Emerald hero areas؛
- Gold headline accents محدود؛
- actual UI، نه fake mockup متفاوت؛
- short benefit headline؛
- no claims without evidence.

Feature Graphic:

- Logo 3D؛
- Dark emerald/graphite؛
- controlled Gold lighting؛
- minimal text.

---

# 79. Future iOS Note

iOS Post-MVP است. در آینده Brand tokens semantic حفظ می‌شوند ولی component behavior باید Native iOS باشد.

Android UI نباید الان طوری نوشته شود که صرفاً iOS clone شود تا بعداً «مشترک» شود.

---

# 80. Final Brand Lock

از تاریخ تصویب این سند:

1. **Emerald Green رنگ Primary برند Ganj VPN است.**
2. **Gold رنگ Premium/Signature Accent است، نه رنگ غالب صفحه.**
3. **Neutral Dark/Warm Light فضای اصلی UI را تشکیل می‌دهد.**
4. **Blue به‌عنوان Brand Primary حذف است.**
5. **Liquid Glass محدود، functional و performance-aware است.**
6. **Material 3 Expressive رفتار Native Android را هدایت می‌کند، نه palette پیش‌فرض آن.**
7. **UI فعلی Foundation است و باید طبق این سند migration شود.**
8. **تمام user-facing strings باید localized باشند.**
9. **RTL، Accessibility و large-font بخشی از Definition of Done هستند.**
10. **Premium بودن باید از precision، hierarchy، material و spacing بیاید؛ نه از مصرف زیاد Gold.**

---

# 81. منابع مرجع

منابع رسمی که اصول این سند با آن‌ها تطبیق داده شده:

- Apple Human Interface Guidelines — Design Principles  
  `https://developer.apple.com/design/human-interface-guidelines/design-principles`
- Apple Human Interface Guidelines — Materials / Liquid Glass  
  `https://developer.apple.com/design/human-interface-guidelines/materials`
- Apple Human Interface Guidelines — Color  
  `https://developer.apple.com/design/human-interface-guidelines/color`
- Android Developers — Material 3 in Compose / Material 3 Expressive  
  `https://developer.android.com/develop/ui/compose/designsystems/material3`
- Android Developers — Accessibility  
  `https://developer.android.com/guide/topics/ui/accessibility/apps`
- Android Core App Quality / adaptive quality guidance  
  `https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality`

---

# 82. Engineering Handoff Note

هر AI/Developer که Phase 7 UI یا هر Screen جدید را ادامه می‌دهد، قبل از کدنویسی باید به‌ترتیب بخواند:

```text
docs/00-CURRENT_PRODUCT_SCOPE.fa.md
docs/15-android-ui-ux-brand-system.fa.md
docs/PROJECT_BIBLE.fa.md
docs/IMPLEMENTATION_BACKLOG.fa.md
design/tokens.json
apps/android/.../ui/
```

سپس implementation فعلی را با این سند compare کند.

هر PR UI باید در Description صریحاً اعلام کند:

- کدام Screen/Component migration شد؛
- کدام tokenها استفاده شدند؛
- Dark/Light وضعیت؛
- fa/en وضعیت؛
- RTL وضعیت؛
- Accessibility evidence؛
- screenshot/device evidence؛
- performance/motion impact؛
- چه بخش‌هایی هنوز Foundation هستند.

---

# 83. خلاصه یک‌خطی برای تیم طراحی

> **Ganj VPN = فضای آرام Graphite/Warm Neutral + Emerald برای اعتماد و Action + Gold بسیار کنترل‌شده برای Premium Value؛ مدرن، Native Android، دسترس‌پذیر و بدون آبیِ برند.**
