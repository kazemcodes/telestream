package com.telestream.i18n

object I18n {
    private val en = mapOf(
        "welcome" to """
🎬 *Welcome to TeleStream Bot!*

Discover and stream movies, TV shows, and anime powered by native CloudStream sources.

👇 *Send me any title to search or choose an option below:*
""".trimIndent(),
        "choose_lang" to "🌐 *Please choose your language:*",
        "lang_changed" to "✅ Language set to English.",
        "search_prompt" to "🔍 *Please type the name of the movie or series:*",
        "searching" to "🔎 *Searching across sources for:* `%s`...",
        "no_results" to "❌ *No results found for:* `%s`\nCheck the spelling or try another title.",
        "search_results" to "🎬 *Search results for:* `%s`\nSelect a title below:",
        "details_card" to """
🎬 *%s* (%s)

⭐ *Rating:* %s
📁 *Type:* %s
🌐 *Source:* %s

📝 *Synopsis:*
%s
""".trimIndent(),
        "episodes_list" to "📺 *Episodes List for %s:*\nSelect an episode:",
        "resolving_links" to "⏳ *Extracting stream and download links...*",
        "links_ready" to "🎬 *Stream & Download Links for:*\n*%s*\n\nSelect a server or quality below:",
        "no_links" to "⚠️ *No direct streams found for this item.*",
        "btn_webapp" to "🚀 Open TeleStream App",
        "btn_search" to "🔍 Search",
        "btn_bookmarks" to "⭐ Bookmarks",
        "btn_repos" to "📦 Repositories",
        "btn_sync" to "🔄 Sync Repositories",
        "btn_donate" to "☕ Donate / Support",
        "btn_lang" to "🌐 Language / زبان",
        "btn_watch" to "▶️ Watch Online / Download",
        "btn_episodes" to "📺 Episodes",
        "btn_bookmark" to "⭐ Bookmark",
        "btn_unbookmark" to "❌ Remove Bookmark",
        "btn_back" to "⬅️ Back",
        "btn_close" to "✖️ Close",
        "btn_toggle_nsfw" to "🔞 Toggle NSFW Sources",
        "bookmarked" to "⭐ Saved to your bookmarks!",
        "unbookmarked" to "🗑️ Removed from bookmarks.",
        "no_bookmarks" to "📭 You don't have any bookmarks saved.",
        "my_bookmarks" to "⭐ *Your Saved Bookmarks:*",
        "repos_summary" to """
📦 *CloudStream Repositories & Extensions*
(Source: [cloudstreamrepo.com](https://cloudstreamrepo.com/))

Total Repositories Synced: *%d*
Total Available Extensions: *%d*

👇 *Click below to sync or type `/addrepo <url>` to add custom repos:*
""".trimIndent(),
        "syncing" to "⏳ *Syncing repositories from cloudstreamrepo.com...*\nPlease wait a few seconds.",
        "sync_done" to "✅ *Sync completed!* Fetched *%d* extensions across *%d* repositories.",
        "donate_msg" to """
💎 *Support TeleStream Project*

If you enjoy using this bot, please consider supporting development and server costs with a crypto donation!

💵 *USDT (TRC20):*
`%s`

⚡ *TON (Telegram / TON Wallet):*
`%s`

🪙 *Bitcoin (BTC):*
`%s`

🔷 *Ethereum (ERC20):*
`%s`

🙏 *Thank you so much for your generosity!*
""".trimIndent(),
        "admin_only" to "⛔ *Access Denied:* This command is restricted to bot administrators.",
        "admin_stats" to """
📊 *TeleStream Bot Admin Dashboard*

👥 *Total Users:* %d
⭐ *Total Bookmarks:* %d
📦 *Synced Repositories:* %d
🧩 *Total Extensions:* %d
🔞 *NSFW Sources:* %s
⚙️ *JVM Memory Used:* %d MB / %d MB
""".trimIndent(),
        "nsfw_toggled" to "🔞 NSFW content sources are now *%s*.",
        "nsfw_on" to "ENABLED",
        "nsfw_off" to "DISABLED",
        "admin_help" to """
⚙️ *Admin Commands:*
• `/admin` - View dashboard & toggle NSFW
• `/nsfw on|off` - Toggle adult sources
• `/sync` - Sync repositories
• `/addrepo <url>` - Add custom repository
""".trimIndent()
    )

