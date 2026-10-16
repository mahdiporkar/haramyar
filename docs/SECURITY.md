# امنیت

## ۱. کنترل‌های پیاده‌شده

### هویت و نشست
- Keycloak تنها IdP؛ کلاینت محرمانه با PKCE، بدون implicit و password grant، بدون service account.
- توکن فقط در Hive BFF؛ مرورگر کوکی HttpOnly دارد. درخواست‌های تغییر وضعیت توکن CSRF از `/auth/csrf` می‌گیرند.
- MFA اجباری کارکنان، brute force، سیاست رمز و محدودیت نرخ — جزئیات در [IDENTITY_AND_ACCESS.md](IDENTITY_AND_ACCESS.md).

### مجوز
- هر عملیات غیرعمومی در Hive BFF با resource/action بررسی می‌شود (پیش‌فرض: رد).
- سرویس‌ها JWT را مستقل اعتبارسنجی می‌کنند (`iss`، امضا، `azp`) و هدر هویت بدون توکن معتبر را نمی‌پذیرند؛ اتصال `sub` ↔ کاربر Hive سنجاق می‌شود.
- قواعد مالکیت در دامنه: کاربر فقط رزروهای خود را می‌بیند و تغییر می‌دهد (تست E2E «میزبان‌ها رزرو یکدیگر را نمی‌بینند»).
- جداسازی وظایف: اصل چهارچشم در انتشار؛ مدیر کسب‌وکار اجازهٔ تصمیم رزرو یا انتشار ندارد.

### gateway (nginx)
- تنها مبدأ عمومی؛ مسیرهای `/admin/*`، `/realms/master`، هر realm دیگر و `/actuator` پاسخ 404 می‌دهند.
- CSP سخت‌گیرانه (`script-src 'self' blob:` برای اجرای artifact با SRI توسط runtime Hive)، `frame-ancestors 'none'`، `X-Frame-Options: DENY`، `nosniff`، `Referrer-Policy`، `Permissions-Policy`، `COOP`.
- محدودیت نرخ: مسیرهای هویت ۲۰ درخواست در دقیقه، API سی درخواست در ثانیه به ازای IP.
- `X-Forwarded-Proto/Port` فقط در صورت وجود از proxy جلویی عبور داده می‌شوند.

### سرویس‌ها
- `server.error.include-message/stacktrace=never`؛ پاسخ خطای استاندارد Hive بدون جزئیات داخلی.
- `management` فقط `health` بدون جزئیات؛ از بیرون در دسترس نیست.
- `ddl-auto=validate`؛ همهٔ schemaها با Flyway. هر سرویس دیتابیس و کاربر جداگانه دارد.
- اعتبارسنجی ورودی در دامنه (طول، الگو، محدودهٔ تاریخ، شمارهٔ موبایل با libphonenumber).
- بارگذاری رسانه: بررسی magic bytes (JPEG/PNG/WebP)، سقف ۵ مگابایت، نام فایل تصادفی، سرو فقط برای پرونده‌های منتشرشده.
- رمز، توکن و شمارهٔ کامل در log نوشته نمی‌شوند؛ Correlation ID از starter Hive.
- `ServiceStartupGuard`: در پروفایل production بدون پیکربندی امنیتی کامل اجرا نمی‌شود.

### فرانت‌اند
- MFEها با SRI از دروازهٔ artifact هم‌مبدأ بارگذاری و در Shadow DOM ایزوله می‌شوند.
- Service Worker هیچ پاسخ `/api`، `/auth`، `/realms` یا خصوصی را کش نمی‌کند؛ خروج، کش و `sessionStorage` (`haramyar.*`) را پاک می‌کند (تست E2E).
- پیش‌نویس ویزارد رزرو فقط در `sessionStorage` با انقضای ۳۰ دقیقه و بدون اطلاعات هویتی.

### زنجیرهٔ تأمین و مخزن
- نسخه‌های دقیق وابستگی‌ها و imageها پین شده‌اند؛ Hive با کامیت پین شده است.
- `deployment/.env`، `.local/`، `vendor/`، `figma.zip` و خروجی‌های build در `.gitignore` هستند. CI فایل‌های env، کلید خصوصی و secret لفظی در realm را رد می‌کند و `npm audit --omit=dev --audit-level=high` اجرا می‌کند.
- اسرار محلی با [generate-env.mjs](../deployment/generate-env.mjs) به‌صورت تصادفی ساخته می‌شوند (فایل با مجوز 600). **هیچ secret production در GitHub قرار نمی‌گیرد.**

## ۲. ریسک‌های باز

| ریسک | شدت | اقدام پیشنهادی |
|---|---|---|
| شمارهٔ موبایل تأیید نمی‌شود | بالا | راه‌اندازی OTP پیامکی و فعال‌سازی `ROZEH_REQUIRE_VERIFIED_MOBILE` |
| بازیابی رمز بدون کانال | متوسط | کانال پیامک؛ تا آن زمان روند پشتیبانی با احراز حضوری |
| نبود CAPTCHA در ثبت‌نام | متوسط | CAPTCHA بومی یا OTP؛ پایش نرخ ثبت‌نام |
| اعتبارنامهٔ provisioning قدرتمند است | متوسط | نگهداری در secret store، چرخش، API محدود در Hive |
| تصاویر تزئینی از `images.unsplash.com` | کم | میزبانی محلی تصاویر و حذف دامنه از CSP |
| `style-src 'unsafe-inline'` | کم | لازمهٔ استایل Shadow DOM؛ با nonce/constructable stylesheets قابل حذف |
| مجوز `blob:` در `script-src` | کم | لازمهٔ runtime Hive برای اجرای artifact تأییدشده با SRI |
| HTTP داخلی بین سرویس‌ها | کم تا متوسط | در production شبکهٔ overlay خصوصی؛ در صورت نیاز mTLS |

## ۳. گزارش آسیب‌پذیری

آسیب‌پذیری‌ها را عمومی ثبت نکنید؛ به نگه‌دارندهٔ مخزن به‌صورت خصوصی اطلاع دهید.
