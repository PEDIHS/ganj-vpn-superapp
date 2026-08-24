# Incident Response و Disaster Recovery Runbook

این سند برای اختلال VPN، Backend، خرید، امنیت، حریم خصوصی، Signing و Supply Chain اجرا می‌شود. هدف اول حفاظت از کاربر و داده است؛ بازگشت سرویس بدون حذف شواهد یا ایجاد ریسک ثانویه انجام می‌شود.

## Severity

| سطح | نمونه | اعلام | هدف مهار |
|---|---|---:|---:|
| SEV-0 | کلید Signing/Root compromise، نشت گسترده، دستکاری Supply Chain | فوری | 30 دقیقه |
| SEV-1 | قطع گسترده VPN/خرید، auth bypass، traffic leak | 5 دقیقه | 60 دقیقه |
| SEV-2 | اختلال منطقه‌ای، latency شدید، feature اصلی degraded | 15 دقیقه | 4 ساعت |
| SEV-3 | اثر محدود با workaround | روز کاری | برنامه‌ریزی‌شده |

Severity از Impact واقعی تعیین می‌شود، نه پیچیدگی اصلاح. Incident Commander می‌تواند سطح را تغییر دهد و دلیل را در Timeline ثبت کند.

## نقش‌ها

- **Incident Commander (IC):** تصمیم، اولویت، cadence و پایان Incident؛ مستقیماً Debug نمی‌کند.
- **Technical Lead:** تشخیص و اجرای مهار/Recovery؛ تغییرات را با IC هماهنگ می‌کند.
- **Security/Privacy Lead:** شواهد، دامنه، الزامات افشا و هماهنگی Legal/Policy.
- **Communications Lead:** پیام کاربر، Support، Telegram/Admin status و stakeholder update.
- **Scribe:** Timeline UTC، تصمیم، command/change ID و شواهد را ثبت می‌کند.
- **Product/Support Liaison:** اثر کاربر، workaround و ticket trend را جمع می‌کند.

برای SEV-0/1 نقش‌ها جدا هستند. اگر نفر کافی نیست، IC و Technical Lead هرگز یک نفر نیستند.

## چرخه Incident

```text
Detect -> Declare -> Triage -> Contain -> Eradicate -> Recover -> Monitor -> Close -> Learn
```

### Declare و Triage

1. Incident ID، severity، زمان UTC، محیط و IC ایجاد شود.
2. تغییر و rollout مرتبط freeze شود؛ کانال اختصاصی و Dashboard لینک شود.
3. اثر: چه کاربران/کشورها/buildها/سرویس‌ها و از چه زمانی؟
4. فرضیه‌ها از facts جدا ثبت شوند؛ هیچ Secret یا PII در کانال قرار نگیرد.
5. یک مسیر containment امن انتخاب و owner/deadline مشخص شود.

### Contain

ابزارها به ترتیب کم‌خطر: Feature kill switch، توقف rollout، حذف سرور از catalog، rate limit، revoke session/token، freeze admin، queue pause، payment fulfillment hold و آخرین راه force update. قطع کامل نباید قبل از ارزیابی traffic leak و اثر Kill Switch انجام شود.

### Eradicate و Recover

- علت اصلی و persistence حذف، credential rotate و unauthorized access revoke شود؛
- Patch از CI و review عبور کند؛ تغییر دستی production باید بعداً codify شود؛
- Recovery تدریجی با synthetic probe و cohort محدود انجام شود؛
- data/payment reconciliation قبل از اعلام حل کامل شود؛
- حداقل دو بازهٔ Alert بدون regression پایش شود.

## Runbook سناریوها

### قطع یا افت اتصال VPN

1. connect success را بر build/protocol/network/country/server تفکیک کنید.
2. server catalog و probe را با sessionهای موجود مقایسه کنید.
3. سرور معیوب را از انتخاب جدید خارج کنید؛ session فعال سالم را بی‌دلیل قطع نکنید.
4. Smart Connect weights یا protocol rollout را با Config امضاشده برگردانید.
5. DNS/Kill Switch/leak smoke را قبل از بازگردانی server اجرا کنید.
6. پس از recovery، capacity و false health result را بررسی کنید.

### اختلال خرید یا Entitlement

1. پذیرش سفارش جدید را در صورت خطر double charge محدود کنید.
2. provider callback، signature verification، idempotency key و queue lag را بررسی کنید.
3. ledger را تغییر/حذف نکنید؛ adjustment جبرانی با reference ایجاد کنید.
4. payment successful بدون entitlement را reconcile کنید.
5. duplicate entitlement یا chargeback را با Audit و notification اصلاح کنید.
6. خرید sandbox و restore end-to-end قبل از بازگشایی اجرا شود.

