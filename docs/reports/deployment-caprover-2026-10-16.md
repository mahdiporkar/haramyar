# گزارش استقرار آزمایشی روی CapRover — ۲۰۲۶-۱۰-۱۶

**وضعیت: موفق (محیط آزمایشی).** HaramYar در `https://haramyar.com` با پوستهٔ Hive، هر دو میکروفرانت‌اند، سرویس‌های backend و Keycloak مستقر شد و با مرورگر واقعی آزمایش شد. استقرار production با کاربران واقعی انجام نشده است.

## دسترسی و روش

- SSH با `codexdeploy@141.11.45.119:9011` (کلید ed25519، بررسی سخت‌گیرانهٔ کلید میزبان) برقرار شد. کاربر عضو گروه `sudo` است اما sudo رمز می‌خواهد و عضو گروه `docker` نیست؛ از SSH فقط برای ممیزی فقط‌خواندنی (حافظه، دیسک، فرایندها) استفاده شد.
- استقرار از طریق API کاپ‌روور با نشست CLI موجود (`husbantech-prod`) انجام شد؛ CapRover imageها را روی سرور ساخت و در registry پیکربندی‌شدهٔ خود (GHCR) push کرد.
- اپ‌های زیرساختی (`haramyar-postgres`، `-redis`، `-rabbitmq`، `-object-storage`، `-openfga`، `-keycloak`) از تلاش قبلی وجود داشتند؛ Keycloak به‌دلیل realm قدیمی (شناسهٔ provider بلندتر از ۳۶ نویسه) در حلقهٔ خطا بود و از کامیت فعلی دوباره مستقر شد. RabbitMQ بدون entrypoint ساخت کاربر مستقر شده بود و اصلاح شد.

## وضعیت پایه و اپ‌های غیرمرتبط

پیش از استقرار ۱۳ اپ غیرمرتبط ثبت شد (husbantech-web، hive-redis، hive-openfga، hive-authorization، hive-bff، university-api، erp، auth، hive-console، hive-db، hive-graph-db، erp-db، auth-db). پس از استقرار همهٔ آن‌ها با همان نسخه و تعداد نمونه بدون تغییر بودند و `https://husbantech.com` پاسخ 200 داد. هیچ volume، دامنه، شبکه یا تنظیم nginx دیگری تغییر نکرد.

## اپ‌های مستقرشده

`haramyar-web` (عمومی، `haramyar.com`)، `haramyar-keycloak`، `haramyar-hive-authorization`، `haramyar-hive-bff`، `haramyar-booking-api`، `haramyar-martyrs-api`، `haramyar-shell-service`، `haramyar-booking-frontend`، `haramyar-martyrs-frontend`، `haramyar-public-web`، `haramyar-postgres`، `haramyar-rabbitmq`، `haramyar-object-storage`، `haramyar-redis`، `haramyar-openfga`، و `haramyar-bootstrap` (یک‌باره، اکنون ۰ نمونه). ماژول‌های فعال در Hive: `rozeh-booking@1.0.1`، `martyrs@1.0.1`.

## نتایج آزمون روی محیط مستقر

| آزمون | نتیجه |
|---|---|
| E2E کامل (`npm run test:e2e` با `HARAMYAR_E2E_BASE=https://haramyar.com`) | ۱۰ موفق، ۱ رد‌شده طبق طراحی (تست املای تکراری ثبت‌نام، چون ثبت‌نام غیرفعال است)، ۰ شکست |
| بدون نشست / توکن جعلی / هدر `X-Hive-User-Id` جعلی / کوکی نشست جعلی | 401 |
| POST بدون CSRF | 403 |
| `/admin/*`، `/realms/master`، `/actuator`، `/console` | 404 |
| HTTP → HTTPS | 302 به `https://haramyar.com/` |
| کوکی نشست | `HIVE_SESSION; Secure; HttpOnly; SameSite=Lax`؛ ورود با PKCE (`S256`) |
| هدرهای امنیتی | CSP، HSTS، X-Frame-Options، nosniff، Referrer-Policy، Permissions-Policy |
| ماندگاری پس از راه‌اندازی مجدد postgres، booking-api و shell-service | ۳ رزرو با همان شناسه و وضعیت و ۸ اعلان، پیش و پس از restart یکسان |
| ذخیره‌ساز اشیا | بارگذاری PNG توسط ویراستار (200) و دریافت دوباره (200، `image/png`) با بایت‌های یکسان |
| RabbitMQ | اعلان پذیرش رزرو از طریق outbox → RabbitMQ → shell-service در E2E دریافت شد |
| Rollback | `haramyar-web` به image نسخهٔ ۳ برگشت (بدون HSTS) و سپس به image نسخهٔ ۴ بازگشت (HSTS برقرار)؛ سایت در هر دو حالت 200 |

E2E در اجرای اول و دوم هر بار یک شکست داشت: یکی کندی پاسخ صفحه زیر فشار حافظه، دیگری تداخل اعلان قدیمی حساب آزمایشی ثابت. دومی با افزودن `data-subject-id` به اعلان‌ها و هدف‌گیری رزرو همان اجرا رفع شد و اجرای سوم بدون شکست بود.

## منابع سرور پس از استقرار

RAM کل 3916 MiB، حدود 500 MiB در دسترس؛ swap حدود 1.6 از 2 GiB؛ دیسک ۷۷٪ (6.6 GiB آزاد). بار CPU پایین (حدود ۹۰٪ idle). محیط پایدار است اما حاشیهٔ حافظه کم است.

## تغییرات کد برای سازگاری استقرار

- gateway: upstream BFF متغیر شد تا در نبود BFF بالا بیاید (رفع بن‌بست discovery)؛ `/console` در نبود کنسول 404؛ HSTS.
- Keycloak: `realm-policy.mjs` برای غیرفعال‌سازی ثبت‌نام و بازیابی رمز؛ مشتریان آزمایشی محدود.
- CapRover: اپ bootstrap، entrypoint RabbitMQ، `KC_CACHE=local`، ثبت تاریخچه و دستور `rollback`/`stop`، پشتیبانی CI.
- E2E: حالت اجرای روی محیط مستقر.
- `.github/workflows/deploy.yml`.

## موارد باز

- secretهای GitHub (`CAPROVER_URL`، `CAPROVER_TOKEN`، `HARAMYAR_DEPLOY_SECRETS`) تعریف نشده‌اند؛ workflow استقرار تا آن زمان رد می‌شود. وضعیت اجرای CI روی GitHub از این محیط قابل مشاهده نبود (مخزن خصوصی و بدون توکن API).
- تأیید پیامکی، بازیابی رمز و ثبت‌نام آزاد پیاده نشده‌اند.
- ظرفیت حافظه محدود است.
