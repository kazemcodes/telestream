# 🎬 TeleStream: Native CloudStream Telegram Bot (Pure Kotlin JVM)

**TeleStream** is a lightweight, high-performance Telegram movie & series streaming bot written in **100% Kotlin (Java 21 LTS)** using **Ktor & Coroutines**. 

It runs CloudStream provider logic **natively on the JVM without any Android dependencies or Python bridges**, allowing you to discover, search, and stream content across multiple languages, including **English** and **Persian (فارسی)**.

---

## ✨ Features

- **📱 Telegram Mini App (WebUI)**:
  - Mobile-first, cinematic web app interface served directly by the embedded Ktor server (`GET /` and `GET /webapp`).
  - **Telegram WebApp SDK Integration**: Automatically hooks into native theme colors (`--tg-theme-bg-color`, `--tg-theme-text-color`), haptic feedback, and viewport expansion.
  - **Integrated Video Streaming Player**: Built-in player powered by `Hls.js` for `.m3u8` streams and native HTML5 video for `.mp4`, with full-screen and quality controls.
  - **Instant Search & Real-time Filters**: Quick filtering by Movies, TV Series, Anime, and Persian content.
  - **Bot Menu Button Integration**: Persistent `[ 🎬 TeleStream ]` Web App launch button placed directly next to the Telegram chat input box via `setChatMenuButton`.
- **☕ 100% Pure Kotlin / JVM**: Built with modern Kotlin 2.0+ and Ktor on Java 21 LTS. No Android SDK or Dalvik VM required.
- **🔄 Native CloudStream Compatibility**:
  - Pure-JVM SDK Shim (`com.lagradost.cloudstream3`) matching `MainAPI`, `app.get()`, `Jsoup`, and extractor links.
  - Bundled high-value providers:
    - **KissKH** (`com.telestream.providers.KissKH`): English movies, TV series, Asian dramas, and anime with direct HLS/MP4 streams.
    - **AvaMovie** (`com.telestream.providers.AvaMovie`): Persian dubbed and soft-subbed movies & series with direct stream links.
    - **FaselHD** (`com.telestream.providers.FaselHD`): Arabic and international multi-server media.
