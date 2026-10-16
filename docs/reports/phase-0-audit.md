# گزارش فاز ۰ — ممیزی پلتفرم Hive و نمونهٔ Figma

## ورودی‌ها

| ورودی | منبع واقعی بررسی‌شده | نسخه |
|---|---|---|
| Archive A (Hive) | مخزن `mahdiporkar/hive-platform`، شاخهٔ `fix/bff-identity-control-internal-http` (کامیت `ee7a1c6`)، کپی محلی در `../hive-platform` | `0.1.0-dev.0` (همان نسخهٔ ذکرشده در مشخصات) |
| Archive B (Figma) | `figma.zip` در ریشهٔ همین مخزن (خروجی Figma Make) | — |

فایل ZIP مربوط به Hive در مخزن موجود نبود؛ کپی محلی Hive که همان نسخهٔ `0.1.0-dev.0` است جایگزین آن شد. هیچ قابلیتی از نسخه‌های دیگر Hive فرض نشده است.

## اسناد و کد خوانده‌شده

اسناد: `architecture`, `platform-principles`, `consumer-guide`, `identity`, `authorization`, `public-hybrid-authenticated`, `dynamic-routing`, `mfe-registration`, `manifest-governance`, `compatibility`, `security`, `deployment`, `react-tailwind-guide`, `mfe-runtime`, `workspace-runtime`, `cli`, `resource-catalog`, `extension-points` و ADRها.

کد: `services/bff` (امنیت، `IdentityConfiguration`، `RuntimeProxy`)، `services/authorization` (`IdentityRegistry`، `AccessController`، `AccessAdministration`، `AdminAuthorization`، `RoutePathPolicy`، `RuntimeContexts`)، `packages/contracts|http-client|auth|react|mfe-runtime|workspace|core`، `apps/default-shell`، `examples/react-tailwind-consumer`، `cli/src/register.mjs` و `starters/hive-spring-boot-starter`.

## یافته‌های تعیین‌کننده

1. **ایجاد کاربر بدون نقش.** BFF پس از ورود OIDC، `IdentityRegistry.sync` را صدا می‌زند و فقط کاربر canonical می‌سازد. هیچ نقش یا grant پیش‌فرضی داده نمی‌شود. «عضویت» در یک اپلیکیشن یعنی داشتن دست‌کم یک مجوز در آن. پس **ورود موفق به‌تنهایی اجازهٔ ثبت رزرو نمی‌دهد** و HaramYar به یک مکانیزم onboarding نیاز دارد.
2. **تخصیص نقش فقط با دسترسی ممتاز ممکن است.** `POST /admin/roles/{key}/assignments` به نقش پلتفرمی `SECURITY_ADMIN` یا اعتبارنامهٔ ماشینی `provisioner` نیاز دارد. برای همین onboarding در سرویس جداگانهٔ `shell-service` قرار گرفت تا این اعتبارنامه از ماژول‌های کسب‌وکار جدا بماند.
3. **کلیدهای نقش و منبع** باید با `[a-z][a-z0-9._-]*` منطبق باشند. نقش‌های کسب‌وکار به‌صورت `haramyar-user`، `rozeh-coordinator` و … ثبت می‌شوند (نگاشت در `docs/IDENTITY_AND_ACCESS.md`).
4. **یک issuer اصلی.** ارائه‌دهندهٔ پویا (dynamic provider) نمی‌تواند issuer اصلی را به کار ببرد. بنابراین دو client جدا در یک realm ممکن نیست و جداسازی کامل کارکنان به realm دوم نیاز دارد. برای MVP یک realm انتخاب شد. MFA کارکنان با جریان شرطی Keycloak مبتنی بر نقش realm اعمال می‌شود.
5. **issuer باید برای مرورگر و BFF یکسان باشد** و HTTP ساده فقط روی `localhost` پذیرفته می‌شود. در Docker Compose محلی، BFF فضای شبکهٔ gateway را به اشتراک می‌گذارد (`network_mode: service:gateway`). به این ترتیب `http://localhost:8080/realms/haramyar` از هر دو طرف یکسان است.
6. **سرآیندهای ارسالی به سرویس‌ها** محدود به `Accept`، `Accept-Language`، `Content-Type` و `If-*` و سرآیندهای Hive هستند. بنابراین `Idempotency-Key` به backend نمی‌رسد و کلید idempotency در بدنهٔ درخواست (`clientRequestId`) ارسال می‌شود.
7. **الگوی مسیر عملیات‌ها**: `{param}`، `*` و `**` انتهایی، نسبت به پیشوند مسیر. الگوهای هم‌پوشان با اولویت برابر رد می‌شوند و مسیرها طوری طراحی شدند که ابهام نداشته باشند.
8. **Service Worker نمونهٔ Figma** همهٔ پاسخ‌های GET موفق هم‌مبدأ را کش می‌کند (از جمله `/api/**`). این رفتار اصلاح شد.
9. **نمونهٔ Figma** یک `App.tsx` حدوداً ۴۰۰ خطی با سوئیچ صفحه در حافظه، داده‌های نمایشی ثابت و دیکشنری سه‌زبانهٔ fa/ar/en برای صفحات پوسته است. صفحات ماژول‌ها فقط فارسی‌اند.

