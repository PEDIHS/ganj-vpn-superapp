# Observability، Bug Tracking و پشتیبانی عملیاتی

این سند قرارداد عملیاتی مشترک Android، VPN Core، Backend، Admin Panel و Telegram Bot است. هدف، تشخیص سریع مشکل بدون جمع‌آوری تاریخچهٔ مرور، مقصدها، محتوای ترافیک یا کانفیگ خام VPN است.

## اصول غیرقابل مذاکره

1. هر Signal باید Owner، هدف، Retention و Runbook داشته باشد؛ Telemetry بدون مصرف‌کننده تولید نمی‌شود.
2. Log، Metric و Trace از یک `correlation_id` تصادفی و کوتاه‌عمر استفاده می‌کنند؛ شناسهٔ دائمی کاربر در Client Log قرار نمی‌گیرد.
3. Config URI، UUID/password، Token، کلید، DNS query، مقصد ترافیک، IP کامل، رسید و متن گفت‌وگوی پشتیبانی هرگز Log نمی‌شوند.
4. Crash/Analytics/Diagnostics تابع Consent و Remote Kill Switch مستقل هستند.
5. نبود Telemetry نباید اتصال VPN یا دسترسی کاربر به سرویس خریداری‌شده را مختل کند.

## معماری Signal

```text
Android events/crashes/diagnostics ─┐
VPN health aggregates ──────────────┼─> Ingestion API -> Queue -> Redaction -> Stores
Backend traces/metrics/logs ─────────┤                         ├-> Dashboards/Alerts
Server probes ───────────────────────┘                         └-> Issue/Support links
```

- **Metrics:** مقادیر تجمعی برای SLO و Alert؛ بدون Dimension با Cardinality نامحدود.
- **Traces:** مسیر API/Queue/Provider با Sampling؛ Body و Authorization حذف می‌شود.
- **Structured logs:** JSON، سطح مشخص، Event code ثابت و Redaction در مبدأ و Ingestion.
- **Crash reports:** Stack trace، build، مدل دستگاه و Breadcrumbهای Allowlist؛ بدون اطلاعات اتصال.
- **Product analytics:** Eventهای نسخه‌بندی‌شده و تابع Consent؛ برای Funnel و Retention، نه مانیتورینگ امنیتی.
- **Audit/Security events:** Append-only، دسترسی محدود، نگهداری جدا و غیرقابل تغییر برای مدیر عادی.

## قرارداد Log

هر رکورد عملیاتی فقط فیلدهای زیر را مجاز می‌داند:

| Field | توضیح |
|---|---|
| `timestamp` | UTC با دقت میلی‌ثانیه |
| `level` | `debug`, `info`, `warn`, `error` |
| `event_code` | نام ثابت و قابل جست‌وجو مانند `vpn.connect.failed` |
| `service` / `module` | تولیدکننده و زیرسامانه |
| `environment` | development، testing، staging یا production |
| `app_version` / `build` | نسخهٔ قابل نگاشت به Commit |
| `correlation_id` | شناسهٔ درخواست/عملیات، بدون PII |
| `account_key` / `device_key` | HMAC محیطی و قابل Rotation، فقط سمت Server |
| `server_id` | شناسهٔ داخلی؛ نه Host/IP/Config |
| `error_code` | کد Domain؛ نه متن Exception حساس |
| `duration_ms` | زمان عملیات در صورت نیاز |
| `outcome` | success، failure، cancelled، timeout |

Exceptionها قبل از ارسال از Header، URL query، Authorization، Cookie، IP، Profile و Payment payload پاک می‌شوند. متن Log آزاد در Production ممنوع است؛ پیام باید از Template نسخه‌بندی‌شده تولید شود.

## SLI و SLO اولیه

| حوزه | SLI | SLO سی‌روزه | Alert سریع |
|---|---|---:|---|
| Control API | درخواست موفق غیر 5xx | 99.9% | خطای 5xx بالاتر از 2% برای 5 دقیقه |
| API latency | P95 endpointهای read | کمتر از 350ms | بالاتر از 800ms برای 10 دقیقه |
| VPN connect | اتصال موفق در 60 ثانیه | 98% | افت 10 درصدی نسبت به baseline |
| VPN latency | P95 زمان اتصال | کمتر از 4s | بالاتر از 8s برای 10 دقیقه |
| Reconnect | reconnect موفق | 95% | کمتر از 85% برای 15 دقیقه |
| Commerce | Fulfillment دقیقاً یک Entitlement | 100% | هر duplicate یا mismatch |
| Crash-free | کاربران بدون Crash | 99.7% | افت زیر 99.2% در release جدید |
| Queue | سن قدیمی‌ترین Job حیاتی | کمتر از 60s | بیشتر از 180s |
| Server health | Probe موفق سرور قابل ارائه | 99.5% | سه Probe متوالی ناموفق |

Error Budget ماهانه مبنای توقف Feature rollout است. اگر 50% Budget در هفت روز مصرف شود، Release Owner rollout را متوقف و Reliability work را اولویت می‌دهد.

## Dashboardهای اجباری

### Product and subscription

- install → first open → guest/Telegram login → first connect → checkout → entitlement؛
- conversion، renewal، refund، churn و pending payment؛
- صدور Entitlement ناموفق یا تکراری؛
- Sync lag ربات و Backend.

### VPN reliability

