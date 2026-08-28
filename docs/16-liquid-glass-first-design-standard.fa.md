# Ganj VPN — Liquid Glass-First Android Design Standard

> **وضعیت:** Mandatory / Canonical Visual Interaction Standard  
> **Scope:** تمام UI/UX اپلیکیشن Android Ganj VPN  
> **اولویت:** این سند در موضوع Liquid Glass، Material، Depth، Motion و Surface hierarchy مکمل و مفسر اجباری `15-android-ui-ux-brand-system.fa.md` است.  
> **مالک تصمیم:** Product / Design System  
> **قاعده تعارض:** هر Mockup، Theme، Token، Component یا Implementation قدیمی که با این سند تعارض داشته باشد باید به این استاندارد مهاجرت کند.

---

# 0. قانون شماره یک طراحی Ganj VPN

## **GANJ VPN MUST BE DESIGNED LIQUID-GLASS-FIRST ACROSS THE ENTIRE APPLICATION.**

## **تمام اپلیکیشن، از Splash تا Home، Server List، Connect، Store، Account، Settings، Support و تمام Sheet/Dialog/Navigationها باید از یک زبان طراحی مدرن، لایه‌ای، سیال و Liquid Glass پیروی کند.**

این قانون به این معنی نیست که تمام Pixelهای اپ Blur یا Transparent شوند. برعکس، یک Liquid Glass حرفه‌ای دارای **دو لایه روشن و مشخص** است:

1. **Content Layer** — محتوا، اطلاعات، متن، لیست و Cardهایی که باید خوانا و پایدار باشند؛
2. **Functional Glass Layer** — Navigation، Control، Floating Action، Toolbar، Search، Sheet handle، segmented control، primary interaction و لایه‌های تعاملی که روی Content شناورند.

بنابراین:

> **کل محصول باید از منطق Liquid Glass پیروی کند، اما هر Card نباید Glass باشد.**

این تفاوت بسیار مهم است. اگر همه‌چیز Blur شود، نتیجه مدرن نیست؛ نتیجه شلوغ، کم‌کنتراست، پرمصرف و غیرحرفه‌ای است.

Liquid Glass در Ganj باید یک **Design Language** باشد، نه یک Effect تزئینی.

---

# 1. North Star

شخصیت تجربه نهایی:

> **Emerald Security + Refined Gold + Calm Neutral + Liquid Depth + Fluid Motion**

کاربر باید هنگام بازکردن اپ این حس را بگیرد:

- محصول نسل جدید است؛
- Interface زنده و responsive است؛
- کنترل‌ها روی محتوا شناورند؛
- عمق وجود دارد اما شلوغی نیست؛
- Premium است اما فریاد نمی‌زند؛
- امنیت را با آرامش نشان می‌دهد؛
- Interactionها نرم و فیزیکی‌اند؛
- هر صفحه بخشی از یک سیستم واحد است؛
- ظاهر Developer-like یا Material-default ندارد؛
- آبی iOS/Material هویت محصول نیست؛
- Gold برای ارزش و امضاست، نه تزئین عمومی؛
- Emerald رنگ هویت و Action است؛
- Neutralها بخش عمده canvas را می‌سازند.

---

# 2. تفسیر صحیح Liquid Glass برای Android

Ganj VPN یک Android App است. بنابراین نباید تلاش شود Apple API یا ظاهر iOS به‌صورت مصنوعی کپی شود.

چارچوب صحیح:

```text
Android Native Behavior
        +
Material 3 / M3 Expressive component semantics
        +
Ganj Emerald/Gold Brand
        +
Liquid Glass visual hierarchy
        +
Custom Compose material implementation
        =
Ganj Liquid Android UI
```

یعنی:

- Back behavior باید Android-native باشد؛
- Predictive Back باید حفظ شود؛
- System bars باید Android-native باشند؛
- Navigation semantics باید قابل انتظار باشد؛
- Material 3 Expressive برای shape/motion/component quality مبناست؛
- Visual Material اختصاصی Ganj روی آن ساخته می‌شود؛
- هیچ UI نباید صرفاً iPhone clone باشد.

---

# 3. پنج اصل اجباری Liquid Glass در Ganj

## 3.1 Hierarchy before decoration

Glass باید hierarchy بسازد.

کاربر باید در کمتر از یک ثانیه بفهمد:

- محتوا چیست؛
- Navigation کجاست؛
- Primary Action چیست؛
- وضعیت VPN چیست؛
- چه چیزی قابل لمس است.

اگر Glass باعث شود این موارد مبهم شوند، Glass اشتباه استفاده شده است.

## 3.2 Depth must be meaningful

هر elevation باید دلیل داشته باشد.

سه سطح اصلی:

```text
Level 0 — Canvas / Content
Level 1 — Elevated content / cards
Level 2 — Floating glass controls / navigation
Level 3 — Modal / sheet / transient focus
```

وجود Levelهای بیشتر فقط در Hero transitionهای محدود مجاز است.

## 3.3 Fluidity must communicate state

Motion باید تغییر state را نشان دهد؛ نه صرفاً زیبایی.

مثال:

```text
Disconnected
→ Preparing
→ Connecting
→ Connected
```

Connect control باید طی این مسیر morph شود، نه اینکه فقط رنگ عوض کند.

## 3.4 Glass must adapt to background

Glass یک رنگ ثابت نیمه‌شفاف نیست.

باید با توجه به:

- Light/Dark؛
- پیچیدگی Background؛
- میزان Contrast؛
- زیرلایه رنگی؛
- Accessibility setting؛
- Performance class

Opacity، Blur، Scrim و Border خود را تطبیق دهد.

## 3.5 Content remains readable

هیچ جلوه بصری حق ندارد readability را کاهش دهد.

Priority:

```text
Meaning
> Readability
> Interaction clarity
> Accessibility
> Performance
> Brand
> Decorative effect
```

---

# 4. Material Model رسمی Ganj

به‌جای یک `GlassCard` عمومی، چهار Material role تعریف می‌شود.

## 4.1 `Glass.Clear`

