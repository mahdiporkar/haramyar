# ممیزی واقعی سرور و طرح ظرفیت — ۲۰۲۶-۱۰-۱۶

این گزارش جایگزین وضعیت دسترسی و پروژه در گزارش preflight است. استقرار هنوز انجام نشده است. ورود SSH و sudo با `codexdeploy` موفق بود. تمام دستورات سرور فقط‌خواندنی بودند؛ هیچ سرویس، داده، DNS، firewall یا تنظیم SSH تغییر نکرد.

## سرور

| مورد | نتیجهٔ واقعی |
|---|---|
| مقصد | `141.11.45.119:9011` |
| سیستم‌عامل | Ubuntu 24.04.5 LTS |
| Docker / Swarm | 29.9.0 / active |
| CapRover | 1.15.4، یک replica فعال |
| CPU | ۲ هسته |
| RAM | 3916 MiB کل؛ حدود 1380 MiB در دسترس |
| Swap | 2047 MiB؛ حدود 299 MiB مصرف‌شده |
| دیسک ریشه | 30 GiB؛ 12 GiB آزاد؛ inode مصرفی 15٪ |
| Docker | 19 کانتینر؛ 46 image؛ 14 volume؛ build cache حدود 2.84 GB |

سرویس‌های فعلی شامل auth، auth-db، erp، erp-db، hive-authorization، hive-bff، hive-console، hive-db، hive-graph-db، hive-openfga، hive-redis، university-api، husbantech-web و سرویس‌های CapRover هستند. همهٔ سرویس‌های Swarm مشاهده‌شده 1/1 بودند؛ کانتینرهای دارای healthcheck در خروجی docker ps سالم گزارش شدند. این مشاهده جایگزین آزمون کاربردی نیست. هیچ‌یک برای HaramYar بازپیکربندی یا استفادهٔ مجدد نشدند.

## دامنه، HTTPS و شبکه

- کاربر `haramyar.com` را تأیید کرد؛ از داخل سرور به IP مقصد resolve شد.
- NSها `lucy.ns.cloudflare.com` و `tanner.ns.cloudflare.com` هستند؛ تنظیم proxy پنل بررسی نشده است.
- دامنه از قبل به `haramyar-web` متصل است؛ image فعلی `caprover/caprover-placeholder-app:latest` است، نه برنامهٔ HaramYar.
- HTTPS با اعتبارسنجی معمول curl پاسخ 200 داد. گواهی Let's Encrypt از ۱۶ اکتبر ۲۰۲۶ تا ۱۴ ژانویهٔ ۲۰۲۷ معتبر است.
- HTTP نیز 200 می‌دهد و `forceSsl=false` است؛ هدایت HTTPS هنوز فعال نشده است.
- ریشهٔ CapRover برابر `husbantech.com`، شبکه `captain-overlay-network` و alias اپ هدف `srv-captain--haramyar-web` است.
- پورت‌های عمومی شنونده شامل 80، 443، 3000 و 9011 هستند؛ Swarm روی 2377 و 7946 نیز گوش می‌دهد. UFW فعال است و 80/443/3000/9011 در قواعد مجازند. آزمون جامع دسترسی بیرونی اجرا نشده است.
- دیتابیس‌ها پورت host منتشرشده در docker ps نداشتند. ابزارهای موجود Docker proxy و release gateway روی loopback هستند.
- registry تنظیم‌شده GHCR است. `/v2/` در GHCR و Docker Hub پاسخ 401 داد: اتصال HTTPS برقرار است، ولی pull مجاز و push هنوز آزمایش نشده‌اند. شنونده‌ای روی 996 مشاهده نشد.

## داده و پشتیبان

volumeها حفظ شدند. فایل‌های dump با نام auth-db، erp-db، hive-db و hive-graph-db در `/var/backups/hive-demo` وجود دارند؛ تازگی، کامل‌بودن و قابلیت restore آن‌ها تأیید نشده است. دیتابیس یا volume اختصاصی HaramYar هنوز ایجاد نشده است.

## پروژه و اعتبارسنجی

