# قرارداد پلتفرم Enterprise گنج VPN

نسخه قرارداد: `0.2.0`

دامنه این سند: اپ Android، API، Backend، پنل Admin، ربات Telegram و عملیات Production.
منابع ماشینی: [`api/openapi.yaml`](../api/openapi.yaml) و [`database/schema.sql`](../database/schema.sql).

## اصل غیرقابل‌مذاکره اتصال VPN

Ganj VPN یک Client عمومی شبیه V2RayNG نیست. **ورود دستی کانفیگ از URI، QR، Clipboard، فایل، Subscription URL یا هر منبع دلخواه کاربر ممنوع است.** هیچ endpoint، صفحه یا intent برای import وجود ندارد.

جریان مجاز تنها این است:

1. کاربر وارد حساب مهمان یا Telegram-linked خود می‌شود؛
2. Backend اشتراک، سرویس فعال، سقف دستگاه، کشور/سرور و پروتکل مجاز را ارزیابی می‌کند؛
3. دستگاه با کلید public و attestation ثبت و اثبات مالکیت می‌شود؛
4. `POST /services/{service_id}/connection-profile` یک envelope کوتاه‌عمر و رمزگذاری‌شده برای همان `service_id + device_id + server_id` صادر می‌کند؛
5. کلاینت envelope را فقط در حافظه امن باز و مستقیماً به VPN Core می‌دهد؛ export و نمایش credential مجاز نیست؛
6. لغو دستگاه، پایان اشتراک، تغییر entitlement یا افزایش `config_version` همه profileهای قبلی را بی‌اعتبار می‌کند.

جدول `config_envelopes` با `profile_source='entitlement'`، `entitlement_revision` و `device_public_key_hash` این invariant را ثبت می‌کند. Backend باید تعلق دستگاه به کاربر، سرویس فعال، entitlement و limit دستگاه را در یک transaction کنترل کند؛ این کنترل را نمی‌توان فقط به کلاینت سپرد.

## اصول مشترک Enterprise

- حداقل‌سازی داده: مقصد VPN، DNS query، محتوای ترافیک، credential، Token، IP کامل و Advertising ID هرگز جمع‌آوری نمی‌شوند.
- Secure by default: رمزنگاری در انتقال و سکون، KMS key version، Secret reference، least privilege، MFA و Audit برای Admin.
- Consent by purpose: Analytics، Crash diagnostics، Marketing و Support diagnostics رضایت مستقل و قابل لغو دارند.
- Append-only evidence: تغییر وضعیت Bug، Audit، SOC، Privacy و Release تاریخچه غیرقابل بازنویسی دارند.
- Environment isolation: Development → Testing → Staging → Production؛ داده و Secret محیط‌ها مشترک نیست.
- Idempotency: گزارش‌های Client با UUID و عملیات مالی با `Idempotency-Key` تکرار امن دارند.
- Retention: هر داده owner، purpose، expiry و deletion path دارد؛ legal hold استثناست و باید ثبت شود.
- Observability بدون Surveillance: سلامت محصول پایش می‌شود، نه فعالیت اینترنتی کاربر.

## ۱. Bug Tracking & Issue Management

### مسیر کاربر

`POST /reliability/bug-reports` توضیح کاربر و context صریحاً تأییدشده را می‌گیرد. Context مجاز: Device model، OS/App version، Connection state، Server ID، Network type، Error code و timestamp. User ID از session معتبر گرفته می‌شود و از payload قابل جعل پذیرفته نمی‌شود.

Screenshot، Screen recording، Log و Document از مسیر `POST /reliability/bug-reports/{bug_id}/attachments` با upload grant کوتاه‌عمر ارسال می‌شوند. فایل:

- سقف ۵۰ MiB، MIME allowlist و SHA-256 دارد؛
- در Object Storage رمزگذاری‌شده است، نه PostgreSQL؛
- قبل از دسترسی Agent قرنطینه و malware scan می‌شود؛
- Screen recording به consent صریح نیاز دارد؛
- با expiry حذف و حذف آن Audit می‌شود.

### داشبورد توسعه

`GET /admin/issues` فیلتر Severity/Status، impact، version، module و assignee را می‌دهد. شدت‌ها:

- Critical: قطع فراگیر، رخداد امنیتی یا از دست رفتن داده؛
- High: شکست اتصال/خرید یا مصرف شدید منابع؛
- Medium: رفتار اشتباه یا اختلال UI؛
- Low: ایراد جزئی بدون اثر جدی.

چرخه مجاز `New → Investigating → Assigned → Fixing → Testing → Released → Closed` است. تغییر فقط با `POST /admin/issues/{id}/transition`، optimistic `from_status`، reason و Audit انجام می‌شود. Released باید به `app_releases` متصل باشد. reopen از Closed یک transition جدید با دلیل است، نه بازنویسی تاریخچه.