    private val fa = mapOf(
        "welcome" to """
🎬 *به ربات تله‌استریم (TeleStream) خوش آمدید!*

جستجو و پخش آنلاین انواع فیلم‌ها، سریال‌ها و انیمه‌ها با پشتیبانی مستقیم از سورس‌های قدرتمند کلوداستریم.

👇 *نام فیلم یا سریال مورد نظرتان را بفرستید یا از دکمه‌های زیر استفاده کنید:*
""".trimIndent(),
        "choose_lang" to "🌐 *لطفاً زبان مورد نظر را انتخاب کنید:*",
        "lang_changed" to "✅ زبان به فارسی تغییر کرد.",
        "search_prompt" to "🔍 *نام فیلم یا سریال مورد نظرتان را ارسال کنید:*",
        "searching" to "🔎 *در حال جستجو برای:* `%s`...",
        "no_results" to "❌ *نتیجه‌ای برای* `%s` *یافت نشد.*\nلطفاً املای عنوان را بررسی کرده یا نام انگلیسی آن را امتحان کنید.",
        "search_results" to "🎬 *نتایج جستجو برای:* `%s`\nیکی از عناوین زیر را انتخاب کنید:",
        "details_card" to """
🎬 *%s* (%s)

⭐ *امتیاز:* %s
📁 *نوع:* %s
🌐 *منبع:* %s

📝 *خلاصه داستان:*
%s
""".trimIndent(),
        "episodes_list" to "📺 *قسمت‌های سریال %s:*\nیک قسمت را انتخاب کنید:",
        "resolving_links" to "⏳ *در حال استخراج لینک‌های پخش و دانلود...*",
        "links_ready" to "🎬 *لینک‌های پخش و دانلود برای:*\n*%s*\n\nسرور یا کیفیت مورد نظر را انتخاب کنید:",
        "no_links" to "⚠️ *متأسفانه لینک فعالی برای این عنوان یافت نشد.*",
        "btn_webapp" to "🚀 اجرای مینی‌اپ تله‌استریم",
        "btn_search" to "🔍 جستجو",
        "btn_bookmarks" to "⭐ نشان‌شده‌ها",
        "btn_repos" to "📦 مخازن سورس‌ها",
        "btn_sync" to "🔄 همگام‌سازی مخازن",
        "btn_donate" to "☕ حمایت مالی (Donate)",
        "btn_lang" to "🌐 تغییر زبان / Language",
        "btn_watch" to "▶️ تماشا / دانلود",
        "btn_episodes" to "📺 لیست قسمت‌ها",
        "btn_bookmark" to "⭐ افزودن به نشان‌شده‌ها",
        "btn_unbookmark" to "❌ حذف از نشان‌شده‌ها",
        "btn_back" to "⬅️ بازگشت",
        "btn_close" to "✖️ بستن",
        "btn_toggle_nsfw" to "🔞 تغییر وضعیت محتوای بزرگسال",
        "bookmarked" to "⭐ به نشان‌شده‌های شما افزوده شد!",
        "unbookmarked" to "🗑️ از نشان‌شده‌ها حذف شد.",
        "no_bookmarks" to "📭 هیچ فیلم یا سریالی در لیست نشان‌شده‌های شما نیست.",
        "my_bookmarks" to "⭐ *فیلم‌ها و سریال‌های نشان‌شده شما:*",
        "repos_summary" to """
📦 *مخازن و افزونه‌های کلوداستریم*
(منبع: [cloudstreamrepo.com](https://cloudstreamrepo.com/))

تعداد کل مخازن همگام‌شده: *%d*
تعداد کل افزونه‌های در دسترس: *%d*

👇 *روی دکمه زیر برای همگام‌سازی کلیک کنید یا با دستور `/addrepo <url>` مخزن جدید اضافه کنید:*
""".trimIndent(),
        "syncing" to "⏳ *در حال دریافت و همگام‌سازی مخازن از cloudstreamrepo.com...*\nچند ثانیه شکیبا باشید.",
        "sync_done" to "✅ *همگام‌سازی با موفقیت انجام شد!* تعداد *%d* افزونه از *%d* مخزن دریافت شد.",
        "donate_msg" to """
💎 *حمایت مالی از توسعه پروژه تله‌استریم*

اگر از این ربات رایگان استفاده می‌کنید و برایتان کاربردی است، می‌توانید با دونیت کریپتو به نگهداری سرورها و توسعه سورس‌ها کمک کنید:

💵 *تتر USDT (شبکه TRC20):*
`%s`

⚡ *تون کوین TON (شبکه TON / تلگرام):*
`%s`

🪙 *بیت‌کوین (BTC):*
`%s`

🔷 *اتریوم (ERC20):*
`%s`

🙏 *با لمس هر آدرس می‌توانید آن را کپی کنید. از مهر و همراهی شما سپاسگزاریم!*
""".trimIndent(),
        "admin_only" to "⛔ *دسترسی غیرمجاز:* این دستور فقط برای مدیران ربات تعریف شده است.",
        "admin_stats" to """
📊 *داشبورد مدیریت ربات تله‌استریم*

👥 *تعداد کل کاربران:* %d
⭐ *تعداد فیلم‌های نشان‌شده:* %d
📦 *مخازن همگام‌شده:* %d
🧩 *کل افزونه‌ها:* %d
🔞 *وضعیت سورس‌های بزرگسال (NSFW):* %s
⚙️ *حافظه مصرفی JVM:* %d مگابایت از %d مگابایت
""".trimIndent(),
        "nsfw_toggled" to "🔞 سورس‌های محتوای بزرگسال اکنون *%s* شدند.",
        "nsfw_on" to "فعال (مجاز)",
        "nsfw_off" to "غیرفعال (محدود)",
        "admin_help" to """
⚙️ *دستورات مدیریت:*
• `/admin` - مشاهده داشبورد و آمار سیستم
• `/nsfw on|off` - محدودسازی یا فعال‌سازی سورس‌های بزرگسال
• `/sync` - همگام‌سازی مخازن کلوداستریم
• `/addrepo <url>` - افزودن مخزن دلخواه
""".trimIndent()
    )

    fun donationMessage(lang: String = "en"): String {
        return t(
            "donate_msg",
            lang,
            com.telestream.config.Config.usdtTrc20,
            com.telestream.config.Config.tonWallet,
            com.telestream.config.Config.btcWallet,
            com.telestream.config.Config.ethWallet
        )
    }

    fun t(key: String, lang: String = "en", vararg args: Any): String {
        val dict = if (lang == "fa") fa else en
        val template = dict[key] ?: en[key] ?: key
        return if (args.isNotEmpty()) {
            try {
                String.format(template, *args)
            } catch (e: Exception) {
                template
            }
        } else {
            template
        }
    }
}
