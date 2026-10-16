# هویت و دسترسی

## ۱. اصول

- **Keycloak تنها ارائه‌دهندهٔ هویت است.** HaramYar هیچ جدول رمز عبور، صادرکنندهٔ توکن یا جریان password grant ندارد (`directAccessGrantsEnabled=false` روی کلاینت).
- **نشست مرورگر فقط در Hive BFF است:** کلاینت محرمانهٔ `haramyar-web` با Authorization Code + PKCE (`S256`)؛ مرورگر فقط کوکی HttpOnly `HIVE_SESSION` دارد. هیچ توکنی در JavaScript، `localStorage` یا `sessionStorage` نیست.
- **مجوز در Hive Authorization است.** سرویس‌های HaramYar هرگز به هدرهای هویت یا نقش ارسالی از مرورگر اعتماد نمی‌کنند؛ فقط به JWT امضاشدهٔ Keycloak که BFF فوروارد می‌کند و `X-Hive-User-Id` که BFF تنظیم می‌کند (بخش ۵).
- **هیچ نقش پلتفرمی Hive به‌طور خودکار به کاربر یا مدیر کسب‌وکار داده نمی‌شود.**

## ۲. realm `haramyar`

| تنظیم | مقدار |
|---|---|
| نام کاربری | شمارهٔ موبایل نرمال‌شده E.164 (`+989…` یا `+9647…`)؛ کارکنان: `staff.<name>` |
| ورود با ایمیل | غیرفعال |
| زبان‌ها | `fa` (پیش‌فرض)، `ar`، `en` — تم ورود سه‌زبانه با RTL/LTR |
| سیاست رمز | حداقل ۱۰ و حداکثر ۱۲۸ نویسه، حداقل یک رقم، متفاوت با نام کاربری/ایمیل، عدم تکرار ۳ رمز قبلی |
| محافظت brute force | پس از ۵ شکست، قفل موقت افزایشی (۶۰ ثانیه تا حداکثر ۱۵ دقیقه)؛ قفل دائمی ندارد |
| نشست SSO | بیکاری ۳۰ دقیقه، حداکثر ۱۰ ساعت؛ عمر access token ۵ دقیقه |
| ثبت‌نام | فعال؛ فرم: نام کاربری موبایل + رمز + پذیرش قوانین؛ reCAPTCHA غیرفعال (بخش ۷) |
| نقش‌های realm | `haramyar-customer` (پیش‌فرض)، `haramyar-staff` (فعال‌کنندهٔ MFA) |

realm از [haramyar-realm.json](../integrations/keycloak/realm/haramyar-realm.json) import می‌شود؛ secret کلاینت و origin عمومی از متغیرهای محیطی `${HARAMYAR_OIDC_CLIENT_SECRET}` و `${HARAMYAR_PUBLIC_ORIGIN}` جایگزین می‌شوند و هیچ مقدار واقعی در مخزن نیست.

## ۳. شمارهٔ موبایل به‌عنوان نام کاربری (افزونهٔ SPI)

[integrations/keycloak/mobile-username-spi](../integrations/keycloak/mobile-username-spi) سه نقطهٔ ورود را پوشش می‌دهد تا یک شماره با املاهای مختلف (`09121234567`، `۰۹۱۲۱۲۳۴۵۶۷`، `+98 912 123 4567`، `00989121234567`) همیشه به یک حساب برسد:

| Provider ID | جایگاه | کار |
|---|---|---|
| `haramyar-mobile-password-form` | جریان ورود | نرمال‌سازی ورودی پیش از جست‌وجوی کاربر |
| `haramyar-registration-user-creation` | فرم ثبت‌نام (زیرکلاس `RegistrationUserCreation`) | نرمال‌سازی پیش از اعتبارسنجی و ایجاد؛ جلوگیری از حساب تکراری با املای دیگر |
| `haramyar-reset-choose-user` | بازیابی رمز | نرمال‌سازی پیش از یافتن کاربر |

ارقام فارسی/عربی، فاصله، خط تیره و پیشوندهای `0`، `00`، `+` پذیرفته می‌شوند؛ فقط موبایل ایران و عراق معتبر است. نرمال‌سازی با نمای proxy از درخواست انجام می‌شود تا Keycloak فرم را با مقدار نرمال‌شده بخواند. الگوی user profile نیز همین قالب را در سمت سرور اعمال می‌کند.

## ۴. MFA کارکنان

جریان `haramyar browser forms`: پس از رمز عبور، اگر کاربر نقش realm `haramyar-staff` داشته باشد، OTP (TOTP، ۶ رقم، ۳۰ ثانیه) **اجباری** است. کاربر کارمندی که هنوز OTP ندارد در اولین ورود به ثبت آن (`CONFIGURE_TOTP`) هدایت می‌شود و بدون آن وارد نمی‌شود. کاربران عادی MFA ندارند.

افزودن کارمند جدید: ساخت کاربر `staff.<name>` در Keycloak (از طریق ابزار مدیریتی داخلی، نه از مرورگر عمومی)، دادن نقش realm `haramyar-staff` و required action `CONFIGURE_TOTP`، سپس دادن نقش کسب‌وکاری در Hive (بخش ۶).

## ۵. اعتماد به هویت در سرویس‌ها

`HiveRequestIdentityFilter` در [service-kit](../shared/service-kit) برای هر درخواست:

1. JWT را با JWKS Keycloak، `iss` و `azp=haramyar-web` اعتبارسنجی می‌کند.
2. `X-Hive-User-Id` را فقط وقتی همراه توکن معتبر باشد می‌پذیرد و اتصال `sub` Keycloak ↔ شناسهٔ Hive را در `hive_identity_binding` ثبت می‌کند (اولین اتصال ثبت می‌شود؛ هر ناسازگاری بعدی رد می‌شود).
3. نقش کارمند را فقط از نقش realm داخل توکن (`haramyar-staff`) می‌خواند.

مجوز عملیات (مثلاً `rozeh-booking.coordination:decide`) پیش از رسیدن درخواست به سرویس در Hive BFF بررسی می‌شود؛ سرویس علاوه بر آن قواعد مالکیت (فقط رزروهای خود) و سیاست‌های دامنه را اعمال می‌کند. `ServiceStartupGuard` در پروفایل production بدون issuer و JWKS اجرا نمی‌شود.

## ۶. نقش‌های کسب‌وکاری و نگاشت آن‌ها

نام‌های مشخصات به کلیدهای مجاز Hive (`[a-z][a-z0-9._-]*`) نگاشت شده‌اند. تعریف کامل در [access.json](../integrations/hive/access.json).

| نقش مشخصات | کلید Hive | grantها |
|---|---|---|
| `HARAMYAR_USER` | `haramyar-user` | `rozeh-booking.requests`: view, create, confirm, cancel · `haramyar-app.notifications`: view, acknowledge |
| `ROZEH_COORDINATOR` | `rozeh-coordinator` | `rozeh-booking.coordination`: view, decide, assign, confirm, cancel, complete · `rozeh-booking.providers`: view, maintain |
| `MARTYRS_EDITOR` | `martyrs-editor` | `martyrs.editorial`: view, edit, submit |
| `MARTYRS_REVIEWER` | `martyrs-reviewer` | `martyrs.editorial`: view, review, publish |
| `HARAMYAR_ADMIN` | `haramyar-admin` | `haramyar-app.occasions`: view, configure · مشاهدهٔ هماهنگی، روضه‌خوانان و تحریریه |

- action `manage` در Hive رزرو است؛ به همین دلیل از `maintain` و `configure` استفاده شده است.
- `HARAMYAR_ADMIN` مدیر کسب‌وکار است و عمداً اجازهٔ تصمیم‌گیری رزرو یا انتشار ندارد؛ جداسازی وظایف حفظ می‌شود. این نقش هیچ دسترسی به کنسول اپراتور Hive ندارد.
- **Onboarding:** در اولین ورود، پوسته `POST /api/routes/haramyar/onboarding` را صدا می‌زند و shell-service نقش `haramyar-user` را از طریق API مدیریت Hive اختصاص می‌دهد. نقش‌های کارکنان دستی و توسط اپراتور مجاز داده می‌شوند.
- در محیط توسعه، `dev-staff-fixtures.mjs` (فقط با `HARAMYAR_DEV_FIXTURES=true`) چهار کارمند آزمایشی با نام خانوادگی `Fixture` می‌سازد.

## ۷. وضعیت‌های باز و ریسک‌ها

| موضوع | وضعیت |
|---|---|
| تأیید مالکیت شماره | **انجام نمی‌شود.** هیچ پیامکی ارسال نمی‌شود؛ `phone_number_verified` در توکن هرگز true نمی‌شود. هر کسی می‌تواند با شمارهٔ دیگری ثبت‌نام کند. برای کاهش اثر: دادهٔ رزرو فقط به همان حساب نمایش داده می‌شود و سیاست `ROZEH_REQUIRE_VERIFIED_MOBILE` آمادهٔ فعال‌سازی پس از راه‌اندازی OTP پیامکی است. |
| بازیابی رمز | جریان با `reset-credential-email` است و کاربران موبایلی ایمیل ندارند؛ بنابراین بازیابی خودکار عملاً در دسترس نیست تا کانال پیامک تعیین شود ([BUSINESS_DECISIONS](BUSINESS_DECISIONS.md#۵-موارد-باز-بدون-پیشفرض-اجرایی)). |
| ربات و ثبت‌نام انبوه | reCAPTCHA غیرفعال است (سرویس Google در منطقهٔ هدف قابل اتکا نیست). محدودیت نرخ gateway روی مسیرهای هویت (۲۰ درخواست در دقیقه به ازای IP) و brute force Keycloak فعال‌اند؛ CAPTCHA بومی یا OTP پیامکی پیشنهاد می‌شود. |
| اعتبارنامهٔ provisioning | shell-service با `HIVE_PROVISIONING_PASSWORD` (Basic) نقش پیش‌فرض را اختصاص می‌دهد. این اعتبارنامه فقط در شبکهٔ داخلی استفاده می‌شود، اما کسی که آن را داشته باشد می‌تواند نقش اختصاص دهد. باید فقط در secret store سرور نگهداری و دوره‌ای چرخانده شود. هدف بلندمدت: API محدود به «اختصاص نقش پیش‌فرض» در Hive. |
| اعتبارنامهٔ مدیر Keycloak | فقط برای import اولیه و fixtureهای توسعه؛ Admin Console از gateway در دسترس نیست. پس از bootstrap در production باید غیرفعال یا چرخانده شود. |