جداول: `bug_reports`, `bug_status_history`, `bug_attachments`.

## ۲. Crash Reporting

`POST /telemetry/crashes` stack trace پاک‌سازی‌شده، module/function، app/OS version، error code، connection state و timestamp را می‌پذیرد. Server-side redactor پیش از persistence Token، URL، path کاربر و متن ورودی را حذف می‌کند.

Fingerprint پایدار رخدادها را در `crash_groups` گروه‌بندی و `crash_events` occurrenceها را با retention کوتاه نگه می‌دارد. Dashboard تعداد رخداد، کاربران pseudonymous متاثر، نسخه‌ها، first/last seen و اولویت را نمایش می‌دهد. Crash payload جایگزین Bug report کاربر نیست؛ امکان link گروه به Issue وجود دارد.

## ۳. Analytics و Funnel

رویدادها از `POST /telemetry/events:batch` با `consent_receipt_id` معتبر می‌آیند. هر event name/version در `analytics_event_definitions` property allowlist دارد؛ property پرکاردینالیتی، متن آزاد، مقصد شبکه و شناسه تبلیغاتی رد می‌شود.

رویدادهای استاندارد شامل install، app_open، first_connect، connection_result، server_selected، account_linked، checkout_started، purchase_completed، subscription_cancelled است. Funnel نمونه:

`Install → Open → Connect → Account → Purchase`

تعریف در `funnel_definitions/funnel_steps` و خروجی فقط در `funnel_daily_aggregates` است. Cohort کوچک کمتر از حد تعریف‌شده suppress می‌شود. داشبورد event-level export ندارد.

## ۴. Remote Configuration

`GET /app/runtime-config` فقط تنظیمات schema-validated مناسب environment/device/locale/plan را با ETag، TTL و امضای سرور برمی‌گرداند. Namespaceها owner و schema دارند. انتشار با `remote_config_releases` immutable است و rollback یک release تازه محسوب می‌شود.

Remote config برای متن، Banner، محدودیت plan رایگان، Campaign، maintenance و رفتار غیرامنیتی است. credential، private key، Token، VPN profile و provider secret در آن ممنوع‌اند. کلیدهای امنیت/Privacy/Billing/VPN به dual approval نیاز دارند.

## ۵. Feature Flag و A/B Testing

`feature_flags` هویت ثابت و `feature_flag_versions` سیاست immutable دارد. Assignment بر hash پایدار subject و salt محیط انجام می‌شود تا کاربر بین variantها نپرد. درصد rollout، audience، beta cohort و experiment key پشتیبانی می‌شود.

Kill switch اضطراری، guardrail metric، تاریخ انقضا و owner برای هر flag الزامی است. نتیجه در `feature_assignments` ثبت می‌شود؛ PII در rule یا assignment وجود ندارد.

## ۶. Logging و Audit

سه stream در `structured_logs`:

- Application: lifecycle، خطای domain و performance؛
- VPN Core: state، handshake result، protocol و error code؛
- Security: login، session reuse، device change و abuse signal.

فیلدها allowlist و versioned redaction دارند. مقصد، DNS، محتوا، raw IP، credential و Token ممنوع است. `audit_logs` فعالیت Admin را با actor، action، target، request ID، IP prefix و timestamp ثبت می‌کند و append-only/WORM export دارد.

## ۷ و ۸. Monitoring و Alerting

`monitor_resources` برای VPN server، API، DB، Queue و Worker و `metric_samples` برای CPU، RAM، bandwidth، latency، packet loss، online session count، API latency/error rate، DB pool و queue depth استفاده می‌شود.

`alert_rules` threshold/window/severity/destination را تعریف و `alert_incidents` رخدادهای deduplicated را مدیریت می‌کند. وضعیت Alert: Open → Acknowledged → Mitigated → Resolved. ارسال در `alert_deliveries` به Admin Panel، Telegram و Email با retry و suppression طوفان هشدار ثبت می‌شود.

## ۹. پشتیبانی کاربر

`support_tickets`, `support_messages`, `support_attachments` گفت‌وگوی کامل را مدل می‌کنند. وضعیت‌ها Open، Waiting Support، Waiting User، Resolved و Closed هستند. Permission مالکیت Ticket در API الزامی است و Agent فقط Queue مجاز خود را می‌بیند.

FAQ/Knowledge Base در `knowledge_base_articles` با locale، version، review و publish workflow نگهداری و از `GET /support/knowledge-base` خوانده می‌شود. Live Chat در نسخه نخست همان message stream Ticket است و بعداً transport بلادرنگ روی همان قرارداد افزوده می‌شود.

