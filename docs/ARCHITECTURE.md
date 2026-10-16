# معماری حرم‌یار

## ۱. مرز با Hive

HaramYar فقط از قابلیت‌های منتشرشدهٔ Hive استفاده می‌کند و هیچ‌کدام را تکرار نمی‌کند:

| قابلیت | مالک | استفادهٔ HaramYar |
|---|---|---|
| ورود OIDC (code + PKCE)، نشست `HIVE_SESSION`، CSRF | Hive BFF | پوسته از `@hive-platform/auth` استفاده می‌کند؛ هیچ توکنی به مرورگر نمی‌رسد. |
| پراکسی پویا `/api/routes/{route}/**` با عملیات PUBLIC / HYBRID / AUTHENTICATED | Hive BFF + Authorization | سه route ثبت شده: `rozeh`، `martyrs`، `haramyar` ([integrations/hive/solution/services](../integrations/hive/solution/services)). BFF توکن کاربر را با `FORWARD_TOKEN` و شناسهٔ کاربر Hive را در `X-Hive-User-Id` به سرویس می‌فرستد. |
| کاتالوگ منابع، نقش‌ها، grantها و بررسی مجوز (OpenFGA) | Hive Authorization | منابع از resource manifest هر ماژول و منابع دستی [access.json](../integrations/hive/access.json)؛ نقش‌ها و grantها با Hive CLI و API مدیریت ثبت می‌شوند. |
| دروازهٔ artifact میکروفرانت‌اند با SRI و `MfeRuntime` | Hive BFF + SDK | هر ماژول `entry.js` خود را در مسیر نسخه‌دار `/mfe/<version>/entry.js` سرو می‌کند؛ مرورگر آن را از `/api/mfe/{module}/{version}/entry.js` هم‌مبدأ می‌گیرد. |
| Spring Boot starter (Correlation ID، خطاهای استاندارد، گارد production) | Hive | همهٔ سرویس‌های Java از `hive-spring-boot-starter` استفاده می‌کنند. |

Hive هیچ کد، منبع یا دادهٔ HaramYar ندارد. نسخهٔ Hive با کامیت در [hive.lock.json](../hive.lock.json) پین شده و SDK به‌صورت dist در `vendor/hive-sdk` (gitignore) پیوند می‌شود.

## ۲. توپولوژی

```
مرورگر ──HTTPS──► gateway (nginx + پوستهٔ PWA)  ← تنها مبدأ عمومی
                   ├─ /api, /auth, /oauth2 ──► Hive BFF ──► Hive Authorization ──► OpenFGA
                   │                              │ (FORWARD_TOKEN, X-Hive-User-Id)
                   │                              ├──► rozeh-booking   (PostgreSQL: rozeh)
                   │                              ├──► martyrs         (PostgreSQL: martyrs, S3)
                   │                              └──► shell-service   (PostgreSQL: shell)
                   ├─ /realms/haramyar/(صفحات ورود/ثبت‌نام) ──► Keycloak (PostgreSQL: keycloak)
                   ├─ /martyr/**, /sitemap.xml, /robots.txt ──► public-web (SSR) ──► BFF (عملیات PUBLIC)
                   └─ /admin/*, /realms/master, /actuator ──► 404
rozeh-booking ──outbox──► RabbitMQ ──► shell-service (اعلان‌ها)
```

- Admin Console و realm `master` در Keycloak از gateway در دسترس نیستند.
- سرویس‌های کسب‌وکار، دیتابیس‌ها، RabbitMQ و S3 هیچ پورت عمومی ندارند (شبکهٔ `haramyar_internal`).
- در محیط محلی BFF فضای شبکهٔ gateway را به اشتراک می‌گذارد تا issuer برای مرورگر و BFF یکسان (`http://localhost:8080/realms/haramyar`) باشد.

## ۳. ساختار هر سرویس کسب‌وکار (DDD + Hexagonal)

```
domain/          aggregate، value object، سیاست‌ها، رویدادهای دامنه — بدون Spring/JPA
application/     command/query handlerها، portها، viewها (با allowedActions)
infrastructure/  JPA (موجودیت‌های جدا از دامنه)، adapterها، پیکربندی، messaging
interfaces/      کنترلرهای REST: /public/**، /my/**، /coordination/**، /editorial/**
```

قواعد معماری با ArchUnit تست می‌شوند (دامنه به Spring/JPA/infrastructure وابسته نیست؛ interfaces به infrastructure دسترسی مستقیم ندارد).

### رزرو روضه

