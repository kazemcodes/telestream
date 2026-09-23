package com.telestream.i18n

object I18n {
    private val en = mapOf(
        "welcome" to """
🎬 *Welcome to TeleStream!*

Stream and download movies, series, and anime natively on Telegram.

👇 *Send any title to search directly, or choose an option below:*
""".trimIndent(),
        "choose_lang" to "🌐 *Please choose your language:*",
        "lang_changed" to "✅ Language set to English.",
        "search_prompt" to "🔍 *Please type the name of the movie or series:*",
        "search_prompt_direct" to "🔍 *Active Source:* `%s`\n\nSend me the name of any movie or series to search:",
        "search_prompt_source" to "🔍 *Active Source:* `%s`\n\nSend me the name of the movie or series to search in this source:",
        "searching" to "🔎 *Searching for:* `%s`...",
        "searching_in_source" to "🔎 *Searching in `%s` for:* `%s`...",
        "no_results" to "❌ *No results found for:* `%s`\nCheck the spelling or try another title.",
        "no_results_in_source" to "❌ *No results found in `%s` for:* `%s`\nCheck spelling or try another source.",
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
        "episodes_page" to "📺 *%s*\nEpisodes list (Page %d of %d):\nSelect an episode below:",
        "resolving_links" to "⏳ *Extracting stream and download links...*",
        "links_ready" to "🎬 *Stream & Download Links for:*\n*%s*\n\nSelect a server or quality below:",
        "quality_selection" to "🎬 *Stream & Download Options for:*\n*%s*\n\nChoose your preferred quality or server below:",
        "no_links" to "⚠️ *No direct streams found for this item.*",
        "btn_webapp" to "🚀 Open TeleStream App",
        "btn_search" to "🔍 Search",
        "btn_sources" to "📡 Sources",
        "btn_change_source" to "📡 Change Source",
        "btn_bookmarks" to "⭐ Bookmarks",
        "btn_repos" to "📦 Repositories",
        "btn_sync" to "🔄 Sync Repositories",
        "btn_donate" to "☕ Donate",
        "btn_lang" to "🌐 Language / زبان",
        "btn_watch" to "▶️ Watch Online / Download",
        "btn_episodes" to "📺 Episodes",
        "btn_bookmark" to "⭐ Bookmark",
        "btn_unbookmark" to "❌ Remove Bookmark",
        "btn_back" to "⬅️ Back",
        "btn_close" to "⬅️ Back",
        "btn_prev" to "◀️ Prev",
        "btn_next" to "Next ▶️",
        "btn_search_another_source" to "🔄 Search in another source",
        "choose_source_to_search" to "🔍 *Search for:* `%s`\n\n👇 *Select the source you want to search in:*",
        "search_prompt_pick_source" to "🔍 *Search Movies & Series*\n\n👇 *Please select a source to search in:*",
        "source_selected_prompt_query" to "🔍 *Source Selected:* *%s*\n\n👇 *Now send me the name of the movie or series to search:*",
        "btn_popular" to "🔥 Popular",
        "btn_latest" to "🆕 Latest",
        "feed_popular_title" to "🔥 *Popular & Trending Movies / Shows*\nSource: *%s*",
        "feed_no_items" to "⚠️ No movies or series found in this section.",
        "source_unreachable" to "⚠️ *Source '%s' Unreachable*\n\nCould not connect to this source (%s).\nThis usually happens when the source domain is blocked by DNS/ISP or temporarily offline.\n\n👇 Please try another source or retry:",
        "search_error_source" to "⚠️ *Error querying '%s'*\n\nConnection failed (%s).\nThe source may be blocked by your ISP or Cloudflare.\n\n👇 Try searching in another source:",
        "btn_retry" to "🔄 Try Again",
        "btn_check_sources" to "🩺 Check Sources Health",
        "sources_health_title" to "🩺 *Sources Connectivity & Health Status:*\n\n",
        "sources_health_ok" to "🟢 *%s:* Online (%d ms)",
        "sources_health_fail" to "🔴 *%s:* Offline / Blocked (%s)",
        "choose_source" to "📡 *Select Default Source*\nActive: *%s*\n\n👇 Tap any source to select it:",
        "sources_manager_title" to "📡 *Movie & Series Sources*\n\nActive Source: *%s*\n• Tap 📡 to set as active provider\n• Tap ✅ / ❌ to enable or disable",
        "source_selected" to "✅ Active source set to %s",
        "btn_live_search" to "⚡ Live Search Sources",
        "btn_search_source" to "🔍 Search by Name",
        "btn_clear_filter" to "❌ Clear Filter",
        "btn_quick_picks" to "⭐ Quick Picks",
        "source_search_prompt" to "🔍 *Search Sources by Name*\n\nType any part of the source or provider name (e.g. `Kiss`, `Ava`, `Flix`, `Anime`, `Arab`):\n\n_Or type `/source <name>` anytime to switch immediately!_",
        "sources_search_results" to "🔍 *Matching Sources for:* `%s` (%d found)\n\nActive Source: *%s*\n• Tap 📡 to set as active provider\n• Tap ✅ / ❌ to enable or disable",
        "no_sources_found" to "❌ No sources found matching `%s`.\nTry searching with a shorter keyword or browse with /sources.",
        "source_switched_ready" to "✅ *Active source set to %s* [%s]!\n\n💡 Send any movie or series title to start searching immediately.",
        "btn_admin_panel" to "⚙️ Admin Panel",
        "btn_toggle_nsfw" to "🔞 Toggle NSFW",
        "filter_all" to "🌐 All",
        "filter_fa" to "🇮🇷 Persian",
        "filter_ar" to "🇸🇦 Arabic",
        "filter_anime" to "🌏 Anime/Asian",
        "source_changed" to "✅ Active source changed to *%s*!\nAll searches will now query this source directly.",
        "btn_manage_sources" to "⚙️ Manage Sources",
        "btn_enabled_sources" to "📋 Enabled Sources",
        "btn_enable_all" to "✅ Enable All",
        "btn_disable_all" to "❌ Disable All",
        "all_languages" to "🌐 All Languages",
        "step_choose_provider" to "⚙️ *Source Manager — Step 1: Choose Provider / Repo*\n\nSelect a provider or repository below to configure its sources:",
        "step_choose_lang" to "⚙️ *Source Manager — Step 2: Choose Language*\nProvider / Repo: *%s*\n\nSelect a language to filter sources:",
        "manage_sources_list" to "⚙️ *Manage Sources: %s* (Language: *%s*)\nTap any source below to toggle between enabled (✅) and disabled (❌):",
        "enabled_sources_title" to "📋 *Your Enabled Sources (%d active)*\nClick any source to set it as your active search provider:",
        "source_toggled_on" to "✅ Enabled: %s",
        "source_toggled_off" to "❌ Disabled: %s",
        "bulk_enabled" to "✅ Enabled %d sources!",
        "bulk_disabled" to "❌ Disabled %d sources!",
        "no_enabled_sources" to "⚠️ *You have no enabled sources!* Please go to Sources to enable at least one source.",
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

پخش آنلاین و دانلود مستقیم فیلم‌ها، سریال‌ها و انیمه‌ها در تلگرام.

👇 *نام هر عنوان را ارسال کنید تا فوراً جستجو شود، یا از گزینه‌های زیر استفاده کنید:*
""".trimIndent(),
        "choose_lang" to "🌐 *لطفاً زبان مورد نظر را انتخاب کنید:*",
        "lang_changed" to "✅ زبان به فارسی تغییر کرد.",
        "search_prompt" to "🔍 *نام فیلم یا سریال مورد نظرتان را ارسال کنید:*",
        "search_prompt_direct" to "🔍 *منبع فعال:* `%s`\n\nنام هر فیلم یا سریالی را برای جستجو ارسال کنید:",
        "search_prompt_source" to "🔍 *منبع فعال:* `%s`\n\nنام فیلم یا سریال مورد نظرتان را برای جستجو در این منبع ارسال کنید:",
        "searching" to "🔎 *در حال جستجو برای:* `%s`...",
        "searching_in_source" to "🔎 *در حال جستجو در `%s` برای:* `%s`...",
        "no_results" to "❌ *نتیجه‌ای برای* `%s` *یافت نشد.*\nلطفاً املای عنوان را بررسی کرده یا نام انگلیسی آن را امتحان کنید.",
        "no_results_in_source" to "❌ *نتیجه‌ای در منبع `%s` برای* `%s` *یافت نشد.*\nلطفاً املای عنوان را بررسی کنید یا سورس دیگری را امتحان کنید.",
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
        "episodes_page" to "📺 *%s*\nلیست قسمت‌ها (صفحه %d از %d):\nیک قسمت را انتخاب کنید:",
        "resolving_links" to "⏳ *در حال استخراج لینک‌های پخش و دانلود...*",
        "links_ready" to "🎬 *لینک‌های پخش و دانلود برای:*\n*%s*\n\nسرور یا کیفیت مورد نظر را انتخاب کنید:",
        "quality_selection" to "🎬 *گزینه‌های پخش و دانلود برای:*\n*%s*\n\nکیفیت یا سرور مورد نظر خود را انتخاب کنید:",
        "no_links" to "⚠️ *متأسفانه لینک فعالی برای این عنوان یافت نشد.*",
        "btn_webapp" to "🚀 اجرای مینی‌اپ تله‌استریم",
        "btn_search" to "🔍 جستجو",
        "btn_sources" to "📡 سورس‌ها",
        "btn_change_source" to "📡 تغییر منبع",
        "btn_bookmarks" to "⭐ نشان‌شده‌ها",
        "btn_repos" to "📦 مخازن سورس‌ها",
        "btn_sync" to "🔄 همگام‌سازی مخازن",
        "btn_donate" to "☕ حمایت مالی",
        "btn_lang" to "🌐 تغییر زبان / Language",
        "btn_watch" to "▶️ تماشا / دانلود",
        "btn_episodes" to "📺 لیست قسمت‌ها",
        "btn_bookmark" to "⭐ افزودن به نشان‌شده‌ها",
        "btn_unbookmark" to "❌ حذف از نشان‌شده‌ها",
        "btn_back" to "⬅️ برگشت",
        "btn_close" to "⬅️ برگشت",
        "btn_prev" to "◀️ قبلی",
        "btn_next" to "بعدی ▶️",
        "btn_search_another_source" to "🔄 جستجو در منبع دیگر",
        "choose_source_to_search" to "🔍 *جستجو برای:* «`%s`»\n\n👇 *منبعی که می‌خواهید در آن جستجو شود را انتخاب کنید:*",
        "search_prompt_pick_source" to "🔍 *جستجوی فیلم و سریال*\n\n👇 *لطفاً ابتدا منبع مورد نظرتان را برای جستجو انتخاب کنید:*",
        "source_selected_prompt_query" to "🔍 *منبع انتخاب شد:* *%s*\n\n👇 *اکنون نام فیلم یا سریال مورد نظرتان را ارسال کنید:*",
        "btn_popular" to "🔥 محبوب‌ترین‌ها",
        "btn_latest" to "🆕 جدیدترین‌ها",
        "feed_popular_title" to "🔥 *فیلم‌ها و سریال‌های محبوب و پرطرفدار*\nمنبع: *%s*",
        "feed_no_items" to "⚠️ در حال حاضر موردی در این بخش یافت نشد.",
        "source_unreachable" to "⚠️ *عدم دسترسی به سورس '%s'*\n\nارتباط با این سورس برقرار نشد (%s).\nاین مشکل معمولاً به دلیل فیلترینگ اینترنت، قطعی موقت سرورهای سورس یا کلودفلر رخ می‌دهد.\n\n👇 لطفاً سورس دیگری را انتخاب کنید یا دوباره امتحان کنید:",
        "search_error_source" to "⚠️ *خطا در دریافت نتایج از '%s'*\n\nارتباط برقرار نشد (%s).\nاحتمالاً این سورس توسط اینترنت مسدود شده یا سرور آن قطع است.\n\n👇 جستجو در سورس دیگر را امتحان کنید:",
        "btn_retry" to "🔄 تلاش مجدد",
        "btn_check_sources" to "🩺 بررسی سلامت سورس‌ها",
        "sources_health_title" to "🩺 *وضعیت اتصال و سلامت سورس‌ها:*\n\n",
        "sources_health_ok" to "🟢 *%s:* فعال (%d میلی‌ثانیه)",
        "sources_health_fail" to "🔴 *%s:* مسدود یا قطع (%s)",
        "choose_source" to "📡 *انتخاب سورس پیش‌فرض*\nمنبع فعال: *%s*\n\n👇 جهت تغییر، سورس مورد نظر را انتخاب کنید:",
        "sources_manager_title" to "📡 *منابع و سورس‌های فیلم و سریال*\n\nمنبع فعال: *%s*\n• روی 📡 برای انتخاب به عنوان منبع فعال بزنید\n• روی ✅ / ❌ برای فعال یا غیرفعال‌سازی بزنید",
        "source_selected" to "✅ منبع فعال روی %s تنظیم شد",
        "btn_live_search" to "⚡ جستجوی زنده سورس‌ها",
        "btn_search_source" to "🔍 جستجو بر اساس نام",
        "btn_clear_filter" to "❌ حذف فیلتر",
        "btn_quick_picks" to "⭐ منتخب‌های محبوب",
        "source_search_prompt" to "🔍 *جستجوی سورس بر اساس نام*\n\nبخشی از نام سورس مورد نظر را بفرستید (مثال: `Kiss`, `Ava`, `Flix`, `فیلم`, `انیمه`):\n\n_یا در هر زمان دستور `/source <نام>` را برای تغییر فوری بفرستید!_",
        "sources_search_results" to "🔍 *سورس‌های پیدا شده برای:* «`%s`» (%d سورس یافت شد)\n\nمنبع فعال: *%s*\n• روی 📡 برای انتخاب به عنوان منبع فعال بزنید\n• روی ✅ / ❌ برای فعال یا غیرفعال‌سازی بزنید",
        "no_sources_found" to "❌ هیچ سورسی با عبارت «`%s`» یافت نشد.\nعبارت کوتاه‌تری را امتحان کنید یا همه سورس‌ها را با /sources مرور کنید.",
        "source_switched_ready" to "✅ *منبع فعال روی %s [%s] تنظیم شد!*\n\n💡 اکنون می‌توانید نام هر فیلم یا سریالی را جهت جستجو ارسال کنید.",
        "btn_admin_panel" to "⚙️ پنل مدیریت",
        "btn_toggle_nsfw" to "🔞 تغییر وضعیت NSFW",
        "filter_all" to "🌐 همه",
        "filter_fa" to "🇮🇷 فارسی",
        "filter_ar" to "🇸🇦 العربية",
        "filter_anime" to "🌏 انیمه و آسیا",
        "source_changed" to "✅ منبع فعال روی *%s* تنظیم شد!\nاز این پس تمام جستجوهای شما در این منبع انجام می‌شود.",
        "btn_manage_sources" to "⚙️ مدیریت سورس‌ها (فعال/غیرفعال)",
        "btn_enabled_sources" to "📋 سورس‌های فعال من",
        "btn_enable_all" to "✅ فعال‌سازی همه",
        "btn_disable_all" to "❌ غیرفعال‌سازی همه",
        "all_languages" to "🌐 همه زبان‌ها",
        "step_choose_provider" to "⚙️ *مدیریت سورس‌ها — مرحله ۱: انتخاب منبع یا مخزن*\n\nیکی از ارائه‌دهنده‌ها یا مخازن زیر را جهت مدیریت سورس‌ها انتخاب کنید:",
        "step_choose_lang" to "⚙️ *مدیریت سورس‌ها — مرحله ۲: انتخاب زبان*\nمخزن / ارائه‌دهنده: *%s*\n\nیک زبان را جهت مشاهده و فیلتر سورس‌ها انتخاب کنید:",
        "manage_sources_list" to "⚙️ *مدیریت سورس‌ها: %s* (زبان: *%s*)\nبرای فعال (✅) یا غیرفعال (❌) کردن، روی هر سورس بزنید:",
        "enabled_sources_title" to "📋 *سورس‌های فعال شما (%d سورس فعال)*\nبرای انتخاب به عنوان منبع فعال جستجو، روی سورس مورد نظر بزنید:",
        "source_toggled_on" to "✅ سورس فعال شد: %s",
        "source_toggled_off" to "❌ سورس غیرفعال شد: %s",
        "bulk_enabled" to "✅ تعداد %d سورس فعال شدند!",
        "bulk_disabled" to "❌ تعداد %d سورس غیرفعال شدند!",
        "no_enabled_sources" to "⚠️ *هیچ سورسی فعال نیست!* لطفاً از بخش سورس‌ها حداقل یک سورس را فعال کنید.",
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