## ۱۰. Remote Help & Diagnostics

گزارش فقط با consent صریح و `POST /diagnostics/reports` ساخته می‌شود. Testهای مجاز: network reachability، DNS resolver class، server ping، packet loss، VPN state و device integrity. Hostname دلخواه، destination history و اطلاعات محرمانه پذیرفته نمی‌شود.

`diagnostic_reports` و `diagnostic_test_results` به Bug یا Ticket وصل و پس از expiry حذف می‌شوند. Agent نتیجه کدبندی‌شده را می‌بیند، نه داده خام شبکه.

## ۱۱ و ۱۲. Update و Release Management

`GET /app/update-policy` برای Stable/Beta سه حالت None، Optional و Forced می‌دهد. Forced فقط برای minimum supported version یا مشکل امنیتی Critical و با policy امضاشده مجاز است.

`app_releases` artifact digest، source commit، signing key version و rollback target را ثبت می‌کند. `release_deployments` promotion را از Development تا Production کنترل می‌کند. Production نیازمند:

- artifact امضاشده و hash معتبر؛
- Unit/Integration/UI/Security/Lint/Dependency/Performance/Accessibility gate موفق؛
- approval مستقل؛
- rollout درصدی، health gate و rollback آماده.

## ۱۳ تا ۱۵ (۱۳، ۱۴ و ۱۵). تست خودکار، CI/CD و کیفیت کد

`quality_runs` نوع و نتیجه هر Test/Scan و `quality_findings` fingerprint، severity و disposition را ثبت می‌کنند. CI پس از Push: Review → Build → Test → Security/Dependency Scan → Sign → APK/AAB artifact → Staging → Approval → Rollout.

قوانین merge: build reproducible، lint سبز، Unit/Integration/UI موفق، Critical/High security finding باز نداشته باشد، OpenAPI و migration validate شوند. Accepted risk باید owner، expiry و Audit داشته باشد. معماری Android ماژولار، Clean Architecture، SOLID و dependency boundary test دارد.

## ۱۶. Backup & Disaster Recovery

`backup_jobs` scope، retention و KMS key version؛ `backup_artifacts` storage خارج failure domain، digest و restore verification؛ و `restore_jobs` بازیابی isolated/staging/production را نگه می‌دارند.

Backup بدون Restore test موفق «سالم» محسوب نمی‌شود. `disaster_recovery_plans` مالک، RPO/RTO و runbook و `disaster_recovery_exercises` نتیجه تمرین را ثبت می‌کند. Production restore به dual approval، maintenance window و rollback نیاز دارد.

## ۱۷ و ۱۸. SOC و Anti-Abuse

`security_events` و `abuse_signals` تلاش Login، Token reuse، API abuse، device anomaly، account sharing، automation و traffic anomaly را با evidence حداقل‌شده ثبت می‌کنند. Signal حکم قطعی نیست.

`soc_cases/soc_case_events` بررسی Analyst را نگه می‌دارد. Actionها: challenge، rate limit، revoke session، restrict device، suspend service و block account. دو مورد آخر approval انسانی لازم دارند؛ همه actionها proportional، زمان‌دار و قابل appeal هستند (`abuse_actions`, `abuse_appeals`). Root detection یک risk signal است و به‌تنهایی دلیل block دائمی نیست.

## ۱۹. Feedback

`POST /feedback` دسته Bug، Suggestion، Complaint، Praise و Feature Request، rating و اجازه تماس را می‌گیرد. متن رمزگذاری و identity برای Product view به‌صورت پیش‌فرض جدا می‌شود. `user_feedback` workflow New → Reviewing → Planned/Declined → Completed/Closed دارد.

## ۲۰. Product Management Dashboard

`product_metric_daily` Growth، DAU/MAU، Retention، Revenue، Conversion و Churn را با cohort size نگه می‌دارد. `GET /admin/product/metrics` فقط aggregate می‌دهد؛ گروه‌های کوچک و dimensionهایی که re-identification ایجاد کنند suppress می‌شوند. منبع مالی Revenue دفتر کل است، نه event کلاینت.

## ۲۱. Documentation System

Architecture، OpenAPI، Database، Deployment، Security، Developer Guide و Runbook به‌عنوان code در Git نگهداری می‌شوند. `documentation_artifacts` owner، version، checksum، review و next review را ایندکس می‌کند. CI لینک‌ها، OpenAPI، schema و freshness را بررسی می‌کند؛ سند بدون owner منتشر نمی‌شود.

## ۲۲. Multi-language

localeهای هدف: فارسی، انگلیسی، عربی، ترکی و آلمانی. `localization_bundles` version، translator/reviewer، checksum و signature دارد. RTL/LTR، pluralization و قالب عدد/تاریخ بر locale است. متن Consent، Security و Purchase با app bundle fallback دارد تا remote copy نتواند آن را مبهم کند.

