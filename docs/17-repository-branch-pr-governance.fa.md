# Ganj VPN — Repository, Branch & Pull Request Governance

> **وضعیت:** Canonical / Mandatory  
> **مخاطب:** تمام Developerها، Maintainerها، AI Agentها و Automationهایی که روی این Repository تغییر ایجاد می‌کنند.  
> **هدف:** حفظ Repository Hygiene، جلوگیری از Branch Sprawl، کاهش Merge Risk و نگه‌داشتن `main` به‌عنوان تنها Source of Truth اجرایی.

---

# 1. اصل توسعه

Ganj VPN از مدل **Trunk-Based Development با Short-Lived Feature Branches** استفاده می‌کند.

- `main` تنها Branch بلندمدت و مرجع نهایی کد است.
- Feature/Fix/Docs branchها موقت هستند.
- Branch محل آرشیو تاریخچه نیست؛ Git Commit/PR history این وظیفه را دارد.
- هر تغییر باید کوچک، قابل Review، قابل Test و قابل Merge باشد.
- هدف تعداد Branch زیاد یا Phaseهای متعدد نیست؛ هدف Delivery امن و قابل ردیابی است.

---

# 2. Branchهای مجاز

## Long-lived

فقط:

```text
main
```

Branchهای دائمی مثل `develop`, `staging`, `phase-7`, `backend`, `android` یا مشابه، بدون تصمیم معماری رسمی نباید ساخته شوند.

## Short-lived

قالب استاندارد:

```text
feat/<scope>
fix/<scope>
docs/<scope>
refactor/<scope>
test/<scope>
chore/<scope>
hotfix/<scope>
```

نمونه:

```text
feat/android-telegram-login
fix/vpn-reconnect-race
docs/repository-governance
refactor/auth-session-vault
test/play-billing-rtdn
```

استفاده از `phase-N/...-v1/v2/v3` برای کارهای جدید **پیش‌فرض نیست**. این الگوی تاریخی فقط در Branchهای قدیمی دیده می‌شود و نباید بدون دلیل ادامه پیدا کند.

---

# 3. قبل از ساخت Branch

Agent/Developer موظف است:

1. آخرین `main` را بررسی کند.
2. PRهای Open را جست‌وجو کند.
3. Branchهای مرتبط را جست‌وجو کند.
4. بررسی کند Task از قبل در Branch/PR دیگری در حال انجام نباشد.
5. اگر Branch فعال مناسب وجود دارد، همان را ادامه دهد و Branch موازی duplicate نسازد.
6. اگر Branch قدیمی بسته/merged/superseded است، از آن به‌عنوان Base استفاده نکند.
7. Branch جدید را از latest `main` بسازد مگر PR فعال صراحتاً Base دیگری داشته باشد.

**ساخت Branch جدید بدون این بررسی ممنوع است.**

---

# 4. Scope هر Branch

هر Branch باید یک هدف روشن داشته باشد.

مجاز:

```text
feat/android-telegram-login
```

نامناسب:

```text
phase-7/everything-v4
feat/android-backend-ui-security
```

یک Branch نباید چند Workstream مستقل را فقط برای کاهش تعداد PRها با هم ترکیب کند.

اگر دو تغییر:

- مستقل Deploy/Test می‌شوند؛
- Reviewerهای متفاوت دارند؛
- Risk متفاوت دارند؛
- یا Rollback مستقل لازم دارند؛

باید PR مستقل داشته باشند.

---

# 5. عمر Branch

Branch Feature باید **کوتاه‌عمر** باشد.

قاعده:

- Branch باید فقط تا زمان تکمیل همان Task/PR زنده بماند.
- Branch نباید به Backlog دائمی تبدیل شود.
- اگر کار متوقف شد، وضعیت باید در Issue/PR ثبت شود، نه اینکه Branch ماه‌ها به‌عنوان حافظه نگه داشته شود.
- اگر Branch به‌شدت از `main` عقب افتاد، ابتدا دلیل عقب‌افتادگی بررسی شود؛ Merge کورکورانه ممنوع است.

اگر Fresh-Port لازم شد:

1. Branch جدید از latest `main` ساخته شود.
2. فقط Commit/Fileهای لازم منتقل شوند.
3. PR قبلی `Superseded` علامت‌گذاری و بسته شود.
4. Branch قبلی پس از اطمینان از حفظ کد لازم حذف شود.

ساخت `v2`, `v3`, `v4` بدون بستن و Cleanup نسخه قبلی ممنوع است.

