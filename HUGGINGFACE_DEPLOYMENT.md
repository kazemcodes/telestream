# 🚀 راهنمای جامع استقرار ربات TeleStream روی Hugging Face Spaces (رایگان)

سرویس **Hugging Face Spaces** امکان اجرای کانتینرهای **Docker** را به صورت **کاملاً رایگان** با سخت‌افزار قدرتمند (**۲ هسته پردازنده vCPU و ۱۶ گیگابایت رم**) فراهم می‌کند. از آنجا که ربات TeleStream به صورت Pure JVM نوشته شده و از موتور توکار Ktor و مکانیزم Long-Polling تلگرام استفاده می‌کند، به صورت کامل و بدون نیاز به هیچ سرور VPS روی Hugging Face Spaces کار می‌کند.

---

## 📋 فهرست مراحل استقرار

1. [پیش‌نیازها](#۱-پیشنیازها)
2. [ایجاد Space در Hugging Face](#۲-ایجاد-space-در-hugging-face)
3. [پیکربندی متادیتای README.md](#۳-پیکربندی-متادیتای-readmemd)
4. [تنظیم متغیرها و کلیدهای محرمانه (Secrets)](#۴-تنظیم-متغیرها-و-کلیدهای-محرمانه-secrets)
5. [ارسال سورس‌کد به Hugging Face (Git Push)](#۵-ارسال-سورس‌کد-به-hugging-face-git-push)
6. [روشن نگه داشتن ۲۴ ساعته (جلوگیری از Sleep)](#۶-روشن-نگه-داشتن-۲۴-ساعته-جلوگیری-از-sleep)
7. [بررسی لاگ‌ها و عیب‌یابی](#۷-بررسی-لاگ‌ها-و-عیب‌یابی)

---

## ۱. پیش‌نیازها

* داشتن حساب کاربری در [Hugging Face](https://huggingface.co) (رایگان).
* توکن ربات تلگرام از طریق [@BotFather](https://t.me/BotFather).
* شناسه عددی اکانت تلگرام شما از طریق [@userinfobot](https://t.me/userinfobot) (برای متغیر `ADMIN_ID`).
* نصب بودن Git روی سیستم شما.

---

## ۲. ایجاد Space در Hugging Face

1. وارد اکانت Hugging Face خود شوید و به آدرس [huggingface.co/new-space](https://huggingface.co/new-space) بروید.
2. فیلدها را به شکل زیر پر کنید:
   * **Space name**: نام دلخواه، مثلاً `telestream-bot`
   * **License**: گزینه `mit` یا `apache-2.0`
   * **Select the Space SDK**: حتماً روی **Docker** کلیک کنید.
   * **Docker template**: گزینه **Blank** را انتخاب نمایید.
   * **Space hardware**: گزینه پیش‌فرض رایگان (**CPU basic • 2 vCPU • 16 GB RAM**).
3. روی دکمه **Create Space** کلیک کنید.

---

## ۳. پیکربندی متادیتای `README.md`

پلتفرم Hugging Face برای تشخیص مشخصات کانتینر داکر، به یک بلوک YAML در بالای فایل `README.md` نیاز دارد. در ریپازیتوری ارسالی به Space، بالای `README.md` باید این بلوک وجود داشته باشد:

```yaml
---
title: TeleStream Bot
emoji: 🎬
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
---
```

> **نکته مهم:** پورت `7860` پورت استاندارد Hugging Face است که فایل `Dockerfile` و سرور Ktor پروژه روی آن تنظیم شده‌اند.

---

## ۴. تنظیم متغیرها و کلیدهای محرمانه (Secrets)

توکن تلگرام و سایر کلیدها نباید داخل کد کامیت شوند. آن‌ها را در بخش Secrets اضافه کنید:

1. در صفحه Space خود به تب **Settings** بروید.
2. به بخش **Variables and secrets** اسکرول کنید.
3. در قسمت **New secret**، کلیدهای زیر را اضافه کنید:

| نام Secret | مقدار | توضیحات |
| :--- | :--- | :--- |
| `BOT_TOKEN` | `123456789:ABCdef...` | توکن دریافتی از BotFather |
| `ADMIN_ID` | `12345678` | شناسه عددی اکانت ادمین |
| `ENABLE_DOH` | `true` | فعال‌سازی DNS over HTTPS برای عبور از فیلترینگ دامنه‌ها |

*(اختیاری)* اگر آدرس اختصاصی وب‌پلیر یا دامنه خاصی دارید، متغیر `WEBAPP_URL` را نیز اضافه کنید. (به طور پیش‌فرض، ربات مقدار `SPACE_HOST` مربوط به Hugging Face را خودکار شناسایی می‌کند).

---

## ۵. ارسال سورس‌کد به Hugging Face (Git Push)

در صفحه اصلی Space ساخته شده، آدرس مخزن گیت به صورت زیر به شما نمایش داده می‌شود:
`https://huggingface.co/spaces/<نام-کاربری>/<نام-اسپیس>`

### روش اول: اضافه کردن Remote به همین پروژه و Push
در ترمینال پروژه (در پوشه telestream):

```powershell
# ۱. اضافه کردن ریموت هاگینگ‌فیس
git remote add hf https://huggingface.co/spaces/<your-username>/<space-name>

# ۲. ایجاد یک توکن دسترسی با دسترسی Write از آدرس https://huggingface.co/settings/tokens
# (در هنگام push نام کاربری اکانت و پسورد همان Access Token خواهد بود)

# ۳. ارسال کدها به هاگینگ‌فیس
git push hf master:main --force
```

### روش دوم: کلون مخزن Space و کپی فایل‌ها
```powershell
git clone https://huggingface.co/spaces/<your-username>/<space-name> hf-space
# کپی تمامی فایل‌های پروژه (به جز پوشه sample، .gradle، .git) به داخل hf-space
cd hf-space
git add .
git commit -m "Deploy TeleStream to Hugging Face"
git push
```

پس از Push، هاگینگ‌فیس به صورت خودکار شروع به Build کردن `Dockerfile` می‌کند (حدود ۳ الی ۵ دقیقه طول می‌کشد).

---

## ۶. روشن نگه داشتن ۲۴ ساعته (جلوگیری از Sleep)

اسپیس‌های رایگان Hugging Face اگر در طول ۴۸ ساعت هیچ درخواست وب (HTTP) دریافت نکنند، به حالت Pause / Sleep می‌روند.
برای اینکه ربات تلگرام شما ۲۴ ساعته و بدون وقفه فعال بماند:

1. آدرس عمومی اسپیس خود را پیدا کنید:
   `https://<your-username>-<space-name>.hf.space`
2. پروژه TeleStream دارای اندپوینت سلامت (`/health`) است:
   `https://<your-username>-<space-name>.hf.space/health`
3. در یک سرویس رایگان مانند [cron-job.org](https://cron-job.org) یا [UptimeRobot](https://uptimerobot.com) عضو شوید.
4. یک مانیتور رایگان با متد **GET** و بازه زمانی **هر ۵ دقیقه** یا **هر ۱۰ دقیقه** روی لینک `/health` تنظیم کنید.
5. این درخواست‌های منظم باعث می‌شود Hugging Face اسپیس را همیشه در حالت **Running** نگه دارد!

---

## ۷. بررسی لاگ‌ها و عیب‌یابی

* **مشاهده لاگ‌های زنده**: در صفحه Space، روی تب **Logs** کلیک کنید. شما لاگ‌های کاتلین، راه‌اندازی سورس‌ها و وضعیت اتصال به تلگرام را به صورت زنده مشاهده خواهید کرد.
* **تست وب‌پلیر**: در مرورگر آدرس `https://<your-username>-<space-name>.hf.space` را باز کنید؛ رابط کاربری وب‌پلیر TeleStream نمایش داده می‌شود.
* **شروع کار در تلگرام**: به ربات تلگرام خود دستور `/start` را بفرستید.