### افشای Secret یا Token

1. مقدار را در کانال/Issue/Log تکرار نکنید؛ issuer و scope را شناسایی کنید.
2. ابتدا revoke/rotate، سپس deployment و session invalidation انجام شود.
3. Audit برای استفادهٔ مشکوک از زمان احتمالی افشا بررسی شود.
4. Git history/artifact/cache/log پاک‌سازی شود؛ Rotation مقدم است.
5. وابستگی‌های downstream و backupهای حاوی Secret نیز rotate/purge شوند.
6. private advisory و ارزیابی notification قانونی آغاز شود.

### Signing یا Supply-Chain compromise

1. release pipeline، upload key و انتشار را فوراً freeze کنید.
2. artifact fingerprint/provenance را با known-good مقایسه کنید.
3. affected version/tag/action/dependency و نصب‌های احتمالی را تعیین کنید.
4. GitHub token/session، environment approvals و keys را rotate کنید.
5. Play key recovery/revocation process و user communication را فعال کنید.
6. rebuild فقط از clean trusted runner و dependencyهای verified انجام شود.

### Data loss یا Database corruption

1. writeها را متوقف و snapshot forensic جدا تهیه کنید.
2. RPO/RTO و آخرین backup verified را تعیین کنید.
3. Restore در محیط isolated و integrity/reconciliation تست شود.
4. cutover با change ID و امکان بازگشت انجام شود.
5. ledger/payment/user ownership consistency جداگانه تأیید شود.

## شواهد و Forensics

شواهد read-only، checksum شده و با chain of custody نگهداری می‌شوند. دسترسی least-privilege و time-bound است. از کپی indiscriminate دیتابیس یا Log خام پرهیز می‌شود. Timeline شامل UTC، actor، action، reason، result، dashboard/change/commit ID است؛ Secret در آن ثبت نمی‌شود.

## ارتباطات

پیام اولیه باید دانسته‌ها را بگوید و حدس نزند:

> در حال بررسی اختلال در [قابلیت/منطقه] از ساعت [UTC] هستیم. تیم فنی فعال است. در حال حاضر [اثر تأییدشده] و [راهکار امن در صورت وجود] اعلام می‌شود. به‌روزرسانی بعدی تا [زمان] منتشر خواهد شد.

پیام Recovery:

> سرویس از ساعت [UTC] بازیابی شده و تحت پایش است. [اثر باقی‌مانده/اقدام کاربر] اعلام می‌شود. پس از تکمیل بررسی، خلاصهٔ علت و اقدامات پیشگیرانه منتشر خواهد شد.

هرگز در پیام عمومی exploit، infrastructure address، user count تأییدنشده یا وعدهٔ زمانی بدون owner گفته نمی‌شود. رخداد Privacy/Security قبل از پیام با مسئول مربوطه هماهنگ می‌شود.

## Cadence

- SEV-0: update داخلی هر 15 دقیقه، stakeholder هر 30 دقیقه؛
- SEV-1: داخلی هر 30 دقیقه، stakeholder هر 60 دقیقه؛
- SEV-2: هر 2 ساعت یا با تغییر مادی؛
- SEV-3: در Ticket/روز کاری.

نبود پیشرفت نیز update است. Communications Lead زمان بعدی را همیشه مشخص می‌کند.

## معیار پایان

Incident وقتی Resolve می‌شود که service SLO به حالت پایدار برگشته، risk مهار، reconciliation کامل، Alert روشن و owner کار باقی‌مانده مشخص باشد. بسته‌شدن نهایی پس از review Security/Privacy و ثبت Postmortem است.

## Postmortem بدون سرزنش

برای SEV-0/1 ظرف 5 روز کاری و SEV-2 ظرف 10 روز نوشته می‌شود:

- خلاصه و اثر قابل اندازه‌گیری؛
- Timeline UTC؛
- detection gap و contributing factors؛
- چه چیزی خوب/بد عمل کرد؛
- علت سیستمی، نه فقط آخرین خطای انسانی؛
- action item با owner، priority، deadline و verification؛
- تغییر SLO، test، alert، runbook و architecture.

Action بدون owner/deadline پذیرفته نیست. تکرار رخداد تا تکمیل اقدامات در Product Risk Dashboard باقی می‌ماند.

## Drill

هر فصل حداقل یک Game Day اجرا می‌شود: server region outage، payment callback replay، secret exposure، bad Remote Config و backup restore. سالی دو بار Signing/CI compromise tabletop انجام می‌شود. معیار موفقیت: زمان تشخیص/اعلام/مهار، کیفیت Timeline، عدم افشای داده، موفقیت rollback و تکمیل actionها.