## ماتریس قابلیت‌ها

| قابلیت | فراهم‌شده توسط Hive | نیاز HaramYar | رویکرد یکپارچه‌سازی |
|---|---|---|---|
| ورود OIDC، نشست، token vault، CSRF | بله (BFF) | بله | استفادهٔ مستقیم. Keycloak به‌عنوان issuer اصلی (`HIVE_OIDC_*`) |
| شناسهٔ canonical کاربر | بله (`hive_user`، `X-Hive-User-Id`) | بله | کلید مالکیت همهٔ داده‌های کسب‌وکار. شماره موبایل هرگز کلید نیست |
| نقش‌های پلتفرمی | بله | فقط برای اپراتورها | تخصیص خودکار به کاربران یا مدیران کسب‌وکار ممنوع است |
| نقش‌ها و grantهای کسب‌وکار | بله (`/admin/roles`، `/admin/grants`) | بله | اسکریپت `integrations/hive/access-bootstrap.mjs` |
| نقش پیش‌فرض برای کاربر جدید | **خیر** | بله | `shell-service` با endpoint `HYBRID /onboarding` و اعتبارنامهٔ provisioner (idempotent) |
| کاتالوگ منابع و manifestها | بله | بله | manifest منابع و micro-frontend برای هر ماژول، ثبت با `hive register` |
| مسیریابی پویا به backend | بله (`/api/routes/**`) | بله | یک service target و یک proxy route برای هر backend، با عملیات `PUBLIC`/`HYBRID`/`AUTHENTICATED` |
| بارگذاری MFE با SRI و gateway آرتیفکت | بله | بله | هر ماژول آرتیفکت خود را از کانتینر خودش سرو می‌کند و Hive آن را با `/api/mfe/**` در دسترس قرار می‌دهد |
| زمینهٔ عمومی و خصوصی | بله (`/api/public/context`، `/api/me/context`) | بله | پوستهٔ HaramYar میزبان اختصاصی و برندشده است و از `@hive-platform/auth` و `mfe-runtime` استفاده می‌کند |
| EventPort در فرانت‌اند | بله | فقط ارتباط runtime | تغییر تم و زبان در پوسته. پیام‌رسانی پایدار backend با RabbitMQ |
| ممیزی و API log | بله | بله | خودکار برای همهٔ درخواست‌های proxy. ممیزی دامنه در جدول تاریخچهٔ وضعیت |
| correlation id، PlatformError، ProductionGuard | بله (`hive-spring-boot-starter`) | بله | وابستگی مستقیم backendها به starter |
| پیام‌رسانی پایدار (Outbox/Inbox/DLQ) | خیر | بله | پیاده‌سازی در `shared/service-kit` (مسئولیت راه‌حل) |
| ذخیره‌سازی رسانه | خیر | بله | MinIO/S3 در ماژول Martyrs |
| ثبت‌نام با موبایل و رمز | خیر (کار Keycloak است) | بله | realm `haramyar` به‌همراه SPI نرمال‌سازی موبایل و قالب برندشده |
| SSR صفحات عمومی | الگو دارد (public-web نمونه) | بله | `apps/public-web` (Node SSR از APIهای عمومی BFF) |
| کنسول اپراتور | بله | بله (برای اپراتورهای Hive) | بدون تغییر در `/console/` |
| فیچر فلگ | بله | خیر | تم‌ها پیکربندی کسب‌وکار هستند و در `shell-service` با مجوز کسب‌وکار مدیریت می‌شوند |

## خط پایهٔ تست Hive

ایمیج‌های Hive (`hive/bff`، `hive/authorization`) از همین کپی محلی ساخته و در تست‌های end-to-end HaramYar اجرا می‌شوند. نتایج اجرای واقعی در گزارش فاز ۱۰ آمده است.