---

# 6. Pull Request First

تغییر Runtime/Code/Config/Workflow مستقیماً روی `main` Commit نمی‌شود، مگر Emergency با مجوز صریح Maintainer.

Flow استاندارد:

```text
latest main
   ↓
short-lived branch
   ↓
commits
   ↓
Pull Request
   ↓
CI / Review / Security Gates
   ↓
Merge
   ↓
Delete branch
```

PR باید Scope کوچک و قابل Review داشته باشد. PR غول‌پیکر فقط وقتی پذیرفته است که شکستن آن باعث از بین رفتن Atomicity یا ایجاد Risk بیشتر شود.

---

# 7. Merge Policy

پیش‌فرض پروژه:

- `Squash Merge` برای Feature/Fix/Docs PRهای معمولی.
- Merge فقط پس از سبز بودن Gateهای مرتبط.
- PR نباید صرفاً برای «جلو رفتن درصد پروژه» Merge شود.
- Conflict باید آگاهانه Resolve شود؛ حذف تست/Guard برای Merge ممنوع است.
- اگر Branch Head نسبت به Review نهایی تغییر معنادار کرده، CI/Review باید دوباره معتبر باشد.

`Merge Commit` فقط برای مواردی استفاده شود که حفظ topology چند-commit واقعاً ارزش مهندسی دارد.

---

# 8. Branch Cleanup — اجباری

پس از Merge:

- Head Branch باید حذف شود.
- PR history برای Audit کافی است.
- Branch Merge‌شده نباید برای «شاید بعداً لازم شود» باقی بماند.

پس از Supersede/Abandon:

- ابتدا مطمئن شو هیچ Commit منحصربه‌فرد لازم از دست نمی‌رود.
- PR را با دلیل واضح Close کن.
- در PR جانشین Reference بده.
- سپس Branch قدیمی را حذف کن.

Branch فقط در این شرایط می‌تواند موقتاً باقی بماند:

- PR فعال دارد؛
- Release/Hotfix جاری به آن وابسته است؛
- Evidence مشخصی وجود دارد که هنوز Commit منحصربه‌فرد و موردنیاز در آن است.

«ممکن است بعداً لازم شود» دلیل معتبر نیست.

---

# 9. Branch Count Hygiene

تعداد Branch معیار کیفیت پروژه نیست.

Repository باید در حالت عادی فقط شامل:

- `main`؛
- Branchهای PR فعال؛
- Branchهای Automation موقت مثل Dependabot؛
- Release/Hotfix واقعاً جاری؛

باشد.

اگر Branch بدون PR فعال، بدون Work جاری و بدون Commit موردنیاز باقی مانده باشد، باید Candidate Cleanup محسوب شود.

هدف عملیاتی: Branch list باید آن‌قدر کوچک باشد که Maintainer با نگاه به آن بتواند Work جاری را تشخیص دهد.

---

# 10. Parallel AI Agents

چند Agent می‌توانند همزمان کار کنند، اما Parallelism نباید Branch Sprawl بسازد.

هر Agent قبل از شروع:

1. Open PRها را بررسی می‌کند.
2. Active Branch مرتبط را پیدا می‌کند.
3. Ownership Task را از Issue/PR مشخص می‌کند.
4. اگر همان Scope توسط Agent دیگری در حال انجام است، Branch دوم نمی‌سازد.
5. اگر Scope مستقل است، Branch کوتاه‌عمر مستقل می‌سازد.

Agentها نباید Branch دیگری را Force Push یا Rewrite کنند مگر Task صریحاً همان Branch را واگذار کرده باشد.

---

# 11. Commit Policy

Commit باید:

- هدف مشخص داشته باشد؛
- Secret/Binary/Generated artifact ناخواسته نداشته باشد؛
- Build را بی‌دلیل در وضعیت شکسته رها نکند؛
- Message قابل فهم داشته باشد.

قالب پیشنهادی:

```text
feat(android): complete telegram callback validation
fix(vpn): bound reconnect retry loop
docs(repo): define branch lifecycle
```

Commitهای آزمایشی مثل `test`, `again`, `final2`, `fix fix` نباید به شکل نهایی وارد `main` شوند؛ Squash Merge باید تاریخچه نهایی را تمیز کند.

---

# 12. PR Contract

هر PR حداقل باید روشن کند:

- **Outcome:** چه Gap بسته شد؟
- **Scope:** چه بخش‌هایی تغییر کردند؟
- **Security/Privacy Impact:** اگر مرتبط است.
- **Data/Migration Impact:** اگر مرتبط است.
- **Tests & CI:** چه چیزی واقعاً اجرا شده؟
- **Remaining Boundary:** چه چیزی هنوز کامل نیست؟
- **Supersedes:** اگر Branch/PR قبلی را جایگزین می‌کند.

هیچ Agentی نباید تستی را که اجرا نشده «Pass» اعلام کند.

---

# 13. Stale Branch Handling

اگر Branch قدیمی با کار ارزشمند پیدا شد:

- مستقیم Merge نکن.
- ابتدا `main...branch` را Compare کن.
- Commitهای منحصربه‌فرد را شناسایی کن.
- Conflict و تغییر معماری بعد از آن Branch را بررسی کن.
- در صورت Risk بالا، Fresh-Port روی latest `main` انجام بده.
- بعد از Merge جانشین، Branch قدیمی را Cleanup کن.

این Rule مخصوصاً برای Security، Auth، Billing، VPN runtime و Database migration اجباری است.

---

# 14. Release Branches

پیش‌فرض: Release از Commit/Tag روی `main` ساخته می‌شود.

`release/*` فقط وقتی مجاز است که Release Hardening واقعی نیازمند Freeze جداگانه باشد.

اگر ساخته شد:

- عمر آن محدود به همان Release است؛
- Feature جدید وارد آن نمی‌شود؛
- Fix لازم باید به `main` هم برگردد؛
- بعد از پایان Release حذف می‌شود؛
- Tag منبع تاریخچه Release است، نه Branch دائمی.

---

# 15. Emergency Hotfix

Hotfix:

```text
hotfix/<incident-scope>
```

از آخرین Production baseline یا `main` معتبر ساخته می‌شود.

حتی Hotfix باید تا حد ممکن:

- PR؛
- حداقل تست مرتبط؛
- Security review؛
- Post-incident documentation؛

داشته باشد.

بعد از Merge، Branch حذف می‌شود.

---

# 16. ممنوعیت‌ها

برای تمام Agentها/Developerها ممنوع است:

- ساخت Branch جدید بدون جست‌وجوی Branch/PR موجود؛
- نگه‌داشتن Branch Merge‌شده بدون دلیل؛
- ساخت زنجیره `v2/v3/v4` و رها کردن نسخه‌های قبلی؛
- استفاده از Branch stale به‌عنوان Base صرفاً چون قبلاً روی آن کار شده؛
- Merge Branch stale بدون Compare؛
- Direct Push به `main` برای Feature معمولی؛
- Force Push روی Branch دیگران بدون مجوز؛
- استفاده از Branch به‌عنوان Archive؛
- ترکیب چند Workstream نامرتبط در یک PR بزرگ؛
- حذف تست/Guard برای سبزکردن CI؛
- Merge قبل از نتیجه Gateهای اجباری.

---

# 17. Checklist اجباری Agent

## شروع Task

```text
[ ] latest main checked
[ ] open PRs checked
[ ] related branches checked
[ ] duplicate work ruled out
[ ] correct short-lived branch selected/created
```

## قبل از PR

```text
[ ] scope is focused
[ ] tests added/updated
[ ] docs/contracts synced if required
[ ] secrets/artifacts not committed
[ ] branch is based on an acceptable main baseline
```

## قبل از Merge

```text
[ ] CI gates green
[ ] security/release gates relevant to scope green
[ ] PR Remaining Boundary accurate
[ ] stale/superseded predecessor identified
```

## بعد از Merge

```text
[ ] merged head branch deleted
[ ] superseded branch/PR cleaned up
[ ] active branch list still represents real work
[ ] next agent can continue from main without historical branch knowledge
```

---

# 18. Source of Truth

در تعارض بین Branch قدیمی و `main`:

**`main` برنده است، مگر Evidence صریح خلاف آن وجود داشته باشد.**

در تعارض بین توضیح قدیمی PR و Implementation فعلی:

**Implementation + tests روی latest `main` مرجع است.**

Branch history برای تحقیق مفید است، اما Source of Truth محصول نیست.

---

# 19. قانون نهایی

هر Agent باید Repository را بعد از کار **تمیزتر یا حداقل به همان اندازه تمیز** تحویل دهد.

یک Contribution حرفه‌ای فقط کد صحیح نیست؛ شامل Branch lifecycle صحیح، PR قابل Review، CI معتبر، Documentation هماهنگ و Cleanup پس از Merge نیز هست.