- **🌐 Dynamic Repository Fetching ([cloudstreamrepo.com](https://cloudstreamrepo.com/))**:
  - Dynamically fetches, indexes, and caches extension manifests from `cloudstreamrepo.com` (Cs-Karma, re-3arabi, Hexated, Stormunblessed) on-demand.
  - Commands: `/repos`, `/sync`, `/addrepo <url>`.
  - Repositories are kept out of git history, drastically keeping the codebase clean and small.
- **🛡️ Admin Access Control & Dashboard**:
  - Restricts sensitive maintenance commands (`/admin`, `/sync`, `/addrepo`, `/nsfw`) to configured administrator IDs (`ADMIN_IDS`).
  - `/admin` interactive dashboard displays live user count, total bookmarks, synced repositories, total extensions, JVM heap memory, and adult content status.
- **🔞 NSFW Content Restrictions**:
  - Admin-only switch (`/nsfw on|off` or via the `/admin` interactive panel).
  - When disabled (default), all adult providers and NSFW-tagged media are hidden and filtered out from search queries.
- **💎 Crypto Donations**:
  - `/donate` command formats instant-copy crypto wallet addresses for USDT (TRC20), TON, BTC, and ETH.
- **🚀 Multi-User High-Concurrency Architecture**:
  - **SQLite WAL Mode**: Configured with `PRAGMA journal_mode = WAL;`, `synchronous = NORMAL;`, and `busy_timeout = 5000;` allowing non-blocking concurrent reads and fast writes.
  - **Per-User Debouncing**: In-flight action throttling prevents spam clicks from overloading scrapers.
  - **Connection Pooling**: Optimized OkHttp client pool (`maxRequests=128`, `maxRequestsPerHost=32`, `ConnectionPool(64, 5min)`).
  - **Coroutine Concurrency**: Non-blocking asynchronous update dispatching on `Dispatchers.IO`.
- **🇮🇷 Multi-Lingual Support (English & Persian)**:
  - First-time language selection on `/start`.
  - Full natural Persian translation with RTL support.
  - Smart search routing (queries in Persian are routed to Persian sources; Latin/English queries to global sources).
- **⚡ Ultra-Lightweight & Fast**: Consumes only ~60MB RAM on standard JVM.
- **🏥 Embedded Health Server**: Ktor embedded server on port 7860/8080 providing `GET /health` for cloud container health checks.
- **☁️ 100% Free 24/7 Hosting**: Ready for 1-click deployment on **Hugging Face Spaces** (free 2 vCPU · 16 GB RAM, never sleeps) or standard VPS / Local PC.

---

## 📁 Project Structure

```text
telestream/
├── src/
│   ├── main/kotlin/
│   │   ├── com/lagradost/cloudstream3/ # Pure JVM CloudStream SDK Shim
│   │   │   └── MainAPI.kt              # MainAPI, app.get(), Jsoup helpers, models
│   │   └── com/telestream/
│   │       ├── bot/                    # Bot Runner & Event Handler
│   │       │   └── BotRunner.kt        # Concurrency loop, commands, search & callbacks
│   │       ├── config/                 # Environment & Security Configuration
│   │       │   └── Config.kt           # Admin IDs, wallet addresses, ports
│   │       ├── database/               # High-Concurrency SQLite Persistence
│   │       │   └── Database.kt         # WAL mode, users, bookmarks, NSFW settings
│   │       ├── i18n/                   # Multi-lingual Engine
│   │       │   └── I18n.kt             # English & Persian (فارسی) translations
│   │       ├── providers/              # Native Kotlin Scrapers
│   │       │   ├── KissKH.kt           # English & Anime provider
│   │       │   ├── AvaMovie.kt         # Persian provider (dubbed/subtitled)
│   │       │   ├── FaselHD.kt          # Arabic & Regional provider
│   │       │   └── ProviderManager.kt  # Smart routing, concurrency & NSFW filter
│   │       ├── repo/                   # On-Demand Repository Manager
│   │       │   └── CloudStreamRepoManager.kt # cloudstreamrepo.com fetcher & parser
│   │       ├── telegram/               # Telegram Bot API Client
│   │       │   ├── TelegramClient.kt   # Async Ktor CIO client
│   │       │   └── TelegramModels.kt   # Telegram JSON models
│   │       └── Main.kt                 # Application Entrypoint & Ktor server
│   └── test/kotlin/com/telestream/     # JUnit 5 Unit Tests
│       ├── ProviderTest.kt             # Provider metadata, routing & NSFW tests
│       ├── DatabaseTest.kt             # SQLite WAL & settings tests
│       ├── I18nTest.kt                 # Translation & donation tests
│       └── RepoManagerTest.kt          # Repository manager tests
├── Dockerfile                          # Multi-stage Alpine container
├── build.gradle.kts                    # Kotlin Gradle build script (fatJar)
├── settings.gradle.kts                 # Project settings
└── gradle.properties                   # JVM memory options
```

---

## 🚀 Quick Start (Local PC / VPS)

### Prerequisites
- **Java 21 LTS** or newer (`java -version`).
- Telegram Bot Token from [@BotFather](https://t.me/BotFather).

### 1. Set Environment Variables
Create `.env` or set in your environment:
```bash
# On Linux/macOS:
export BOT_TOKEN="your_bot_token_here"
export ADMIN_IDS="12345678,87654321"   # Optional: Restrict admin commands
export DONATION_USDT_TRC20="YourUSDTAddress"
export DONATION_TON="YourTONAddress"
export PORT="7860"

# On Windows PowerShell:
$env:BOT_TOKEN="your_bot_token_here"
$env:ADMIN_IDS="12345678,87654321"
$env:PORT="7860"
```

### 2. Run with Gradle
```bash
# On Linux/macOS:
./gradlew run

# On Windows:
.\gradlew.bat run
```

### 3. Build Self-Contained Fat JAR
```bash
# Generate single standalone executable JAR (~30MB)
./gradlew fatJar

# Run anywhere without Gradle or Docker:
java -jar build/libs/telestream-all.jar
```

---

## 🤖 Bot Commands & Controls

### BotFather Setup Format (Copy & Paste to @BotFather `/setcommands`)

**English / Default:**
```text
start - Main menu & dashboard
sources - Choose active source provider
enabled_sources - View all enabled sources
manage_sources - Manage & toggle sources
search - Search in active source
bookmarks - View saved bookmarks
language - Change language
ping - Check bot status
donate - Support & donate
app - Launch TeleStream Mini App
```

**Persian / فارسی (Optional for `fa` language in BotFather):**
```text
start - منوی اصلی و داشبورد
sources - انتخاب منبع فیلم و سریال
enabled_sources - سورس‌های فعال من
manage_sources - مدیریت و فعال‌سازی سورس‌ها
search - جستجو در منبع فعال
bookmarks - فیلم‌ها و سریال‌های نشان‌شده
language - تغییر زبان / Change Language
ping - وضعیت آنلاین ربات
donate - حمایت مالی از ربات
app - اجرای مینی‌اپ تله‌استریم
```

---

### Command Overview

| Command | Access | Description |
| :--- | :--- | :--- |
| `/start` | Public | 🎬 Main menu, dashboard, and quick navigation |
| `/sources` | Public | 📡 Select active source provider for search |
| `/enabled_sources` | Public | 📋 View your currently enabled sources |
| `/manage_sources` | Public | ⚙️ 3-Step manager to enable/disable sources (Provider &rarr; Lang &rarr; Toggle) |
| `/search [title]` | Public | 🔍 Search movies & series in your active source |
| `/bookmarks` | Public | ⭐ View and manage saved movies/shows |
| `/language` | Public | 🌐 Switch language (English / فارسی) |
| `/ping` | Public | 🏓 Check bot latency and online status |
| `/donate` | Public | 💎 Show crypto donation addresses (USDT, TON, BTC, ETH) |
| `/app` | Public | 🚀 Launch TeleStream Mini App (WebUI) |
| `/repos` | Public | 📦 View synced CloudStream repositories & plugins |
| `/admin` | Admin | 📊 Interactive dashboard (stats, memory, NSFW toggle) |
| `/nsfw <on\|off>` | Admin | 🔞 Enable or restrict adult content across scrapers |
| `/sync` | Admin | 🔄 Re-fetch extension lists from `cloudstreamrepo.com` |
| `/addrepo <url>` | Admin | ➕ Fetch and index a custom repository URL |

---

## ☁️ 100% Free 24/7 Hosting on Hugging Face Spaces

Hugging Face Spaces provides **2 vCPU and 16 GB RAM** completely free, and instances **never sleep**:

1. Create a free account at [huggingface.co](https://huggingface.co).
2. Click **Spaces** > **Create new Space**.
3. Select **Docker** (Blank) and choose **Free** (16 GB RAM).
4. Go to **Settings** > **Variables and secrets**, add your secrets:
   - `BOT_TOKEN`: `your_telegram_bot_token`
   - `ADMIN_IDS`: `your_telegram_numeric_id` (find via [@userinfobot](https://t.me/userinfobot))
   - `DONATION_USDT_TRC20`: your TRC20 wallet address (optional)
   - `DONATION_TON`: your TON wallet address (optional)
5. Push this repository to your Space:
   ```bash
   git remote add space https://huggingface.co/spaces/YOUR_USERNAME/YOUR_SPACE_NAME
   git push space main
   ```
6. Hugging Face will automatically build the `Dockerfile` and your bot will be **live 24/7**!

---

## 🧪 Running Automated Tests

Run the test suite:
```bash
./gradlew test
```
All unit tests verify:
- CloudStream JVM provider registration & Persian/English language detection.
- NSFW content restriction toggle & provider filtering.
- SQLite WAL mode persistence (user language preferences, bookmarks, stats).
- Multi-lingual English & Persian translations and crypto donation message formatting.
- Repository manifest loading and parser reliability.