- مخزن `https://github.com/mahdiporkar/haramyar.git`، شاخهٔ `main`، HEAD مشاهده‌شده `2a02027`. working tree تغییرات ثبت‌نشده دارد و release تغییرناپذیر هنوز تعیین نشده است.
- اکنون frontendها، Dockerfileها، Compose محلی، realm و اسکریپت ثبت Hive در workspace موجودند. تغییرات موجود حفظ شدند.
- `npm run typecheck`: موفق.
- `npm run build`: ابتدا sandbox با `spawn EPERM` متوقف شد؛ اجرای مجاز خارج sandbox موفق بود و هر دو MFE، shell و public-web ساخته شدند.
- ساخت shell هشدار مسیر فونت داد؛ فایل فونت در خروجی وجود دارد. بارگذاری مرورگری بررسی نشده است.
- Compose موجود برای توسعه است؛ profile توسعه، hostname محلی و شبکهٔ مشترک gateway/BFF باید برای production تطبیق داده شوند.
- imageهای فعلی هر ماژول frontend و backend را با هم بسته‌بندی می‌کنند. استقلال انتشار frontend/backend طبق درخواست هنوز نیازمند تفکیک artifact hosting و ثبت manifest است.
- workflow استقرار در بررسی فعلی موجود نبود. GitHub Actions، push image، migration، restore و آزمون کاربردی production اجرا نشده‌اند.

## توپولوژی و بودجهٔ اولیه

اعداد زیر بودجهٔ طراحی‌اند، نه مصرف تضمین‌شده یا نتیجهٔ load test.

| اپ پیشنهادی | پورت داخلی | RAM اولیه |
|---|---:|---:|
| haramyar-web (gateway + shell) | 8080 | 64 MiB |
| haramyar-public-web | 8093 | 128 MiB |
| frontend مستقل Booking و Martyrs | 8080 | هرکدام 32 MiB |
| haramyar-booking-api | 8090 | 384 MiB |
| haramyar-martyrs-api | 8091 | 384 MiB |
| haramyar-shell-service | 8092 | 384 MiB |
| haramyar-hive-bff | 8081 | 384 MiB |
| haramyar-hive-authorization | 8082 | 384 MiB |
| haramyar-keycloak | 8080 / health 9000 | 640 MiB |
| haramyar-postgres | 5432 | 256 MiB |
| haramyar-openfga | 8080 / gRPC 8081 | 96 MiB |
| haramyar-redis | 6379 | 64 MiB |
| haramyar-rabbitmq | 5672 | 256 MiB |
| haramyar-object-storage | مطابق image نهایی | 256 MiB |

جمع بودجه 3744 MiB (حدود 3.66 GiB)، بدون فضای احتیاط سیستم و deployment است؛ این طرح در 1380 MiB حافظهٔ در دسترس فعلی جا نمی‌گیرد. پیشنهاد: RAM کل حداقل 8 GiB، سپس اعتبارسنجی بار و حافظه. برای رشد ظرفیت بیشتری ممکن است لازم باشد. گزینهٔ ارتقا، سرور جدا یا بررسی طرح مرحله‌ای از کاربر پرسیده شد. سرویس جدیدی راه‌اندازی نشد.

مسیر مرورگر `https://haramyar.com` خواهد بود؛ gateway احراز هویت را به Keycloak اختصاصی و APIها را به BFF اختصاصی هدایت می‌کند. business APIها و زیرساخت عمومی نمی‌شوند. PostgreSQL اختصاصی با database/owner جدا برای Hive، Keycloak، OpenFGA، Booking، Martyrs و shell در نظر گرفته می‌شود. اسرار production باید مستقل از محیط توسعه تولید شوند.

ادامه: تعیین ظرفیت و release؛ ساخت و تست imageها خارج VPS؛ snapshot تنظیم اپ هدف؛ storage و زیرساخت اختصاصی؛ identity و Hive؛ backendها و artifactهای مستقل؛ ثبت و فعال‌سازی manifestها؛ انتشار gateway؛ HTTPS redirect؛ smoke test ورود و مجوزها و booking/martyrs؛ سپس انتشار خودکار با توکن هر اپ و tag مبتنی بر SHA. rollback برنامه به image قبلی محدود می‌شود؛ rollback مخرب دیتابیس انجام نمی‌شود.