- connect/reconnect success بر اساس build، protocol، network type، کشور و server؛
- P50/P95/P99 latency، packet loss و load؛
- error code، DNS protection و Kill Switch transitions؛
- Crash/ANR و مصرف باتری/حافظه بر اساس release.

### Platform and security

- API latency/error، DB pool، Queue lag، Cache hit، provider failure؛
- login failures، token reuse، device churn، rate-limit و entitlement abuse؛
- Admin action و تغییر Feature Flag/Remote Config؛
- backup age، restore drill و certificate/signing expiry.

## Alert Routing

Alert فقط وقتی ایجاد می‌شود که Action مشخص داشته باشد. هر Alert شامل `severity`، محیط، مؤلفه، زمان شروع، Dashboard، Runbook و correlation نمونه است.

| Severity | مقصد | پاسخ |
|---|---|---:|
| SEV-0/1 | On-call + Admin Panel + Telegram alert channel | فوری |
| SEV-2 | تیم Owner + Ticket | زیر 30 دقیقه کاری |
| SEV-3 | Backlog با Trend | روز کاری بعد |

هشدار Email مسیر پشتیبان است. Notification نباید Secret یا دادهٔ کاربر داشته باشد. Deduplication، cooldown و grouping از Alert storm جلوگیری می‌کند.

## User Bug Report

فرم داخل اپ با اجازهٔ صریح کاربر می‌تواند این موارد را بفرستد: نسخه/Build، مدل دستگاه، Android، network type، وضعیت کلی اتصال، `server_id`، error code، timestamp، correlation ID و شناسهٔ Crash. توضیح، Screenshot، Screen Recording و Attachment اختیاری‌اند و قبل از Upload هشدار حریم خصوصی و Preview دارند.

ممنوع: Log خام Xray، کانفیگ، Clipboard، notification محتوا، لیست اپ‌ها، IP کامل، DNS query، مقصد، Telegram token و جزئیات بانکی. Attachment با malware scan، MIME allowlist، محدودیت اندازه، encryption at rest و URL کوتاه‌عمر ذخیره می‌شود.

## Issue Dashboard و Lifecycle

```text
New -> Investigating -> Assigned -> Fixing -> Testing -> Released -> Closed
                         \-> Duplicate / Cannot reproduce / Accepted risk
```

Issue شامل `bug_id`، severity، status، affected build، module، impact، assignee، first/last seen، crash group، rollout و release fix است. Severity از اثر و دامنه تعیین می‌شود:

- **Critical:** قطع گسترده، امنیت، نشت یا از دست‌رفتن داده، خطای مالی؛
- **High:** اتصال/خرید اصلی مختل، مصرف شدید منابع، کاربران زیاد؛
- **Medium:** رفتار اشتباه با workaround، UI مهم یا accessibility؛
- **Low:** ایراد جزئی یا polish.

Issue خودکار هرگز مستقیماً اطلاعات Crash را Public نمی‌کند. Issue عمومی فقط خلاصهٔ Sanitized و لینک داخلی دارد.

## Diagnostic Report

Diagnostic محلی اجرا و قبل از ارسال نمایش داده می‌شود:

1. دسترسی شبکه و captive portal؛
2. DNS resolver outcome بدون ذخیرهٔ queryهای کاربر؛
3. latency/packet loss به Probeهای اختصاصی؛
4. وضعیت VpnService، permission و foreground notification؛
5. وضعیت Entitlement، زمان دستگاه و certificate chain؛
6. فضای دیسک، power saver و network restrictions؛
7. error codeهای Allowlist در بازهٔ حداکثر 15 دقیقه.

Bundle با کلید یک‌بارمصرف رمز می‌شود، حداکثر 7 روز نگه داشته و پس از بسته‌شدن Ticket حذف می‌شود. Support Agent فقط با RBAC و Audit به آن دسترسی دارد.

## Analytics Governance

هر Event قبل از Merge در Event Catalog ثبت می‌شود: Owner، purpose، trigger، properties، consent category، retention و deletion behavior. Naming به شکل `domain.object.action.vN` است. Properties ناشناخته در Ingestion رد می‌شوند.

A/B test برای Offer و UX مجاز است؛ رفتار امنیتی، رمزنگاری، Kill Switch، پرداخت و Privacy Consent موضوع Experiment نیست. Feature Flag دارای owner، expiry، default امن و kill switch است.

## Retention اولیه

| داده | Retention | دسترسی |
|---|---:|---|
| Operational metrics | 90 روز؛ aggregate بلندمدت | Operations |
| Sampled traces | 14 روز | Engineering/Operations |
| Redacted application logs | 30 روز | Engineering |
| Crash reports | 90 روز یا دو release | Mobile/QA |
| Product analytics | 13 ماه با Consent | Product محدود |
| Security/Admin audit | 12 ماه یا الزام قانونی بیشتر | Security |
| Diagnostic bundle | حداکثر 7 روز | Support پرونده |

درخواست حذف حساب، داده‌های قابل انتساب را طبق Privacy Policy حذف/ناشناس می‌کند؛ Audit قانونی فقط با مبنای مستند نگهداری می‌شود.

## Definition of Done

- Dashboard و Alert برای مسیر حیاتی وجود دارد؛
- error code و correlation از Client تا Backend قابل ردیابی است؛
- Redaction، consent و deletion تست شده‌اند؛
- Alert دارای Owner و Runbook و بدون PII است؛
- failure telemetry خود محصول را مختل نمی‌کند؛
- تست synthetic و outage simulation evidence تولید کرده است.