برای کنترل‌های کوچک روی background نسبتاً ساده.

Use:

- compact icon button؛
- mini status control؛
- floating search affordance در Hero؛
- decorative badge محدود.

ویژگی:

- transparency بالا؛
- blur متوسط؛
- border بسیار ظریف؛
- tint نزدیک به neutral؛
- هیچ متن طولانی روی آن قرار نمی‌گیرد.

نباید استفاده شود برای:

- فرم؛
- transaction row؛
- pricing details؛
- error message طولانی؛
- account information.

## 4.2 `Glass.Regular`

Material اصلی Navigation/Control.

Use:

- Bottom Navigation؛
- Top floating toolbar؛
- Search control؛
- segmented controls؛
- Filter capsule؛
- transient controls؛
- floating connection controls.

ویژگی:

- adaptive opacity؛
- blur متوسط؛
- subtle highlight؛
- soft inner/outer edge؛
- neutral tint؛
- selected state با Emerald emphasis.

## 4.3 `Glass.Dense`

برای سطح‌هایی که readability بیشتری نیاز دارند.

Use:

- Bottom Sheet header؛
- modal sheet؛
- connection details overlay؛
- permission explanation panel؛
- account quick panel؛
- server filter sheet.

ویژگی:

- opacity بیشتر؛
- blur بیشتر یا background suppression بیشتر؛
- contrast قوی‌تر؛
- border واضح‌تر اما نرم.

## 4.4 `Glass.Prominent`

فقط برای Primary momentهای محدود.

Use:

- Connect action؛
- Confirm purchase؛
- Activate Premium؛
- critical modal primary CTA.

رنگ:

- Emerald stained glass برای action؛
- Gold فقط برای Premium emphasis بسیار محدود.

نباید بیشتر از یک Prominent Glass action در viewport وجود داشته باشد مگر flow خاص و تاییدشده.

## 4.5 `Glass.OpaqueFallback`

برای:

- Reduce Transparency؛
- low-end GPU؛
- unsupported blur path؛
- high contrast mode؛
- battery saver؛
- problematic background.

این fallback باید همچنان همان Shape، spacing، hierarchy و motion را حفظ کند تا ظاهر اپ نشکند.

---

# 5. Liquid Glass Coverage Contract

این جدول تعیین می‌کند هر ناحیه چگونه باید از زبان Liquid استفاده کند.

| Area | Liquid Language | Actual Glass |
|---|---|---|
| Splash | Yes | محدود |
| Onboarding | Yes | controls/sheets |
| Login | Yes | actions/sheets |
| Home | Yes | nav + primary controls |
| Connect | Strongest | yes |
| Servers | Yes | search/filter/nav |
| Store | Yes | nav/filter/CTA |
| My Services | Yes | nav/action |
| Account | Yes | nav/actions |
| Settings | Yes | nav/toggles where suitable |
| Wallet | Yes | action/filter |
| Transactions | Yes | filter/nav, rows mostly solid |
| Support | Yes | composer/actions |
| Bug Report | Yes | controls, form content solid |
| Diagnostics | Yes | controls/status |
| Bottom Sheets | Yes | dense glass shell |
| Dialogs | Yes | dense/opaque adaptive |
| Toast/Snackbar | Yes | regular/dense glass |
| Bottom Navigation | Mandatory | regular glass |
| Primary CTA | Mandatory language | prominent glass where appropriate |

---

# 6. قانون Background و Canvas

## 6.1 Dark Canvas

Dark Mode نباید pure black در همه صفحات باشد.

هدف:

- Graphite-black؛
- subtle deep emerald undertone؛
- بسیار کم gradient؛
- بدون noise شدید؛
- بدون gold wash.

نمونه semantic direction:

```text
canvas.base        #080B09
canvas.elevated    #0E1310
canvas.deep        #050806
surface.primary    #121814
surface.secondary  #18211B
```

## 6.2 Light Canvas

Light Mode:

- Warm off-white؛
- ivory-neutral؛
- pure white فقط برای elevated surface لازم؛
- سبز tint بسیار کم؛
- Gold tint فقط promotional micro-surface.

Direction:

```text
canvas.base        #F7F6F1
canvas.elevated    #FCFBF7
surface.primary    #FFFFFF
surface.secondary  #F0F2EC
```

## 6.3 Background activity

Glass وقتی زیباست که چیزی برای refract/infuse وجود داشته باشد، اما background نباید شلوغ شود.

مجاز:

- یک soft radial Emerald halo؛
- gradient بسیار subtle؛
- abstract low-frequency shape؛
- connection aura؛
- server-country hero tint.

غیرمجاز:

- چند gradient اشباع؛
- animated particles دائمی؛
- neon grid؛
- shiny gold texture؛
- background image شلوغ پشت متن.

---

# 7. Color inside Liquid Glass

## 7.1 Emerald

Emerald رنگ اصلی stained glass و selection است.

Use:

- selected tab؛
- primary button background؛
- connected status؛
- progress؛
- active filter؛
- selected server؛
- switch on؛
- focus accent.

## 7.2 Gold

Gold نباید Primary Action عمومی باشد.

Use محدود:

- Premium badge؛
- recommended plan accent؛
- reward؛
- special status؛
- subtle ring؛
- logo moment؛
- subscription success detail؛
- exclusive promotional highlight.

## 7.3 Color infusion rule

Glass به‌صورت پیش‌فرض neutral است.

رنگ فقط زمانی وارد background Glass می‌شود که:

- state مهم باشد؛
- primary action باشد؛
- premium distinction ضروری باشد.

رنگ text/icon روی glass باید conservative باقی بماند.

---

# 8. Border, Highlight و Refraction

Glass حرفه‌ای فقط blur نیست.

هر Glass surface می‌تواند ترکیبی از این موارد داشته باشد:

- background blur؛
- saturation reduction/increase کنترل‌شده؛
- top-edge highlight؛
- inner hairline؛
- soft shadow؛
- subtle refraction shift؛
- adaptive scrim.

## 8.1 Border

در Dark:

- سفید با alpha پایین؛
- یا Emerald-neutral edge بسیار کم.

در Light:

- dark neutral با alpha بسیار پایین؛
- white highlight در top edge.

Gold border فقط Premium micro-state.

## 8.2 Highlight

Highlight نباید metallic شود.

هدف: حس نور روی glass، نه chrome.

## 8.3 Shadow

Shadow:

- wide؛
- soft؛
- low-opacity؛
- بدون black hard drop shadow.

---

# 9. Blur Specification

اعداد زیر Design target هستند و implementation باید با device capability تنظیم شود.

## `Clear`

- visual blur target: حدود 12–18dp equivalent؛
- transparency بالا؛
- content محدود.

## `Regular`

- blur target: حدود 18–28dp equivalent؛
- opacity adaptive.

## `Dense`

- blur target: حدود 24–36dp equivalent یا suppression معادل؛
- scrim قوی‌تر.

## `Prominent`

- blur target مشابه Regular/Dense؛
- stained brand tint؛
- stronger focus edge.

### مهم

عدد Blur یک contract مطلق pixel نیست. کیفیت و readability مهم‌تر است.

Implementation باید:

- density aware؛
- GPU aware؛
- API aware؛
- performance aware

باشد.

---

# 10. ممنوعیت Nested Glass

یکی از قوانین سخت:

> **Glass روی Glass به‌صورت عمومی ممنوع است.**

مثال بد:

```text
Glass Card
  → Glass Button
    → Glass Icon container
```

این ساختار باعث:

- muddy contrast؛
- expensive render؛
- unclear hierarchy؛
- visual noise

می‌شود.

به‌جای آن:

```text
Solid/standard content surface
  → floating Glass control
```

یا:

```text
Glass sheet shell
  → mostly solid/transparent content rows
```

---

# 11. Edge-to-Edge Contract

تمام Screenهای اصلی باید edge-to-edge طراحی شوند.

یعنی:

- content زیر system region امتداد بصری دارد؛
- Insets درست اعمال می‌شوند؛
- Floating Navigation روی content قرار می‌گیرد؛
- Background زیر Navigation ادامه دارد؛
- scroll می‌تواند زیر glass nav عبور کند؛
- readability با fade/scrim کنترل می‌شود.

نباید:

- پایین صفحه یک rectangle opaque بزرگ برای nav گذاشته شود؛
- Status bar با block رنگی جدا شود؛
- Content در box مرکزی کوچک محبوس شود.

---

# 12. Bottom Navigation — مهم‌ترین عنصر Glass

Bottom Navigation باید Signature component اپ باشد.

## 12.1 Form

- floating capsule/bar؛
- margin افقی؛
- rounded continuous form؛
- Regular Glass؛
- adaptive blur؛
- background content زیر آن قابل حس باشد؛
- safe inset رعایت شود.

## 12.2 Tabs

Home / Servers / Connect / Store / Account

Selected:

- Emerald indication؛
- slight shape fill؛
- icon/label clarity؛
- subtle motion.

Unselected:

- monochrome؛
- high readability؛
- no gold.

## 12.3 Connect tab

مرکز می‌تواند emphasis بیشتری داشته باشد اما نباید به floating button جدا و disconnected تبدیل شود.

State Connected می‌تواند:

- Emerald aura؛
- soft filled capsule؛
- tiny live indicator

داشته باشد.

---

# 13. Top App Bar / Toolbar

Default:

- content-first؛
- title روی canvas؛
- actions در Clear/Regular Glass؛
- هنگام scroll ممکن است toolbar به Regular Glass تبدیل شود.

Scroll behavior:

```text
Expanded transparent
→ content scrolls
→ compact glass toolbar appears
```

این transition باید نرم باشد.

---

# 14. Search

Search در Servers و سایر صفحات باید Liquid behavior داشته باشد.

Collapsed:

- floating search glass capsule.

Focused:

- expand smoothly؛
- background content de-emphasize؛
- keyboard transition طبیعی؛
- search results content layer خوانا.

نباید:

- search field solid gray Material-default باشد؛
- focus ناگهانی resize کند؛
- filter chips جدا از زبان glass باشند.

---

# 15. Chips / Segmented Controls / Filters

Default shell:

- Regular Glass group؛
- individual options بدون glass جداگانه.

Selected:

- Emerald stained fill؛
- subtle selected shape؛
- text contrast واضح.

Gold selected state فقط برای Premium filter/offer context مجاز است.

---

# 16. Buttons

## 16.1 Primary

- Emerald Prominent Glass یا opaque-high-emphasis fallback؛
- large readable label؛
- subtle depth؛
- press compression؛
- haptic.

## 16.2 Secondary

- Regular Glass یا tonal surface؛
- Emerald content accent.

## 16.3 Tertiary

- text/icon control؛
- glass only if floating over content.

## 16.4 Destructive

- red semantic؛
- no red-glass spectacle؛
- clear confirmation.

---

# 17. Cards

مهم:

> **Cardها به‌طور پیش‌فرض Glass نیستند.**

Card محتوایی باید:

- مات یا نیمه‌مات؛
- contrast بالا؛
- radius هماهنگ؛
- elevation کم؛
- neutral surface

باشد.

Glass Card فقط برای:

- Hero summary؛
- compact overlay؛
- ephemeral status؛
- promotional controlled module

است.

---

# 18. Sheets

Bottom Sheet باید یکی از بهترین نمایش‌های Liquid باشد.

Shell:

- Dense Glass؛
- top rounded geometry؛
- grab handle؛
- background de-emphasis؛
- content داخل sheet غالباً solid/neutral rows.

Interaction:

- spring natural؛
- velocity aware؛
- snap points محدود؛
- predictive back/dismiss سازگار؛
- focus restoration درست.

---

# 19. Dialogs

Dialog باید از Dense Glass یا opaque adaptive material استفاده کند.

Small dialog:

- centered؛
- clear hierarchy؛
- no over-blur.

Critical/destructive:

- opacity بیشتر؛
- no decorative gold؛
- focus on decision.

---

