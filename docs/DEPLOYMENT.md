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

محیط آزمایشی روی CapRover در بخش ۴ مستند شده است. **استقرار production (با داده و کاربران واقعی) هنوز انجام نشده است.**

## ۴. محیط آزمایشی CapRover — `https://haramyar.com`

### معماری استقرار

همهٔ اجزا اپ‌های اختصاصی CapRover با پیشوند `haramyar-` روی شبکهٔ `captain-overlay-network` هستند و با نام داخلی `srv-captain--haramyar-<name>` با هم صحبت می‌کنند. **فقط `haramyar-web` عمومی است**؛ بقیه `notExposeAsWebApp` دارند و هیچ پورت host منتشر نمی‌کنند. هیچ اپ، دیتابیس یا volume دیگری روی سرور (husbantech، university ERP، Hive دمو) تغییر نمی‌کند یا دوباره استفاده نمی‌شود.

| اپ CapRover | نقش | پورت داخلی | سقف حافظه | volume |
|---|---|---:|---:|---|
| `haramyar-web` | gateway nginx + پوستهٔ PWA؛ دامنهٔ `haramyar.com` با HTTPS اجباری | 8080 | 64 MiB | — |
| `haramyar-keycloak` | Keycloak 26.3 + SPI + تم + realm `haramyar` (`KC_CACHE=local`) | 8080 | 512 MiB | — (داده در Postgres) |
| `haramyar-hive-bff`، `haramyar-hive-authorization` | Hive BFF و Authorization اختصاصی (نه نمونه‌های دمو) | 8081، 8082 | 384 MiB | — |
| `haramyar-booking-api`، `haramyar-martyrs-api`، `haramyar-shell-service` | سرویس‌های کسب‌وکار | 8090، 8091، 8092 | 384 MiB | — |
| `haramyar-booking-frontend`، `haramyar-martyrs-frontend` | nginx ایستا برای artifact نسخه‌دار هر MFE (`/mfe/<version>/entry.js`) | 8080 | 32 MiB | — |
| `haramyar-public-web` | SSR صفحات شهدا | 8093 | 96 MiB | — |
| `haramyar-postgres` | PostgreSQL 17؛ دیتابیس و کاربر جدا برای hive، openfga، keycloak، rozeh، martyrs، shell | 5432 | 192 MiB | `haramyar-postgres-data` |
| `haramyar-rabbitmq` | RabbitMQ 4.1 با کاربر `haramyar` و topology از definitions | 5672 | 320 MiB | `haramyar-rabbitmq-data` |
| `haramyar-object-storage` | SeaweedFS (S3) برای رسانهٔ شهدا | 8333 | 192 MiB | `haramyar-objects-data` |
| `haramyar-redis`، `haramyar-openfga` | کش Hive و OpenFGA (دادهٔ OpenFGA در Postgres) | 6379، 8080 | 64، 96 MiB | `haramyar-redis-data` |
| `haramyar-bootstrap` | کار یک‌باره: ثبت Hive، سیاست realm، حساب‌های آزمایشی؛ پس از اجرا روی ۰ نمونه | — | 128 MiB | — |

