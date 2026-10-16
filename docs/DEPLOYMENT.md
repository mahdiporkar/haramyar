# استقرار

## ۱. پشتهٔ محلی با Docker Compose

### پیش‌نیازها
- Docker Engine ۲۵ یا جدیدتر (API ۱.۴۴+) با Compose v2، حداقل ۸ گیگابایت RAM آزاد برای Docker.
- Node ۲۲.۱۳+، JDK ۲۱.
- checkout مخزن Hive در `../hive-platform` روی کامیت [hive.lock.json](../hive.lock.json) (یا مسیر دلخواه با `HIVE_PLATFORM_DIR`).

### گام‌ها

```bash
# ۱) Hive: SDK و starter
(cd ../hive-platform && npm ci && npm run build && ./mvnw -B -q -pl starters/hive-spring-boot-starter -am install -DskipTests)
npm run hive:sdk && npm ci

# ۲) اسرار محلی (deployment/.env؛ هرگز commit نشود). --dev-fixtures کارمندان آزمایشی می‌سازد.
node deployment/generate-env.mjs --dev-fixtures

# ۳) ساخت همهٔ artifactها و imageها (بار اول با --hive برای hive/bff و hive/authorization)
npm run images -- --hive

# ۴) اجرا و bootstrap
cd deployment
docker compose --env-file .env up -d
docker compose --env-file .env ps            # منتظر healthy شدن همه
docker compose --env-file .env --profile bootstrap run --rm bootstrap
```

bootstrap به ترتیب: اپلیکیشن و منابع دستی را در Hive ثبت می‌کند ← solution را با Hive CLI ثبت می‌کند (ماژول‌ها، resource manifestها، artifactها، targetها، routeها، عملیات) و artifact را فعال می‌کند ← نقش‌ها و grantها را ثبت می‌کند ← در صورت `HARAMYAR_DEV_FIXTURES=true` کارمندان آزمایشی را در Keycloak و Hive می‌سازد. اجرای مجدد آن بی‌خطر است (idempotent).

نشانی: <http://localhost:8080>. کارمندان آزمایشی `staff.coordinator`، `staff.editor`، `staff.reviewer`، `staff.admin` با رمز `HARAMYAR_FIXTURE_STAFF_PASSWORD` از `deployment/.env`؛ در اولین ورود OTP باید ثبت شود. Mailpit فقط با `--profile dev` روی `127.0.0.1:8025`.

### سرویس‌ها

| سرویس | image | نقش |
|---|---|---|
| gateway | `haramyar/gateway` | nginx + پوسته؛ تنها پورت منتشرشده `127.0.0.1:8080` |
| bff | `hive/bff` | Hive BFF (فضای شبکهٔ gateway) |
| authorization | `hive/authorization` | Hive Authorization |
| keycloak | `haramyar/keycloak` | Keycloak 26.3 + SPI + تم + realm |
| rozeh-booking / martyrs / shell-service | `haramyar/*` | سرویس‌های کسب‌وکار (+ artifact MFE) |
| public-web | `haramyar/public-web` | SSR صفحات شهدا |
| postgres، openfga، redis، rabbitmq، object-storage | نسخه‌های پین‌شده | زیرساخت داخلی |

## ۲. انتشار نسخهٔ جدید یک ماژول

1. نسخه را در `modules/<m>/frontend/package.json` و `manifestVersion` و مسیر artifact در `modules/<m>/manifests/mf-manifest.json` هم‌زمان بالا ببرید.
2. `npm run build && npm run validate:manifests`.
3. image ماژول را بسازید و با برچسب جدید (`ROZEH_BOOKING_VERSION` / `MARTYRS_VERSION`) مستقر کنید.
4. bootstrap را اجرا کنید تا manifest جدید ثبت و artifact فعال شود.

بازگشت: image قبلی را دوباره مستقر و نسخهٔ artifact قبلی را فعال کنید. migrationهای Flyway فقط رو به جلو هستند؛ rollback مخرب دیتابیس انجام نمی‌شود، پس هر migration باید با نسخهٔ قبلی سازگار باشد.

## ۳. production

تفاوت‌ها با محیط محلی:

| موضوع | production |
|---|---|
| TLS | در proxy جلوی gateway (CapRover/ingress) خاتمه می‌یابد و باید `X-Forwarded-Proto` و `X-Forwarded-Port` بفرستد. هدایت HTTP به HTTPS فعال شود. |
| origin | `HARAMYAR_PUBLIC_ORIGIN=https://<domain>`؛ issuer برابر `https://<domain>/realms/haramyar` |
| پروفایل‌ها | `HARAMYAR_PROFILE=production`، `HIVE_PROFILE=production`، `HIVE_ALLOW_LOCAL_HTTP=false` |
| fixtureها | `HARAMYAR_DEV_FIXTURES=false` (پیش‌فرض) |
| اسرار | مستقل از محیط توسعه، در secret store سرور؛ هرگز در مخزن یا GitHub |
| مدیر Keycloak | پس از import غیرفعال یا چرخانده شود |
| پشتیبان | dump روزانهٔ PostgreSQL (همهٔ دیتابیس‌ها) و volume ذخیره‌ساز اشیا، با آزمون restore |
| ظرفیت | کل پشته در بودجهٔ طراحی حدود ۳٫۷ گیگابایت RAM است؛ حداقل ۸ گیگابایت توصیه می‌شود ([ممیزی سرور](reports/production-server-audit-2026-10-16.md)) |

ابزار [deployment/caprover/deploy.mjs](../deployment/caprover/deploy.mjs) اپ‌های جدا برای CapRover با اسرار تصادفی در `.local/caprover` (gitignore) آماده می‌کند. **وضعیت فعلی: استقرار production انجام نشده است.**

## ۴. عیب‌یابی

| نشانه | علت و راه‌حل |
|---|---|
| Testcontainers: «Could not find a valid Docker environment» | Docker Engine ۲۹ کمینهٔ API را بالا برده است؛ `pom.xml` مقدار `api.version=1.44` را تنظیم می‌کند. اگر مستقیم از IDE اجرا می‌کنید، همین system property را اضافه کنید. |
| پس از بازسازی gateway، ورود با خطای redirect یا اتصال BFF قطع است | BFF فضای شبکهٔ gateway را به اشتراک می‌گذارد؛ پس از هر recreate gateway: `docker compose --env-file .env up -d --force-recreate bff` |
| `redirect_uri` بدون پورت (`http://localhost/...`) | proxy جلویی `X-Forwarded-Proto` را بدون `X-Forwarded-Port` می‌فرستد؛ هر دو را بفرستید یا هیچ‌کدام. |
| bootstrap: `ARTIFACT_UNAVAILABLE ... HTTP 404` | image ماژول نسخهٔ جدید را سرو نمی‌کند یا image bootstrap قدیمی است؛ `npm run images` (شامل profile bootstrap) و استقرار مجدد ماژول. |
| صفحهٔ ورود Keycloak خطای «Invalid username» برای شماره | افزونهٔ SPI بارگذاری نشده؛ `docker compose logs keycloak` و وجود jar در image را بررسی کنید. |
| RabbitMQ با کاربر پیش‌فرض بالا نمی‌آید | `deployment/rabbitmq/entrypoint.sh` کاربر را از `RABBITMQ_PASSWORD` می‌سازد؛ volume قدیمی با رمز دیگر را حذف کنید (فقط محیط محلی). |
| تم مناسبت آزمایشی باقی مانده است (تست E2E نیمه‌کاره) | مناسبت `e2e-*` را در «مدیریت مناسبت‌ها» بایگانی کنید. |
