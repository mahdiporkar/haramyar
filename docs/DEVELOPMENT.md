# راهنمای توسعه

## ساختار مخزن

```
apps/            haramyar-shell (PWA)، public-web (SSR)، shell-service (Java)
modules/         rozeh-booking و martyrs — هر کدام backend/، frontend/، manifests/، Dockerfile
shared/          service-kit (Java)، design-system و i18n (TypeScript)
integrations/    keycloak (realm، تم، SPI)، hive (solution، access.json، register.mjs)
deployment/      docker-compose، generate-env، پیکربندی زیرساخت، ابزار CapRover
tests/           unit (Node)، e2e (Playwright)
tools/           hive-sdk، mfe-manifests، validate-manifests، images
```

## آماده‌سازی

1. Hive را در `../hive-platform` روی کامیت پین‌شده checkout کنید و `npm ci && npm run build` و نصب starter را اجرا کنید (README).
2. `npm run hive:sdk` بسته‌های `@hive-platform/*` را از dist همان کامیت در `vendor/hive-sdk` کپی می‌کند (کد منبع Hive وارد مخزن نمی‌شود). پس از تغییر کامیت Hive، `hive.lock.json` را به‌روز و این دستور را دوباره اجرا کنید.
3. `npm ci`.

## دستورات

| دستور | کار |
|---|---|
| `npm run typecheck` | TypeScript همهٔ workspaceها |
| `npm run build` | design-system، هر دو MFE (با SRI و manifest)، پوسته و public-web |
| `npm test` | تست‌های واحد Node: تبدیل جلالی، شبکهٔ ماه، منطقهٔ زمانی، ارقام موبایل، کامل‌بودن واژه‌نامه‌های سه زبان |
| `npm run validate:manifests` | اعتبارسنجی آفلاین Hive + هماهنگی نسخه، artifact و grantها |
| `./mvnw verify` | تست واحد، ArchUnit و یکپارچگی (Testcontainers: PostgreSQL، RabbitMQ، SeaweedFS) |
| `npm run images` | ساخت jarها، frontendها و همهٔ imageها |
| `npm run test:e2e` | سفر کامل کاربر روی پشتهٔ در حال اجرا |

اجرای یک سرویس بدون Docker: `./mvnw -pl modules/rozeh-booking/backend spring-boot:run` با متغیرهای `ROZEH_DB_*`، `RABBITMQ_*` و `HARAMYAR_OIDC_*` (ببینید `application.properties`). در پروفایل `development` گارد production غیرفعال است.

## تست E2E

- نیازمند پشتهٔ در حال اجرا با `HARAMYAR_DEV_FIXTURES=true` و Docker (برای بازنشانی MFA کارمندان آزمایشی).
- مرورگر: روی Windows از Microsoft Edge نصب‌شده و در بقیه از Chromium Playwright استفاده می‌شود (`npx playwright install chromium`).
- هر اجرا یک شمارهٔ موبایل تصادفی ثبت‌نام می‌کند؛ پرونده‌های آزمایشی برچسب `E2E fixture` دارند و مناسبت آزمایشی (`e2e-*`) در پایان همان تست بایگانی می‌شود.

## قواعد کدنویسی

- **دامنه خالص است:** بدون Spring، JPA، Jackson یا service-kit (ArchUnit). موجودیت‌های JPA جدا از aggregateها هستند.
- **قاعدهٔ کسب‌وکار در سرور:** UI از `allowedActions` پاسخ‌ها پیروی می‌کند و قاعده را تکرار نمی‌کند.
- **رویدادها:** فقط از طریق `OutboxWriter` در تراکنش؛ مصرف با `ReliableEventConsumer` (inbox، retry، DLQ). نام نسخه‌دار `haramyar.<context>.<event>.v<n>`.
- **migration:** فقط افزودنی و سازگار با نسخهٔ قبلی؛ هرگز ویرایش migration منتشرشده.
- **متن رابط کاربری:** هر رشته در هر سه زبان (`messages.ts` هر MFE، `shared/i18n/src/common.ts`، `messages_*.properties` تم Keycloak). از ویژگی‌های منطقی CSS استفاده کنید، نه left/right.
- **سیاست‌های تأییدنشده** را با پیکربندی و ثبت در [BUSINESS_DECISIONS.md](BUSINESS_DECISIONS.md) پیاده کنید، نه سخت‌کد.
- **اسرار:** هرگز در کد، تست یا مستندات؛ فقط متغیر محیطی.

## افزودن ماژول جدید

1. `modules/<key>/` با همان ساختار؛ backend به `service-kit` وابسته و در `pom.xml` ریشه اضافه شود.
2. `manifests/mf-manifest.json` و `resource-manifest.json`؛ build با `tools/mfe-manifests.mjs`.
3. `integrations/hive/solution/services/<key>.json` (target، route، عملیات با access و resource/action) و افزودن به `hive.solution.json`.
4. نقش‌ها و grantها در `access.json`؛ سرویس در `docker-compose.yml`؛ نام ماژول در `tools/images.mjs` و `tools/validate-manifests.mjs`.