**نگاشت دامنه:** فقط `haramyar.com` → `haramyar-web` (گواهی Let's Encrypt از CapRover، `forceSsl`). Keycloak و APIها زیردامنهٔ جدا ندارند و از همان مبدأ سرو می‌شوند: issuer برابر `https://haramyar.com/realms/haramyar` و APIها زیر `/api/routes/**`. مسیرهای `/admin/*`، `/realms/master`، `/actuator` و `/console` پاسخ 404 می‌دهند. CSP، HSTS (بدون includeSubDomains)، X-Frame-Options و دیگر هدرهای امنیتی روی همهٔ صفحات اعمال می‌شوند.

### سیاست‌های محیط آزمایشی

- ثبت‌نام آزاد و بازیابی رمز **غیرفعال** است (`HARAMYAR_SELF_REGISTRATION=false`، `HARAMYAR_PASSWORD_RESET=false`)؛ bootstrap در هر اجرا این را روی realm اعمال می‌کند.
- حساب‌های آزمایشی محدود (`HARAMYAR_DEV_FIXTURES=true`): کارمندان `staff.coordinator`، `staff.editor`، `staff.reviewer`، `staff.admin` (MFA اجباری) و مشتریان `+989990000001` و `+989990000002` با نام خانوادگی `Fixture`. شماره‌ها تأییدنشده‌اند و هیچ پیامکی ارسال نمی‌شود. رمز مشترک آن‌ها `HARAMYAR_FIXTURE_STAFF_PASSWORD` در `.local/caprover/secrets.json` است.

### متغیرهای محیطی (فقط نام؛ مقادیر در `.local/caprover/secrets.json` یا secret store)

| اپ | متغیرها |
|---|---|
| سرویس‌های Java کسب‌وکار | `HARAMYAR_PROFILE=production`، `HARAMYAR_OIDC_ISSUER`، `HARAMYAR_OIDC_JWKS_URI` (داخلی)، `ROZEH_DB_*` / `MARTYRS_DB_*` / `SHELL_DB_*` (URL، USER، PASSWORD)، `JAVA_TOOL_OPTIONS` |
| booking-api، shell-service | `RABBITMQ_HOST`، `RABBITMQ_PASSWORD`؛ shell-service همچنین `HIVE_AUTHORIZATION_URL`، `HIVE_PROVISIONING_PASSWORD` |
| martyrs-api | `MARTYRS_S3_ENDPOINT`، `MARTYRS_S3_ACCESS_KEY`، `MARTYRS_S3_SECRET_KEY` |
| hive-authorization | `HIVE_DB_*`، `HIVE_OPENFGA_URL`، `HIVE_OPENFGA_API_TOKEN`، `HIVE_PROVISIONING_PASSWORD`، `HIVE_INTERNAL_PASSWORD`، `HIVE_REDIS_*`، `HIVE_PRIMARY_ISSUER`، `HIVE_ARTIFACT_*`، `HIVE_TARGET_*` |
| hive-bff | `HIVE_OIDC_ISSUER`، `HIVE_OIDC_CLIENT_ID`، `HIVE_OIDC_CLIENT_SECRET`، `HIVE_VAULT_KEY`، `HIVE_INTERNAL_PASSWORD`، `HIVE_REDIS_*`، `HIVE_PROXY_*`، `HIVE_MFE_*` |
| keycloak | `KC_DB_*`، `KC_HOSTNAME`، `KC_PROXY_HEADERS`، `KC_CACHE`، `KC_BOOTSTRAP_ADMIN_*`، `HARAMYAR_PUBLIC_ORIGIN`، `HARAMYAR_OIDC_CLIENT_SECRET` |
| web | `HARAMYAR_BFF_UPSTREAM`، `HARAMYAR_KEYCLOAK_UPSTREAM`، `HARAMYAR_PUBLIC_WEB_UPSTREAM`، `HARAMYAR_CONSOLE_UPSTREAM=off` |
| public-web | `HARAMYAR_BFF_URL`، `HARAMYAR_PUBLIC_ORIGIN` |
| bootstrap | `HIVE_AUTHORIZATION_URL`، `HIVE_PROVISIONING_PASSWORD`، `ROZEH_BOOKING_URL`، `MARTYRS_URL`، `SHELL_SERVICE_URL`، `ROZEH_BOOKING_MFE_URL`، `MARTYRS_MFE_URL`، `KEYCLOAK_URL`، `KEYCLOAK_ADMIN_*`، `HARAMYAR_OIDC_ISSUER`، `HARAMYAR_DEV_FIXTURES`، `HARAMYAR_FIXTURE_*`، `HARAMYAR_SELF_REGISTRATION`، `HARAMYAR_PASSWORD_RESET` |
| rabbitmq، object-storage، openfga، postgres | `HARAMYAR_RABBITMQ_USER/PASSWORD`؛ `S3_ACCESS_KEY/SECRET_KEY`؛ `OPENFGA_DATASTORE_URI`، `OPENFGA_AUTHN_PRESHARED_KEYS`؛ `POSTGRES_PASSWORD` و `*_DB_PASSWORD` |

### روش استقرار

[deploy.mjs](../deployment/caprover/deploy.mjs) از نشست CLI موجود CapRover (یا `CAPROVER_URL` + `CAPROVER_TOKEN` در CI) استفاده می‌کند. برای هر جزء، context ساخت (jar یا dist + Dockerfile) را آماده و از طریق API به CapRover می‌فرستد. CapRover image را می‌سازد، در registry پیکربندی‌شدهٔ خود (`ghcr.io/mahdiporkar/img-captain-haramyar-<name>:<version>`) push و مستقر می‌کند. برچسب build شامل SHA کامیت است و نسخهٔ قبلی هر اپ در `.local/caprover/history.jsonl` ثبت می‌شود.

```bash
npm run build && ./mvnw -B -q package -DskipTests && npm run validate:manifests
node deployment/caprover/deploy.mjs deploy booking-api martyrs-api shell-service booking-frontend martyrs-frontend public-web web
node deployment/caprover/deploy.mjs deploy bootstrap   # ثبت manifest/artifact جدید در Hive؛ idempotent
node deployment/caprover/deploy.mjs stop bootstrap     # پس از پایان اجرا
node deployment/caprover/deploy.mjs status
```

ترتیب اولین استقرار: postgres، redis، rabbitmq، object-storage، openfga ← keycloak ← hive-authorization ← hive-bff ← سرویس‌های کسب‌وکار ← frontendها ← public-web ← web ← bootstrap. gateway حتی وقتی BFF هنوز بالا نیامده شروع می‌شود (upstream متغیر)، چون BFF برای discovery به `https://haramyar.com/realms/haramyar` نیاز دارد.

**migration:** هر سرویس Java هنگام شروع، migrationهای Flyway خودش را فقط روی دیتابیس خودش اجرا می‌کند (`ddl-auto=validate`). Keycloak schema خود را مدیریت و realm را فقط بار اول import می‌کند؛ تغییرات بعدی realm از طریق bootstrap (admin API داخلی) اعمال می‌شود.

### بررسی سلامت

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://haramyar.com/                                                  # 200
curl -s https://haramyar.com/api/public/context                                                                 # ماژول‌های martyrs و rozeh-booking
curl -s -o /dev/null -w '%{http_code}\n' https://haramyar.com/realms/haramyar/.well-known/openid-configuration  # 200
curl -s -o /dev/null -w '%{http_code}\n' https://haramyar.com/api/routes/rozeh/my/bookings                     # 401 بدون نشست
node deployment/caprover/deploy.mjs logs <component>                                                            # وضعیت build
```

E2E روی محیط مستقر:

```bash
HARAMYAR_E2E_BASE=https://haramyar.com HARAMYAR_E2E_SECRETS=$PWD/.local/caprover/secrets.json \
HARAMYAR_E2E_CUSTOMERS=+989990000001,+989990000002 HARAMYAR_E2E_TOTP_STORE=$PWD/.local/e2e-totp-haramyar.com.json \
npm run test:e2e
```

### بازگشت (rollback) مختص هر اپ

```bash
node deployment/caprover/deploy.mjs rollback web            # image نسخهٔ پیش از نسخهٔ مستقر
node deployment/caprover/deploy.mjs rollback booking-api 1  # image یک نسخهٔ مشخص
```

فقط همان اپ `haramyar-*` به image قبلی برمی‌گردد؛ CapRover، اپ‌های دیگر و volumeها دست نمی‌خورند. migrationهای دیتابیس برگردانده نمی‌شوند، پس باید با نسخهٔ قبلی سازگار بمانند. اگر manifest یک میکروفرانت‌اند تغییر کرده باشد، پس از rollback اپ frontend، bootstrap را با نسخهٔ قبلی اجرا کنید تا artifact قبلی در Hive فعال شود. snapshot پیکربندی پیش از اولین تغییر هر اپ در `.local/caprover/haramyar-<name>-before.json` است.

### CI/CD

- [ci.yml](../.github/workflows/ci.yml): بهداشت مخزن، typecheck، build، تست واحد، اعتبارسنجی manifest و SRI، `npm audit`، `./mvnw verify` با Testcontainers.
- [deploy.yml](../.github/workflows/deploy.yml): پس از موفقیت CI روی `main` (یا دستی) اجزای برنامه را می‌سازد و با deploy.mjs مستقر می‌کند، نسخه‌های فعلی را برای rollback ذخیره و health check اجرا می‌کند. زیرساخت و Keycloak فقط با اجرای دستی و نام صریح مستقر می‌شوند. تا وقتی secretهای `CAPROVER_URL`، `CAPROVER_TOKEN` و `HARAMYAR_DEPLOY_SECRETS` در GitHub تعریف نشده‌اند، job با یک notice رد می‌شود.
- **هشدار:** توکن API کاپ‌روور دسترسی مدیریتی به کل CapRover (از جمله اپ‌های دیگر) می‌دهد؛ CapRover نقش محدود برای API ندارد. آن را فقط در environment محافظت‌شدهٔ `caprover-test` با تأیید دستی نگه دارید.

### محدودیت‌های شناخته‌شده

- حافظه: پس از استقرار حدود ۵۰۰ مگابایت RAM آزاد و حدود ۱٫۶ از ۲ گیگابایت swap مصرف‌شده است؛ پاسخ اولیهٔ صفحات گاهی کند است. سقف حافظه و تنظیمات JVM (`-Xmx160m`، SerialGC) برای همین سرور تنظیم شده‌اند.
- کنسول اپراتور Hive برای صرفه‌جویی در حافظه مستقر نشده است (`/console` → 404).
- رمز Redis در آرگومان فرمان سرویس است و برای کسی که به Docker سرور دسترسی دارد قابل مشاهده است.
- تأیید پیامکی شماره، بازیابی رمز و ثبت‌نام آزاد همچنان غیرفعال یا پیاده‌نشده‌اند ([BUSINESS_DECISIONS](BUSINESS_DECISIONS.md)).

## ۵. عیب‌یابی

| نشانه | علت و راه‌حل |
|---|---|
| Testcontainers: «Could not find a valid Docker environment» | Docker Engine ۲۹ کمینهٔ API را بالا برده است؛ `pom.xml` مقدار `api.version=1.44` را تنظیم می‌کند. اگر مستقیم از IDE اجرا می‌کنید، همین system property را اضافه کنید. |
| پس از بازسازی gateway، ورود با خطای redirect یا اتصال BFF قطع است | BFF فضای شبکهٔ gateway را به اشتراک می‌گذارد؛ پس از هر recreate gateway: `docker compose --env-file .env up -d --force-recreate bff` |
| `redirect_uri` بدون پورت (`http://localhost/...`) | proxy جلویی `X-Forwarded-Proto` را بدون `X-Forwarded-Port` می‌فرستد؛ هر دو را بفرستید یا هیچ‌کدام. |
| CapRover: gateway با `host not found in upstream` بالا نمی‌آید | template قدیمی؛ upstream BFF باید متغیر (`set $bff`) باشد. |
| CapRover: سرویس‌ها از RabbitMQ خطای `ACCESS_REFUSED` می‌گیرند | image RabbitMQ باید `entrypoint.sh` را داشته باشد که کاربر `haramyar` را می‌سازد. |
| CapRover: Keycloak با `value too long for type character varying(36)` بالا نمی‌آید | realm قدیمی با شناسهٔ provider بلندتر از ۳۶ نویسه؛ Keycloak را از کامیت فعلی مستقر کنید. |
| bootstrap: `ARTIFACT_UNAVAILABLE ... HTTP 404` | image ماژول نسخهٔ جدید را سرو نمی‌کند یا image bootstrap قدیمی است؛ `npm run images` (شامل profile bootstrap) و استقرار مجدد ماژول. |
| صفحهٔ ورود Keycloak خطای «Invalid username» برای شماره | افزونهٔ SPI بارگذاری نشده؛ `docker compose logs keycloak` و وجود jar در image را بررسی کنید. |
| RabbitMQ با کاربر پیش‌فرض بالا نمی‌آید | `deployment/rabbitmq/entrypoint.sh` کاربر را از `RABBITMQ_PASSWORD` می‌سازد؛ volume قدیمی با رمز دیگر را حذف کنید (فقط محیط محلی). |
| تم مناسبت آزمایشی باقی مانده است (تست E2E نیمه‌کاره) | مناسبت `e2e-*` را در «مدیریت مناسبت‌ها» بایگانی کنید. |