# 20. Snackbar / Toast / Inline Feedback

Snackbar:

- floating Regular/Dense Glass؛
- بالای bottom nav؛
- no collision؛
- readable؛
- timeout مناسب؛
- action compact.

Success:

- Emerald indicator، نه کل surface سبز.

Warning:

- Amber/gold-semantic controlled.

Error:

- red icon/edge، نه solid red glass.

---

# 21. Home Screen Liquid Architecture

Home باید نماینده کامل زبان Ganj باشد.

Layering:

```text
Canvas
  → subtle emerald ambient gradient
  → content cards
  → main connection hero
  → floating glass quick actions
  → floating glass bottom navigation
```

## Header

- Greeting/account ساده؛
- Notification icon Clear Glass؛
- profile icon؛
- no giant logo.

## Service Summary

- mostly content surface؛
- expiry؛
- plan؛
- status؛
- renewal CTA.

## Connection Hero

این ناحیه می‌تواند strongest Liquid visual را داشته باشد.

- circular/organic depth؛
- subtle glass ring؛
- Emerald core؛
- ambient halo؛
- selected server below؛
- state morph.

## Quick Actions

- compact regular glass؛
- 2–4 action؛
- no full dashboard tile grid شلوغ.

---

# 22. Connect Screen — Signature Liquid Experience

Connect مهم‌ترین screen بصری است.

## Disconnected

- neutral canvas؛
- central glass/emerald control؛
- low-energy ambient background؛
- server label.

## Preparing

- surface gently tightens؛
- ring starts؛
- no fake progress درصد؛
- permission/system step visible if needed.

## Connecting

- fluid staged morph؛
- refractive edge movement؛
- Emerald energy increases؛
- text stages: checking / server / tunnel.

## Connected

- control settles؛
- stable Emerald；
- subtle glass depth؛
- success haptic once؛
- elapsed time؛
- server؛
- protocol label if useful؛
- transfer stats local.

## Reconnecting

- control remains same identity؛
- pulse slower؛
- no alarming red unless terminal failure؛
- network switch hint.

## Failed

- glass effect becomes more opaque/stable؛
- error clarity increases؛
- actions Retry / Change server / Support.

---

# 23. Servers Screen

Server list content باید readable باشد، بنابراین Rows glass نیستند.

Glass features:

- Search؛
- filters؛
- smart connect capsule؛
- floating nav؛
- context actions.

Rows:

- matte/neutral؛
- country flag؛
- name؛
- latency؛
- availability؛
- tier؛
- favorite.

Selected row:

- subtle Emerald tonal background؛
- no glass rectangle on every row.

---

# 24. Store Screen

Store باید Premium باشد ولی نباید طلایی‌زده شود.

Layout:

- neutral canvas؛
- plan content cards؛
- glass filter/period selector؛
- primary Emerald purchase CTA؛
- recommended plan Gold micro-accent.

Recommended Plan:

- thin Gold edge؛
- small `پیشنهاد گنج` badge؛
- no full metallic gold card.

Purchase Sheet:

- Dense Glass shell؛
- summary solid content؛
- Prominent Emerald confirm.

---

# 25. My Services

Services content:

- stable readable cards؛
- plan status؛
- expiry؛
- device count؛
- renew.

Glass:

- filter؛
- navigation؛
- quick action؛
- contextual menu.

Expired service:

- neutral desaturated؛
- reactivation CTA Emerald؛
- no dramatic red.

---

# 26. Account

Account باید آرام‌ترین screen باشد.

Header:

- avatar/profile؛
- Telegram status؛
- service tier.

Sections:

- neutral content groups؛
- glass toolbar/nav؛
- toggles native/brand-aligned؛
- destructive actions at bottom.

هیچ gold decorative background نباید Account را شبیه Banking wealth dashboard کند.

---

# 27. Settings

Settings = readability first.

Glass فقط:

- navigation shell؛
- search if added؛
- transient chooser sheet.

Rows مات هستند.

Toggle On:

- Emerald.

Toggle Off:

- neutral.

---

# 28. Wallet / Direct Commerce

Wallet نباید حس Crypto Casino پیدا کند.

Balance Hero:

- premium but calm؛
- Dense/Regular Glass allowed؛
- Gold number accent بسیار محدود؛
- Emerald CTA برای شارژ.

Transactions:

- content rows solid؛
- filter glass؛
- status semantic icons.

Smart Banking:

- trust-first؛
- verification animation subtle؛
- no fake scanner effects؛
- privacy explanation clear.

---

# 29. Login / Telegram Linking

Login screen:

- simple ambient canvas؛
- Ganj logo flat/clean؛
- single primary CTA؛
- Dense Glass info sheet if needed؛
- legal/privacy readable.

Telegram button:

Telegram brand blue فقط در خود Provider identity و در محدوده لوگو/brand guideline مجاز است؛ این استثنا به معنی Ganj Blue Primary نیست.

---

# 30. Onboarding

Onboarding نباید ۷ صفحه تبلیغاتی داشته باشد.

حداکثر مراحل مورد نیاز:

- ارزش محصول؛
- privacy/security؛
- notification/login optional context؛
- start.

هر مرحله:

- edge-to-edge؛
- ambient visual؛
- glass CTA؛
- motion between panels.

---

# 31. VPN Permission UX

System permission screen قابل تغییر نیست، بنابراین قبل از آن یک explanation state کوتاه لازم است.

Ganj pre-permission:

- Dense Glass panel؛
- چرا Android اجازه VPN می‌خواهد؛
- اطلاعات چه چیزی جمع نمی‌شود؛
- CTA واضح.

بعد از برگشت:

- state continuity حفظ شود.

---

# 32. Speed Test

Hero meter می‌تواند glass-inspired باشد.

- circular/liquid gauge؛
- Emerald progress؛
- Gold فقط best/result accent؛
- background subdued؛
- stats solid readable.

هیچ neon speedometer arcade-like نباشد.

---

# 33. Support / Bug / Diagnostics

Forms:

- solid readable fields؛
- glass toolbar؛
- glass attachment action؛
- Dense Glass submit confirmation.

Diagnostics live state:

- subtle glass status capsule؛
- no flashing dashboard.

---

# 34. Empty / Loading / Error States

تمام stateها بخشی از Liquid language هستند.

## Loading

- skeleton mostly solid/tonal؛
- glass controls disabled but structurally stable؛
- shimmer minimal.

## Empty

- soft icon؛
- clear copy؛
- one primary action؛
- ambient background.

## Error

- surface opacity increases؛
- hierarchy simpler؛
- retry visible؛
- glass decoration decreases.

قاعده:

> هرچه situation بحرانی‌تر، decoration کمتر و clarity بیشتر.

---

# 35. Offline State

Offline mode:

- Glass controls remain identifiable؛
- unavailable actions disabled؛
- top/bottom status capsule؛
- reconnect motion minimal؛
- no full-screen error unless blocking.

---

# 36. Maintenance / Forced Update

Maintenance:

- Dense Glass modal/page shell؛
- neutral background؛
- clear time/status؛
- no fake progress.

Forced Update:

- prominent but simple؛
- one CTA؛
- release notes concise؛
- no dismiss if truly forced.

---

# 37. Typography روی Glass

Text روی Glass باید:

- contrast تست‌شده؛
- وزن کافی؛
- سایز مناسب؛
- بدون thin weight؛
- بدون gradient text؛
- بدون gold body text.

Gold text فقط:

- Premium label؛
- small reward؛
- nonessential accent.

Body copy همیشه neutral high-contrast.

---

# 38. Iconography

Icons:

- consistent family؛
- rounded هندسی؛
- optical balance؛
- 24dp grid؛
- touch target حداقل 48dp؛
- selected variant می‌تواند fill شود.

Glass icon button:

- icon container خودش یک Glass surface است؛
- نباید glass پشت glass باشد.

---

# 39. Shape Language

Shapes باید نرم و continuous باشند.

Target radius families:

```text
small control     12dp
medium control    16dp
card              20–24dp
sheet              28–32dp top
floating nav       26–32dp
pill               999dp
```

در M3 Expressive می‌توان shape variation داشت اما random shapes ممنوع.

Connect control می‌تواند organic/circular باشد.

---

# 40. Spacing

Liquid UI نیاز به breathing room دارد.

Base grid: 4dp.

Common:

- 4؛
- 8؛
- 12؛
- 16؛
- 20؛
- 24؛
- 32؛
- 40.

Screen horizontal padding target:

- compact phone: 16–20dp؛
- larger phone: 20–24dp؛
- tablet/adaptive: constraint-based.

Glass controls باید اطراف خود فضای کافی داشته باشند تا از content جدا دیده شوند.

---

# 41. Motion Physics

Liquid بدون Motion کامل نیست.

Motion philosophy:

- fluid؛
- spring-based where appropriate؛
- short؛
- intentional؛
- interruptible؛
- state-driven.

## Timing targets

Micro:

- 120–180ms.

Standard content:

- 220–320ms.

Glass transform:

- 280–420ms.

Sheet:

- 320–480ms.

Connect major morph:

- 450–750ms.

این مقادیر باید بر اساس frame performance نهایی tune شوند.

---

# 42. Motion Patterns

## Press

- scale 0.98–0.96 برای control بزرگ؛
- opacity/lighting shift؛
- haptic optional.

## Selection

- fill slides/morphs؛
- no instant hard switch.

## Navigation

- selected glass highlight moves؛
- content transitions subtle fade/translate؛
- no dramatic page flip.

## Sheet

- spring low-bounce؛
- follows gesture velocity.

## Connect

- continuous morph؛
- state color and geometry evolve together.

---

# 43. Refraction Motion

برای ایجاد حس Liquid واقعی می‌توان subtle refraction/lighting shift داشت.

مجاز:

- edge highlight movement؛
- 1–3px-equivalent optical drift؛
- small luminance response؛
- shape distortion بسیار کم هنگام press.

غیرمجاز:

- wobble ژله‌ای زیاد؛
- magnification شدید؛
- متن متحرک زیر glass؛
- lens distortion که readability را خراب کند.

---

# 44. Haptics

Liquid interaction بدون haptic در actionهای مهم ناقص است.

Map:

- tab selection → selection haptic؛
- server select → light selection؛
- connect press → light impact؛
- connected → success؛
- final connection fail → error؛
- destructive confirm → stronger warning؛
- toggle → subtle.

ممنوع:

- haptic در scroll؛
- repeated pulse during connecting؛
- haptic every animation frame.

---

# 45. Light/Dark Adaptive Glass

Glass token نباید فقط alpha ثابت داشته باشد.

در Dark:

- opacity کمی بیشتر ممکن است لازم باشد؛
- border highlight روشن؛
- shadow کمتر؛
- tint Emerald کم.

در Light:

- opacity/blur balance متفاوت؛
- border darker hairline؛
- shadow مهم‌تر؛
- white highlight subtle.

---

# 46. Reduce Transparency

اگر User/System reduced transparency فعال باشد:

- blur حذف/کاهش؛
- surface به OpaqueFallback می‌رود؛
- contrast افزایش؛
- hierarchy حفظ؛
- shape ثابت؛
- motion ساده‌تر در صورت Reduce Motion.

این حالت optional نیست؛ بخشی از Accessibility Definition of Done است.

---

# 47. High Contrast

High Contrast:

- border stronger؛
- glass opacity بیشتر؛
- text contrast بیشتر؛
- subtle decoration کمتر؛
- selected state فقط رنگ نباشد؛ icon/shape نیز تغییر کند.

---

# 48. Large Font / Dynamic Type

Glass container نباید fixed-height باشد اگر متن user-facing دارد.

Rule:

- minHeight، نه hardHeight؛
- wrap content؛
- labels در 200% font scale تست شوند؛
- tab label strategy مشخص؛
- no clipping.

---

# 49. RTL/LTR

Glass geometry باید direction-aware باشد.

