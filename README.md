# حرم‌یار (HaramYar)

سوپراپ خدمات زیارتی و فرهنگی، ساخته‌شده به‌عنوان یک **solution مصرف‌کننده** روی [Hive Platform](https://github.com/mahdiporkar/hive-platform) نسخهٔ `0.1.0-dev.0` (کامیت پین‌شده در [hive.lock.json](hive.lock.json)).

هیچ کد کسب‌وکاری HaramYar در Hive قرار نمی‌گیرد و HaramYar هیچ قابلیت هستهٔ Hive (نشست BFF، مسیریابی پویا، کاتالوگ منابع و مجوز، دروازهٔ artifact میکروفرانت‌اند) را دوباره پیاده نمی‌کند.

## ماژول‌ها

| ماژول | نقش | فناوری |
|---|---|---|
| [modules/rozeh-booking](modules/rozeh-booking) | رزرو روضه‌خوانی: درخواست میزبان، پذیرش/رد هماهنگ‌کننده، تأیید نهایی، لغو، برگزاری | Java 21 · Spring Boot · PostgreSQL · RabbitMQ · React 19 MFE |
| [modules/martyrs](modules/martyrs) | معرفی شهدا: تحریریهٔ پیش‌نویس ← بازبینی ← انتشار ← بایگانی با اصل چهارچشم و صفحات SEO | Java 21 · Spring Boot · PostgreSQL · S3 · React 19 MFE |
| [apps/haramyar-shell](apps/haramyar-shell) | پوستهٔ PWA: ناوبری، سه تم (عادی، عزا، جشن)، اسپلش Lottie، اعلان‌ها، میزبانی MFEها | React 19 · Vite · Tailwind v4 · Hive SDK |
| [apps/shell-service](apps/shell-service) | مناسبت‌ها/تم فعال، onboarding نقش پیش‌فرض، اعلان‌های درون‌برنامه‌ای (مصرف‌کنندهٔ رویداد) | Java 21 · Spring Boot |
| [apps/public-web](apps/public-web) | رندر سمت سرور صفحات عمومی شهدا، sitemap و robots | Node 22 · React SSR |
| [integrations/keycloak](integrations/keycloak) | realm، تم ورود سه‌زبانه، افزونهٔ «شمارهٔ موبایل = نام کاربری» | Keycloak 26.3 SPI |
| [integrations/hive](integrations/hive) | ثبت solution، سرویس‌ها، مسیرها، منابع دستی، نقش‌ها و grantها از طریق Hive CLI | Node |
| [shared/](shared) | `service-kit` (امنیت، outbox/inbox/retry/DLQ)، `design-system` (پورت نمونهٔ Figma)، `i18n` (fa/ar/en، تقویم جلالی) | Java · TS |

همهٔ فرم‌های کاربر (ورود، ثبت‌نام، بازیابی رمز، ویزارد رزرو، تحریریه، مدیریت مناسبت) به سه زبان **فارسی، عربی و انگلیسی** با جهت RTL/LTR درست ارائه می‌شوند.

## شروع سریع (محلی)

پیش‌نیازها: Docker (Engine ۲۵ به بالا)، Node ۲۲.۱۳+، JDK ۲۱، و checkout مخزن Hive کنار این مخزن.

```bash
git clone https://github.com/mahdiporkar/hive-platform.git ../hive-platform
git -C ../hive-platform checkout "$(node -p "require('./hive.lock.json').commit")"
(cd ../hive-platform && npm ci && npm run build && ./mvnw -B -q -pl starters/hive-spring-boot-starter -am install -DskipTests)

npm run hive:sdk          # پیوند بسته‌های SDK پین‌شده در vendor/hive-sdk
npm ci
node deployment/generate-env.mjs --dev-fixtures   # اسرار تصادفی محلی در deployment/.env (commit نمی‌شود)
npm run images -- --hive  # ساخت jarها، frontendها و همهٔ imageها (بار اول با imageهای Hive)
cd deployment
docker compose --env-file .env up -d
docker compose --env-file .env --profile bootstrap run --rm bootstrap
```

سپس <http://localhost:8080> را باز کنید. جزئیات، عیب‌یابی و استقرار production در [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## مستندات

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — معماری، مرز با Hive، جریان درخواست و رویداد
- [docs/BUSINESS_DECISIONS.md](docs/BUSINESS_DECISIONS.md) — تصمیم‌های تأییدشده و سیاست‌های موقت قابل‌پیکربندی
- [docs/IDENTITY_AND_ACCESS.md](docs/IDENTITY_AND_ACCESS.md) — Keycloak، نقش‌ها، MFA، بازیابی، نگاشت نقش‌ها
- [docs/SECURITY.md](docs/SECURITY.md) — کنترل‌های امنیتی و ریسک‌های باز
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — Compose، bootstrap، production، عیب‌یابی
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — محیط توسعه، تست‌ها، افزودن ماژول
- [docs/reports/](docs/reports) — گزارش ممیزی فاز ۰ و گزارش پیاده‌سازی با شواهد تست

## تست‌ها

```bash
npm run typecheck && npm test && npm run validate:manifests   # TypeScript، تست واحد i18n، اعتبارسنجی manifestها
./mvnw verify                                                 # تست واحد، ArchUnit و یکپارچگی با Testcontainers
npm run test:e2e                                              # سفر کامل کاربر روی پشتهٔ در حال اجرا (Playwright)
```

## مجوز و دارایی‌ها

فونت Vazirmatn تحت [SIL OFL 1.1](shared/design-system/assets/fonts/Vazirmatn-OFL.txt). تصاویر نمونهٔ Figma از Unsplash فقط به‌صورت نشانی در CSS ارجاع داده می‌شوند. هیچ دادهٔ شهیدی همراه مخزن نیست: آرشیو تا انتشار توسط تحریریه خالی است. پرونده‌هایی که تست E2E می‌سازد با برچسب «E2E fixture» و متن «محتوای واقعی نیست» مشخص شده‌اند و هیچ زندگی‌نامه یا واقعیت تاریخی جعل نشده است.