## ۲۳. Accessibility

Dynamic font، TalkBack/Screen Reader، High contrast، Reduce motion، focus order، touch target و semantic labels جزء release gate هستند. `accessibility_preferences` فقط ترجیح Sync کاربر و `accessibility_audits` گزارش هر Release را ثبت می‌کند. اتصال VPN هرگز فقط با رنگ اعلام نمی‌شود و Animation به Reduce Motion احترام می‌گذارد.

## ۲۴. Privacy

`GET/PUT /me/consents` receiptهای purpose/version را مدیریت می‌کند. Export و Delete از مسیرهای Privacy با identity verification، deadline و تاریخچه در `privacy_requests/privacy_request_events` انجام می‌شود. `data_retention_policies` برای هر data class purpose، legal basis، retention و deletion method دارد.

Privacy Dashboard باید داده‌های جمع‌شده، دلیل، نگهداری، دریافت‌کننده و امکان withdrawal/deletion را ساده نمایش دهد. حذف حساب session/device/profile را revoke و داده غیرالزامی را purge یا anonymize می‌کند؛ داده مالی لازم تا پایان الزام قانونی حداقل‌سازی و محدود می‌شود.

## ۲۵. Product Intelligence

`product_intelligence_models` purpose، version، feature manifest، evaluation، artifact digest و approval و `product_intelligence_recommendations` confidence، evidence window، explanation و expiry را ذخیره می‌کنند. کاربردها:

- تشخیص خوشه خطاهای تکراری؛
- پیشنهاد کیفیت/ظرفیت سرور و تکمیل ranking قطعی Smart Connect؛
- ریسک ریزش در cohortهای رضایت‌داده؛
- پیشنهاد Campaign به Product Manager.

AI فقط پیشنهاد می‌دهد. تغییر قیمت، block کاربر، ارسال Campaign یا rollout خودکار بدون rule/approval مستقل ممنوع است. `product_intelligence_decisions` تصمیم انسانی و دلیل را ثبت می‌کند. Drift، bias، false-positive و rollback برای هر مدل پایش می‌شود.

## ماتریس کنترل دسترسی

| حوزه | کاربر | Support | Developer | Ops | SOC | Product | Privacy |
|---|---:|---:|---:|---:|---:|---:|---:|
| Bug/Ticket شخصی | مالک | Queue مجاز | Issue بدون PII | خیر | Security-linked | Aggregate | در Request |
| Crash/Log خام | خیر | Diagnostic مجاز | Redacted | Operational | Security scope | Aggregate | Oversight |
| Remote config/Flag | Read resolved | خیر | Draft | Emergency kill | Security review | Draft/rollout | Privacy review |
| Release/Backup | خیر | خیر | Quality | Approve/operate | Security gate | Observe | Privacy gate |
| SOC/Abuse | Appeal | خیر | خیر | محدود | Case scope | Aggregate | Oversight |

تمام نقش‌های مدیریتی MFA، session کوتاه، least privilege، approval مناسب و Audit دارند.

## Retention پایه

| داده | مقدار پیش‌فرض | توضیح |
|---|---:|---|
| Raw analytics event | ۳۰ روز | سپس aggregate/suppress |
| Crash occurrence | ۹۰ روز | group بدون PII طولانی‌تر |
| Diagnostic report | ۱۴ روز | تمدید فقط با Ticket فعال و consent |
| Bug/Support attachment | ۳۰ روز پس از Closure | legal hold استثنا |
| Structured operational log | ۳۰ روز | Security تا ۱۸۰ روز |
| Raw metric sample | ۱۴ روز | aggregate تا ۱۳ ماه |
| Alert delivery | ۹۰ روز | incident summary بیشتر |
| Consent/Audit/Financial | طبق الزام | immutable و access-controlled |

مقادیر Production باید با jurisdiction و policy نهایی بازبینی شوند؛ افزایش retention بدون Privacy approval و نسخه جدید policy مجاز نیست.

## Definition of Done پلتفرم Enterprise

- API و DB migration validate و backward-compatible باشند؛
- هیچ مسیر manual VPN config import وجود نداشته باشد؛
- profile فقط entitlement-derived، device-bound، encrypted و short-lived باشد؛
- Critical/High finding باز، Crash regression بحرانی یا failed restore test وجود نداشته باشد؛
- Consent، Retention، Redaction، Audit و deletion job تست خودکار داشته باشند؛
- dashboardها روی aggregate privacy-safe ساخته شوند؛
- Runbook، rollback، owner، alert و SLO هر قابلیت آماده باشد؛
- نسخه Android در Staging با rollout gate و سپس Production تدریجی منتشر شود.