- Navigation order محصول ثابت بر اساس UX تصمیم می‌گیرد اما label layout RTL-aware؛
- back/action icons mirror مطابق Android conventions؛
- gradients اگر معنای directional ندارند mirror نشوند؛
- directional decorative lighting باید در RTL بررسی شود؛
- IP/protocol/UUID bidi-isolated.

---

# 50. Accessibility Semantics

Glass هیچ‌گاه semantics را جایگزین نمی‌کند.

هر control:

- accessible name؛
- role؛
- state؛
- action؛
- minimum touch target؛
- focus order.

Connect control باید state را TalkBack اعلام کند بدون spam.

مثال:

```text
«اتصال VPN، قطع است، دو بار ضربه برای اتصال»
```

پس از Connected:

```text
«VPN متصل است، سرور آلمان، دو بار ضربه برای قطع اتصال»
```

---

# 51. Performance Contract

Liquid Glass نباید باعث افت کیفیت محصول شود.

Target:

- 60fps minimum baseline؛
- 90/120Hz devices بدون artificial cap where platform permits؛
- no sustained jank؛
- no excessive overdraw؛
- no continuous expensive blur on offscreen content؛
- no battery-heavy decorative loop.

## Performance tiers

### Tier A

High-end:

- full supported blur؛
- subtle real-time light shift؛
- richer morph.

### Tier B

Mid-range:

- standard blur؛
- fewer dynamic effects.

### Tier C

Low-end / battery saver:

- frosted simulated surface؛
- static tint؛
- minimal blur؛
- same hierarchy.

کاربر نباید احساس کند نسخه Tier C «خراب» است.

---

# 52. Battery Contract

VPN app ذاتاً long-running است. UI نباید مصرف اضافه قابل توجه ایجاد کند.

ممنوع:

- دائم animated shader؛
- particles while connected؛
- continuous glass refraction when screen idle؛
- infinite pulse after connected؛
- background animation when app not visible.

Connected state پس از settle باید تقریباً static باشد.

---

# 53. Compose Implementation Architecture

هدف معماری UI:

```text
ui/
  theme/
    GanjColors.kt
    GanjTypography.kt
    GanjShapes.kt
    GanjMotion.kt
    GanjGlass.kt
  components/
    glass/
      GlassSurface.kt
      GlassButton.kt
      GlassIconButton.kt
      GlassNavigationBar.kt
      GlassSearch.kt
      GlassSheet.kt
      GlassSnackbar.kt
    content/
      GanjCard.kt
      ServiceRow.kt
      ServerRow.kt
  screens/
    home/
    servers/
    connect/
    store/
    account/
```

یک monolithic `GanjVpnApp.kt` نباید محل نهایی Design System باشد.

---

# 54. `GanjGlass` API Contract

Implementation باید semantic باشد، نه raw alpha everywhere.

مثال مفهومی:

```kotlin
GanjGlass(
    role = GlassRole.Regular,
    tint = GlassTint.Neutral,
    emphasis = GlassEmphasis.Standard,
    adaptive = true,
)
```

نه:

```kotlin
background(Color.White.copy(alpha = .12f))
.blur(24.dp)
```

در componentهای مختلف.

علت:

- consistency؛
- accessibility؛
- theme switching؛
- performance tuning؛
- global redesign.

---

# 55. Glass Tokens

حداقل tokenهای لازم:

```text
glass.clear.container
glass.regular.container
glass.dense.container
glass.prominent.container
glass.fallback.container

glass.border.soft
glass.border.strong
glass.highlight.top
glass.shadow

glass.blur.clear
glass.blur.regular
glass.blur.dense

glass.scrim.light
glass.scrim.dark

glass.tint.emerald
glass.tint.gold
glass.tint.neutral
```

این tokenها باید Light/Dark/Accessibility variant داشته باشند.

---

# 56. No Raw Alpha Rule

Feature developer نباید arbitrary alpha تعریف کند.

ممنوع در Screen layer:

- `.copy(alpha = 0.13f)` برای ساخت glass؛
- raw blur radius؛
- random border alpha؛
- random tint.

همه از token/component system می‌آیند.

---

# 57. Material 3 Expressive Integration

M3 Expressive باید برای:

- component state؛
- motion principles؛
- adaptive typography؛
- expressive but controlled shapes؛
- Android 16 visual integration

استفاده شود.

اما:

- default purple/blue palette استفاده نمی‌شود؛
- Dynamic Color default خاموش؛
- component ظاهر نهایی با Ganj tokenها override می‌شود.

---

# 58. System Bars

Status/navigation system region باید با canvas هماهنگ باشد.

- edge-to-edge؛
- icon contrast correct؛
- no arbitrary solid brand bar؛
- navigation gesture area respected؛
- glass bottom nav بالای gesture inset.

---

# 59. Predictive Back

Sheet، Dialog و Screen transition باید predictive back را خراب نکنند.

Glass morph می‌تواند با gesture progress هماهنگ شود اما:

- gesture نباید lag داشته باشد؛
- dismissal قابل پیش‌بینی باشد؛
- focus state restore شود.

---

# 60. Keyboard / IME

Search/Login/Support:

- IME animation با glass surface هماهنگ؛
- bottom nav در context مناسب hide/adjust؛
- composer بالا بیاید؛
- glass field زیر keyboard گیر نکند.

---

# 61. Foldable / Tablet / Large Screen

Liquid language باید adaptive باشد.

Large screen:

- bottom nav ممکن است navigation rail/glass rail شود؛
- master-detail Servers possible؛
- side panel Dense Glass؛
- content width constrained؛
- giant stretched glass ممنوع.

---

# 62. Orientation

Phone primary portrait است، اما landscape failure نباید رخ دهد.

Connect hero:

- landscape compact layout؛
- stats side-by-side؛
- navigation adaptive.

---

# 63. Loading Performance and Skeleton

Skeletonها glass نیستند.

Reason:

- shimmer + blur expensive؛
- hierarchy muddled.

Skeleton:

- tonal neutral؛
- minimal shimmer؛
- real glass controls static/disabled.

---

# 64. Images / Flags under Glass

