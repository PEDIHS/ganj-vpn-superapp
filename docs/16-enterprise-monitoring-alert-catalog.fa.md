# Enterprise Monitoring و Alert Catalog

فایل `ops/observability/alerts.production.json` قرارداد machine-readable هشدارهای اولیهٔ Production است. این فایل Credential یا مقصد واقعی Notification ندارد؛ Routeها شناسهٔ منطقی‌اند و در Monitoring provider به مقصدهای RBAC-protected نگاشت می‌شوند. این Catalog جایگزین Provisioning واقعی Monitoring نیست و تا زمانی که Data source، Dashboard و Notification route در Staging آزمایش نشوند «فعال» محسوب نمی‌شود.

## اصول اجرایی

- هر Alert دارای Owner، Severity، Window، minimum sample، cooldown، route و Runbook است.
- `noDataBehavior=alert` است؛ قطع Telemetry مسیر حیاتی نباید وضعیت سبز جعلی بسازد.
- Dimensionها Allowlist و حداکثر شش موردند؛ User/Telegram ID، IP/hostname، purchase token و VPN config ممنوع‌اند.
- Route فقط شناسه‌ای مثل `oncall.vpn` است؛ URL، Email، Telegram token و webhook در Git قرار نمی‌گیرد.
- Alert بر اثر قابل اقدام تعریف می‌شود، نه هر Log یا Exception منفرد.
- Release alertها بر `app_version` و `release_channel` تفکیک می‌شوند تا regression cohort جدید پنهان نشود.

Validator استاندارد `.github/scripts/validate-alert-policy.py` این قواعد، یکتایی ID، وجود Runbook و Fail-closed بودن no-data را بدون Dependency خارجی کنترل می‌کند.

## Alertهای اولیه

| حوزه | Signal | آستانه | Severity | اقدام اول |
|---|---|---:|---|---|
| Android | crash-free users | کمتر از 99.2% | SEV-1 | Halt rollout، مقایسه با stable |
| Android | ANR sessions | بیشتر از 0.47% | SEV-1 | Halt rollout، بررسی main-thread/binder |
| VPN | connect success | کمتر از 90% | SEV-1 | حذف server cohort معیوب، leak smoke |
| VPN | connect P95 | بیشتر از 8s | SEV-2 | ظرفیت/route/protocol بررسی شود |
| Commerce | entitlement mismatch | هر مقدار بالاتر از صفر | SEV-1 | Fulfillment hold و reconciliation |
| Commerce | oldest verification | بیشتر از 180s | SEV-1 | Queue/provider health و idempotency |
| Control API | 5xx | بیشتر از 2% | SEV-1 | Rollback/traffic containment |
| Control API | P95 | بیشتر از 800ms | SEV-2 | DB/queue/dependency breakdown |
| Security | blocked auth abuse | بیشتر از 100 در 5 دقیقه | SEV-1 | rate-limit، token/session investigation |
| VPN fleet | probe failure | سه بار متوالی | SEV-2 | drain server، حفظ session سالم |
| Operations | verified backup age | بیشتر از 26h | SEV-1 | backup job/restore readiness |
| Release | certificate expiry | کمتر از 45 روز | SEV-2 | Rotation rehearsal و Play validation |

آستانه‌ها baseline اولیه‌اند و بعد از حداقل ۳۰ روز دادهٔ سالم با RFC و evidence تغییر می‌کنند. تغییر برای کاهش noise بدون تحلیل false-negative مجاز نیست.

## Provisioning Checklist

برای هر Rule پیش از فعال‌سازی:

- [ ] Signal واقعاً تولید و schema/units آن مستند است.
- [ ] Telemetry redaction و consent/data classification تأیید شده است.
- [ ] Dashboard لینک مستقیم به cohort/زمان Alert دارد.
- [ ] Route logical به On-call واقعی با RBAC و Audit نگاشت شده است.
- [ ] Notification متن ثابت، بدون PII/Secret و دارای Incident ID است.
- [ ] Runbook توسط Owner اجرا و Game Day موفق ثبت شده است.
- [ ] no-data با قطع عمدی exporter آزمایش شده است.
- [ ] grouping/dedup/cooldown و recovery notification تست شده‌اند.
- [ ] metric cardinality و هزینه در Budget قرار دارد.

## Release Monitoring Window

برای Candidate و Production، Dashboard حداقل این مقایسه را دارد:

```text
new build cohort vs previous stable
5m / 15m / 1h / 24h windows
crash-free + ANR + connect + reconnect + commerce + API dependency
```

در Rollout `5% -> 20% -> 50% -> 100%`، هر عبور Stage به Evidence snapshot و تأیید Release Owner نیاز دارد. SEV-0/1، entitlement mismatch، traffic/DNS leak یا no-data در Signal حیاتی Rollout را به‌صورت خودکار متوقف می‌کند؛ ادامه فقط بعد از Recovery و ثبت تصمیم امکان‌پذیر است.

## Notification Payload Allowlist

مجاز:

- Incident/Alert ID، Severity، environment و component؛
- زمان UTC، duration، threshold و مقدار aggregate؛
- app version/release channel، coarse region یا public server ID؛
- Dashboard/Runbook reference داخلی و Owner logical.

ممنوع:

- User/Telegram ID، IP/hostname خصوصی، URL query یا Authorization؛
- purchase token/receipt، VPN profile/config/UUID/password؛
- Crash stack خام، request/response body، attachment یا screenshot؛
- webhook، bot token، email شخصی یا secret provider configuration.

## Alert Lifecycle

```text
Firing -> Acknowledged -> Incident linked -> Mitigating -> Monitoring -> Resolved
```

SEV-0/1 بدون Incident ID Resolve نمی‌شود. Silence باید Actor، reason، expiry و Incident/change ID داشته باشد؛ Silence دائمی ممنوع است. تغییر threshold در زمان Incident به‌جای containment مجاز نیست مگر با تصمیم IC و ثبت Timeline.

## Testing و Review

- هر Release: synthetic smoke برای connect، purchase verification و API؛
- ماهانه: no-data و routing drill برای یک Rule غیرحیاتی؛
- فصلی: server outage، payment backlog و backup restore Game Day؛
- شش‌ماهه: signing/supply-chain tabletop؛
- سالانه: بازبینی SLO، retention، RBAC، provider DPA و هزینه.

نتیجهٔ Drill شامل زمان detect/ack/contain، false positive/negative، route failure و action item با Owner/deadline است. جزئیات اجرای رخداد در [Incident Response Runbook](13-incident-response-runbook.fa.md) و مرز داده در [Observability Privacy](14-observability-privacy.fa.md) تعریف شده‌اند.