- Aggregate `RozehBooking` با چرخهٔ `REQUESTED → ACCEPTED → CONFIRMED → COMPLETED` و `REJECTED` / `CANCELLED`.
- سیاست‌ها: `BookingActorPolicy` (چه نقشی چه عملی)، `BookingConfirmationPolicy`، `BookingCancellationPolicy`، `CeremonyCatalog`، `BookingAvailabilityPolicy` — همه از [سیاست‌های موقت](BUSINESS_DECISIONS.md#۲-سیاستهای-موقت-رزرو-روضه-haramyarrozeh) تغذیه می‌شوند.
- قفل خوش‌بینانه (`version`)، قید exclusion از نوع GiST برای جلوگیری از هم‌پوشانی زمانی روضه‌خوان، تاریخچهٔ append-only با trigger، کد پیگیری تصادفی امن `RZ-XXXX-XXXX`.
- پاسخ‌ها برای هر رزرو `allowedActions` محاسبه‌شده در سرور دارند تا UI هرگز قاعدهٔ کسب‌وکار را حدس نزند.

### معرفی شهدا

- Aggregate `MartyrProfile` با چرخهٔ `DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED`، داستان‌ها، منابع و رسانه.
- `EditorialPolicy(fourEyes)`: منتشرکننده نباید ارسال‌کننده باشد. انتشار بدون منبع ممکن نیست.
- رسانه: بررسی magic bytes، محدودیت حجم، ذخیره در S3 (در محیط محلی SeaweedFS) و سرو از طریق عملیات PUBLIC فقط برای پرونده‌های منتشرشده.
- جست‌وجوی متن با نرمال‌سازی حروف فارسی/عربی (ی/ي، ک/ك، اعراب) در `SearchText`.

### shell-service

- مناسبت‌ها و تم فعال (`/public/theme`)، مدیریت مناسبت با action `configure`.
- Onboarding: در اولین ورود، نقش پیش‌فرض `haramyar-user` را از طریق API مدیریت Hive با اعتبارنامهٔ provisioning اختصاص می‌دهد (پاسخ 409 یعنی قبلاً انجام شده). هیچ نقش پلتفرمی Hive اختصاص داده نمی‌شود.
- اعلان‌ها: مصرف رویدادهای `haramyar.booking.*.v1` و نمایش در پوسته.

## ۴. پیام‌رسانی قابل‌اعتماد (`shared/service-kit`)

- **Outbox تراکنشی:** رویداد یکپارچگی در همان تراکنش aggregate نوشته می‌شود (`OutboxWriter` با propagation `MANDATORY`).
- **Publisher:** `FOR UPDATE SKIP LOCKED` با lease، publisher confirm، تلاش مجدد نمایی.
- **Inbox:** حذف تکرار بر اساس `eventId` در همان تراکنش پردازش.
- **Retry و DLQ:** صف‌های تأخیری با TTL و dead-letter بازگشتی؛ پس از پایان تلاش‌ها یا `PermanentProcessingException` پیام به DLQ می‌رود. پیام‌های مسیرنیافته به alternate exchange می‌روند.
- نام رویدادها نسخه‌دار است (`haramyar.booking.accepted.v1`) و payload شامل اطلاعات شخصی غیرضروری نیست.

## ۵. فرانت‌اند

- **پوسته** ([apps/haramyar-shell](../apps/haramyar-shell)): مسیریاب، `ModuleHost` که با `routeAccess` و `MfeRuntime` ماژول را mount می‌کند، هدر/ناوبری پایین، جست‌وجو، اعلان‌ها، انتخاب زبان، پیش‌نمایش تم برای کارکنان، اسپلش Lottie، Service Worker امن.
- **MFEها:** کتابخانهٔ ES module با `SHADOW_DOM`؛ استایل‌ها داخل shadow root و ریشهٔ `.hy`. هر MFE پیام‌های سه‌زبانهٔ خودش را دارد و locale/theme را از پوسته می‌گیرد.
- **Design system:** پورت مستقیم CSS نمونهٔ Figma با ویژگی‌های منطقی (`margin-inline`, …) برای RTL/LTR، فونت Vazirmatn محلی، آیکون‌های جهت‌دار که در LTR قرینه می‌شوند.
- **i18n:** فارسی پیش‌فرض (مرورگر عربی ← عربی)، تقویم جلالی برای فارسی، `Intl` برای قالب‌بندی، کوکی `KEYCLOAK_LOCALE` برای هم‌زبانی صفحات ورود.
- **PWA:** Service Worker فقط پوستهٔ ایستا را کش می‌کند و `/api`، `/auth`، `/realms` و هر پاسخ خصوصی را هرگز ذخیره نمی‌کند؛ هنگام خروج، کش و `sessionStorage` پاک می‌شوند.

## ۶. انتشار مستقل

هر ماژول یک image دارد که backend و artifact میکروفرانت‌اند همان نسخه را با هم سرو می‌کند؛ نسخهٔ MFE در مسیر artifact است (`/mfe/1.0.1/entry.js`) و با `manifestVersion` و نسخهٔ package هماهنگ است ([tools/validate-manifests.mjs](../tools/validate-manifests.mjs)). انتشار نسخهٔ جدید: ساخت image ماژول ← استقرار ← ثبت manifest جدید و فعال‌سازی artifact با bootstrap. نسخه‌های قبلی artifact تا زمانی که image قبلی در دسترس است قابل بازگشت‌اند. ابزار CapRover ([deployment/caprover/deploy.mjs](../deployment/caprover/deploy.mjs)) امکان سرو artifact از اپ nginx جداگانه را نیز دارد.