Glass روی image ممکن است رنگ زیادی جذب کند.

Rule:

- scrim adaptive؛
- text contrast measured؛
- selected control tint stable؛
- busy flag/image پشت large text glass ممنوع.

---

# 65. Logo Usage with Liquid Glass

لوگوی 3D رسمی:

- Splash؛
- marketing؛
- premium hero limited.

داخل App:

- flat/simplified vector؛
- adaptive icon؛
- monochrome icon where needed.

Logo روی Glass:

- no heavy shadow؛
- no extra gold glow؛
- enough clear space؛
- logo خودش highlight است.

---

# 66. Animation of Logo

Splash logo motion:

- very short؛
- subtle depth/light؛
- no 360 spin؛
- no long intro؛
- must not delay app readiness.

Transition from splash:

- logo scale/fade into app content possible؛
- 300–500ms target maximum after ready.

---

# 67. Security UX

Security should feel calm.

Glass security status:

- shield/check subtle؛
- Emerald safe؛
- no hacker neon؛
- no fake scanning animation؛
- technical details optional.

Warnings:

- clarity first؛
- Dense/Opaque material؛
- specific action.

---

# 68. Privacy UX

Privacy consent/dialog:

- no decorative transparency that reduces readability؛
- Dense Glass or opaque fallback؛
- clear choices؛
- no preselected marketing consent؛
- no dark patterns.

---

# 69. Billing UX

Payment screen:

- stable hierarchy؛
- price readable؛
- renewal terms visible؛
- provider state clear؛
- pending visible؛
- no glass that hides terms.

Purchase success:

- short Emerald liquid morph؛
- tiny Gold premium sparkle optional؛
- then settle static.

---

# 70. Error UX

Error message باید concrete باشد.

بد:

```text
خطایی رخ داد
```

بهتر:

```text
اتصال به سرور برقرار نشد.
یک سرور دیگر انتخاب کنید یا دوباره تلاش کنید.
```

Glass container باید error را مخفی نکند.

---

# 71. Motion Accessibility

Reduce Motion:

- morph → short fade/scale؛
- refraction shift off؛
- spring bounce حذف؛
- connection progress functional باقی بماند؛
- state labels still update.

---

# 72. Touch Targets

Minimum interactive target: **48dp × 48dp**.

Glass visual shape می‌تواند کوچک‌تر باشد، اما hit area کمتر از 48dp نباشد.

خاص:

- favorite icon؛
- close؛
- filter remove؛
- help؛
- info.

---

# 73. Contrast Targets

هدف WCAG AA یا بهتر برای content:

- normal text approximately 4.5:1؛
- large text 3:1؛
- meaningful non-text control boundaries/state حداقل خوانا.

Glass باید در worst-case underlying background تست شود، نه فقط screenshot ایده‌آل.

---

# 74. Screenshot QA Background Matrix

هر Glass component حداقل روی این backgroundها تست شود:

1. dark plain؛
2. light plain؛
3. Emerald halo؛
4. mixed high contrast content؛
5. list scrolling underneath؛
6. image/flag underneath؛
7. high contrast accessibility؛
8. reduce transparency.

---

# 75. Device QA Matrix

UI Liquid QA:

- Pixel reference؛
- Samsung؛
- Xiaomi/POCO؛
- low-end device؛
- high refresh device؛
- Android 8/10 legacy fallback where supported؛
- Android 12؛
- Android 14؛
- Android 15؛
- Android 16.

توجه: feature implementation باید با minSdk واقعی repo هماهنگ شود و روی نسخه‌های قدیمی fallback داشته باشد.

---

# 76. Performance QA Scenarios

- fast scrolling under glass nav؛
- opening/closing filter sheet repeatedly؛
- connect morph while network callback fires؛
- keyboard open/close search؛
- low battery saver؛
- device thermal throttling؛
- 120Hz phone؛
- background/foreground؛
- screen rotate؛
- process recreation.

---

# 77. Jank Gate

PR Final UI نباید فقط screenshot-based تایید شود.

Evidence:

- Macrobenchmark یا equivalent؛
- frame timing؛
- startup؛
- scroll؛
- connect animation؛
- sheet animation.

اگر Liquid effect باعث regression محسوس شود، effect downgrade می‌شود، نه performance target.

---

# 78. Visual Regression Tests

Golden/screenshot tests برای:

- Dark/Light؛
- fa/en؛
- RTL/LTR؛
- 1.0x/1.3x/2.0x font؛
- connected/disconnected/error؛
- free/premium؛
- reduce transparency.

---

# 79. Component Acceptance Checklist

هر Glass component باید قبل از Done:

- semantic role داشته باشد؛
- token-based باشد؛
- Dark/Light داشته باشد؛
- RTL test؛
- TalkBack؛
- 48dp target؛
- contrast pass؛
- Reduce Transparency fallback؛
- Reduce Motion behavior؛
- performance test؛
- no nested glass violation.

---

# 80. Screen Acceptance Checklist

هر Screen قبل از Final:

- Liquid hierarchy مشخص؛
- Content layer خوانا؛
- glass navigation consistent؛
- primary action Emerald؛
- Gold محدود؛
- no Blue primary؛
- edge-to-edge؛
- state animations؛
- loading/empty/error؛
- fa/en؛
- RTL/LTR؛
- font scaling؛
- TalkBack؛
- battery/performance sensible.

---

# 81. Anti-patterns — موارد ممنوع

## ❌ Frosted Glass Everywhere

هر Card blur شده باشد.

## ❌ Glassmorphism 2021 style

White translucent rectangles + giant blur + neon gradients.

## ❌ Casino Gold

Gold buttons/cards/background در تمام صفحه.

## ❌ Emerald Flood

Background کل screen سبز اشباع.

## ❌ Material Default Blue

استفاده از blue primary به دلیل default library.

## ❌ Nested Glass

Glass container داخل Glass container.

## ❌ Fake Depth

shadowهای زیاد بدون hierarchy.

## ❌ Infinite Motion

animation دائمی connected state.

