## Summary

شرح کوتاه تغییر و مسئله‌ای که حل می‌کند.

## Risk and rollback

- Risk level: `low / medium / high / critical`
- Rollback or feature-flag plan:
- User/data/payment/VPN impact:

## Evidence

- [ ] تست‌های مرتبط اضافه یا به‌روزرسانی شده‌اند.
- [ ] Unit test، lint و build محلی یا CI موفق است.
- [ ] هیچ Secret، کانفیگ واقعی، APK/AAB، Keystore یا دادهٔ کاربر Commit نشده است.
- [ ] تغییر API، دیتابیس، Analytics یا Privacy مستندسازی شده است.
- [ ] برای تغییر حساس، Threat Model و مسیر Rollback بررسی شده است.
- [ ] Screenshot یا ویدئو برای تغییر UI پیوست شده است.

## Release notes

تغییر قابل مشاهده برای کاربر، نیاز به Migration و Feature Flag را بنویسید.