## ❌ Low Contrast Premium

طلایی روی سفید یا سبز روی مشکی بدون contrast درست.

## ❌ Blur as Identity

اگر Blur خاموش شود و UI هویت خود را از دست بدهد، Design System ضعیف است.

---

# 82. Liquid Glass Maturity Levels

## Level 0 — Not acceptable

- flat Material defaults؛
- blue primary؛
- no depth؛
- standard rectangular nav.

## Level 1 — Cosmetic glass

- چند card blur؛
- بدون system.

هنوز قابل قبول نیست.

## Level 2 — Structured Liquid

- glass nav؛
- semantic surfaces؛
- motion؛
- adaptive material.

حداقل Beta visual target.

## Level 3 — Ganj Production Liquid

- همه Screenها زبان یکپارچه؛
- brand-integrated؛
- accessibility؛
- performance tiers؛
- motion polished؛
- all state coverage؛
- visual regression evidence.

**Production target = Level 3.**

---

# 83. Phase 7 Migration Requirement

Branchهای UI فعلی هرجا موارد زیر دارند باید migrate شوند:

- `GanjBlue`؛
- blue primary؛
- Material default blue؛
- hardcoded colors؛
- one-off `GlassCard` بدون role؛
- fixed RTL direction؛
- hardcoded English strings؛
- non-adaptive blur.

Migration steps:

1. rebase روی latest main؛
2. import design tokens v0.2+؛
3. ساخت `GlassRole` system؛
4. Theme Emerald/Gold/Neutral؛
5. Navigation Liquid؛
6. Connect Liquid state machine؛
7. localization resources؛
8. Light/Dark/System؛
9. accessibility fallback؛
10. performance benchmark؛
11. screenshot tests؛
12. PR evidence.

---

# 84. Final UI Definition of Done

P0-10 فقط وقتی Done است که:

- تمام screenهای production به زبان Liquid یکپارچه باشند؛
- Bottom Navigation glass باشد؛
- toolbar/search/filter/sheets material roles درست داشته باشند؛
- Content cards بی‌دلیل glass نباشند؛
- no blue primary باقی بماند؛
- Emerald primary و Gold accent درست استفاده شوند؛
- no raw hardcoded glass alpha در feature layer؛
- dark/light کامل؛
- fa/en کامل؛
- RTL/LTR؛
- TalkBack؛
- 200% font scale؛
- reduce transparency؛
- reduce motion؛
- high contrast؛
- low-end fallback؛
- frame/jank evidence؛
- UI state با VPN/Billing واقعیت هماهنگ باشد.

---

# 85. Design Review Questions

قبل از approve هر UI PR این سوال‌ها پاسخ داده شود:

1. آیا این Screen واقعاً Liquid language دارد یا فقط blur؟
2. Glass چه hierarchy ایجاد می‌کند؟
3. اگر Blur خاموش شود، structure هنوز خوب است؟
4. Content layer خواناست؟
5. چرا این component glass است؟
6. آیا Glass روی Glass شده؟
7. Emerald درست استفاده شده؟
8. Gold بیش‌ازحد نیست؟
9. Blue accidental وجود دارد؟
10. animation state-driven است؟
11. accessibility fallback چیست؟
12. low-end performance چه می‌شود؟
13. RTL تست شده؟
14. 200% font چه می‌شود؟
15. آیا Android-native behavior حفظ شده؟

---

# 86. Implementation Rule for AI/Developer

هر AI یا Developer که روی UI کار می‌کند باید قبل از کدنویسی این ترتیب را بخواند:

```text
00-CURRENT_PRODUCT_SCOPE.fa.md
→ 15-android-ui-ux-brand-system.fa.md
→ 16-liquid-glass-first-design-standard.fa.md
→ 04-design-system.fa.md
→ design/tokens.json
→ current Android UI code
```

نباید از حافظه عمومی «glassmorphism» استفاده کند و یک سبک تصادفی بسازد.

---

# 87. Product Owner Intent — Non-negotiable

این Requirement مستقیم و قطعی محصول است:

> **Ganj VPN باید از نظر زبان بصری یک اپلیکیشن کاملاً مدرن و Liquid Glass-first باشد. تمام Screenها باید به یک سیستم Liquid واحد تعلق داشته باشند. استفاده از Glass فقط یک افکت جانبی یا چند Card محدود نیست؛ کل Navigation، Material hierarchy، motion، depth، interaction و state transition باید با این زبان طراحی شود. با این حال برای رعایت اصول حرفه‌ای، Content layer نباید به‌صورت کورکورانه Blur شود و خوانایی، عملکرد و Accessibility همیشه حفظ می‌شوند.**

این بند باید در Design Review و PR Review به‌عنوان Requirement اصلی بررسی شود.

---

# 88. External Design References

منابع مرجع برای تفسیر اصول، نه برای کپی مستقیم:

- Apple Developer — Liquid Glass: `https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass`
- Apple Human Interface Guidelines — Materials: `https://developer.apple.com/design/human-interface-guidelines/materials`
- Apple Human Interface Guidelines — Color: `https://developer.apple.com/design/human-interface-guidelines/color`
- Apple Design Principles: `https://developer.apple.com/design/human-interface-guidelines/design-principles`
- Android Developers — Material 3 in Compose / M3 Expressive: `https://developer.android.com/develop/ui/compose/designsystems/material3`

این منابع platform guidance هستند؛ هویت نهایی رنگ، component و motion متعلق به Ganj است.

---

# 89. خلاصه نهایی

## **کل Ganj VPN باید Liquid Glass-first باشد.**

اما تعریف حرفه‌ای آن:

```text
Liquid Glass-first
≠ blur everywhere

Liquid Glass-first
=
Layered hierarchy
+ floating functional materials
+ edge-to-edge content
+ adaptive translucency
+ refined Emerald/Gold brand tint
+ fluid state motion
+ native Android behavior
+ accessibility fallbacks
+ performance-aware rendering
+ consistent design across every screen
```

این فرمول از این لحظه استاندارد اجباری طراحی Ganj VPN است.
