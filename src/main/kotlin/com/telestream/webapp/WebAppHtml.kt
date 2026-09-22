package com.telestream.webapp

object WebAppHtml {
    fun renderHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
  <title>TeleStream Mini App</title>
  
  <!-- Telegram WebApp SDK -->
  <script src="https://telegram.org/js/telegram-web-app.js"></script>
  <!-- HLS.js for direct .m3u8 streaming -->
  <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js"></script>

  <style>
    :root {
      --bg: var(--tg-theme-bg-color, #0d1117);
      --card-bg: var(--tg-theme-secondary-bg-color, #161b22);
      --text: var(--tg-theme-text-color, #f0f6fc);
      --hint: var(--tg-theme-hint-color, #8b949e);
      --btn: var(--tg-theme-button-color, #238636);
      --btn-text: var(--tg-theme-button-text-color, #ffffff);
      --accent: #58a6ff;
      --accent-glow: rgba(88, 166, 255, 0.25);
      --danger: #f85149;
      --gold: #f1e05a;
      --border: rgba(240, 246, 252, 0.1);
      --radius: 14px;
      --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    }

    [dir="rtl"] {
      direction: rtl;
      text-align: right;
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-tap-highlight-color: transparent;
      user-select: none;
    }

    body {
      background: var(--bg);
      color: var(--text);
      font-family: var(--font);
      min-height: 100vh;
      overflow-x: hidden;
      padding-bottom: 80px;
    }

    /* Glass Header */
    header {
      position: sticky;
      top: 0;
      z-index: 100;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      background: rgba(13, 17, 23, 0.85);
      border-bottom: 1px solid var(--border);
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .top-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 800;
      font-size: 1.25rem;
      background: linear-gradient(135deg, #58a6ff, #bc8cff);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .icon-btn {
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 6px 12px;
      border-radius: 20px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
    }

    .icon-btn:active {
      transform: scale(0.95);
      background: var(--border);
    }

    /* Search Box */
    .search-container {
      position: relative;
      display: flex;
      align-items: center;
    }

    .search-input {
      width: 100%;
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 12px 16px 12px 42px;
      border-radius: 24px;
      font-size: 0.95rem;
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
    }

    [dir="rtl"] .search-input {
      padding: 12px 42px 12px 16px;
    }

    .search-input:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-glow);
    }

    .search-icon {
      position: absolute;
      left: 14px;
      color: var(--hint);
      pointer-events: none;
      font-size: 1.1rem;
    }

    [dir="rtl"] .search-icon {
      left: auto;
      right: 14px;
    }

    /* Category Chips */
    .chips-bar {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding: 4px 16px 8px 16px;
      scrollbar-width: none;
    }
    .chips-bar::-webkit-scrollbar {
      display: none;
    }

    .chip {
      white-space: nowrap;
      padding: 6px 14px;
      border-radius: 20px;
      background: var(--card-bg);
      border: 1px solid var(--border);
      font-size: 0.85rem;
      font-weight: 500;
      color: var(--hint);
      cursor: pointer;
      transition: all 0.2s;
    }

    .chip.active {
      background: var(--accent);
      color: #fff;
      border-color: var(--accent);
      box-shadow: 0 2px 8px var(--accent-glow);
    }

    /* Main Content */
    main {
      padding: 16px;
    }

    .section-title {
      font-size: 1.1rem;
      font-weight: 700;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    /* Media Grid */
    .media-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
      gap: 12px;
    }

    @media (min-width: 480px) {
      .media-grid {
        grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
        gap: 16px;
      }
    }

    .card {
      background: var(--card-bg);
      border-radius: var(--radius);
      overflow: hidden;
      border: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      cursor: pointer;
      position: relative;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .card:active {
      transform: scale(0.97);
    }

    .poster-wrap {
      width: 100%;
      aspect-ratio: 2 / 3;
      background: #1f242c;
      position: relative;
      overflow: hidden;
    }

    .poster-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: opacity 0.3s;
    }

    .card-badge {
      position: absolute;
      top: 6px;
      left: 6px;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(4px);
      padding: 2px 6px;
      border-radius: 6px;
      font-size: 0.65rem;
      font-weight: 700;
      color: #fff;
    }

    [dir="rtl"] .card-badge {
      left: auto;
      right: 6px;
    }

    .source-badge {
      position: absolute;
      bottom: 6px;
      right: 6px;
      background: rgba(88, 166, 255, 0.85);
      padding: 2px 6px;
      border-radius: 6px;
      font-size: 0.6rem;
      font-weight: 700;
      color: #fff;
    }

    [dir="rtl"] .source-badge {
      right: auto;
      left: 6px;
    }

    .card-info {
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .card-title {
      font-size: 0.82rem;
      font-weight: 600;
      line-height: 1.2;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .card-meta {
      font-size: 0.7rem;
      color: var(--hint);
      display: flex;
      justify-content: space-between;
    }

    /* Modal / Drawer */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.8);
      backdrop-filter: blur(8px);
      z-index: 200;
      display: none;
      justify-content: center;
      align-items: flex-end;
    }

    .modal-overlay.open {
      display: flex;
    }

    .modal-sheet {
      width: 100%;
      max-width: 600px;
      max-height: 90vh;
      background: var(--bg);
      border-top-left-radius: 24px;
      border-top-right-radius: 24px;
      border-top: 1px solid var(--border);
      overflow-y: auto;
      padding: 20px 16px 36px 16px;
      animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes slideUp {
      from { transform: translateY(100%); }
      to { transform: translateY(0); }
    }

    .modal-close-bar {
      width: 40px;
      height: 4px;
      background: var(--hint);
      border-radius: 4px;
      margin: 0 auto 16px auto;
      opacity: 0.5;
    }

    .detail-header {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
    }

    .detail-poster {
      width: 100px;
      height: 150px;
      border-radius: 12px;
      object-fit: cover;
      flex-shrink: 0;
      background: #21262d;
    }

    .detail-meta {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex-grow: 1;
    }

    .detail-title {
      font-size: 1.15rem;
      font-weight: 800;
      line-height: 1.3;
    }

    .detail-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 4px;
    }

    .tag {
      background: var(--card-bg);
      border: 1px solid var(--border);
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 600;
      color: var(--accent);
    }

    .detail-plot {
      font-size: 0.85rem;
      line-height: 1.5;
      color: var(--hint);
      margin-bottom: 20px;
    }

    .detail-actions {
      display: flex;
      gap: 10px;
      margin-bottom: 20px;
    }

    .btn-action {
      flex: 1;
      padding: 12px;
      border-radius: 14px;
      border: none;
      font-weight: 700;
      font-size: 0.95rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s;
    }

    .btn-primary {
      background: var(--btn);
      color: var(--btn-text);
    }

    .btn-secondary {
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text);
    }

    /* Video Player */
    .player-container {
      width: 100%;
      background: #000;
      border-radius: 14px;
      overflow: hidden;
      margin-bottom: 16px;
      position: relative;
    }

    video {
      width: 100%;
      aspect-ratio: 16 / 9;
      display: block;
      outline: none;
    }

    .episode-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
      gap: 8px;
      max-height: 200px;
      overflow-y: auto;
      padding: 4px;
      margin-bottom: 20px;
    }

    .ep-btn {
      padding: 10px 4px;
      border-radius: 8px;
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text);
      font-weight: 700;
      font-size: 0.8rem;
      cursor: pointer;
      text-align: center;
    }

    .ep-btn.active {
      background: var(--accent);
      color: #fff;
      border-color: var(--accent);
    }

    .links-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 20px;
    }

    .link-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 14px;
      border-radius: 10px;
      background: var(--card-bg);
      border: 1px solid var(--border);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
    }

    .link-item:hover {
      border-color: var(--accent);
    }

    /* Bottom Navigation Bar */
    .bottom-nav {
      position: fixed;
      bottom: 0;
      left: 0;
      width: 100%;
      height: 64px;
      background: rgba(13, 17, 23, 0.95);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border-top: 1px solid var(--border);
      display: flex;
      justify-content: space-around;
      align-items: center;
      z-index: 90;
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      color: var(--hint);
      font-size: 0.7rem;
      font-weight: 600;
      cursor: pointer;
      padding: 6px 16px;
      border-radius: 12px;
      transition: color 0.2s ease;
    }

    .nav-item.active {
      color: var(--accent);
    }

    .nav-icon {
      font-size: 1.3rem;
    }

    /* Toast Notification */
    .toast {
      position: fixed;
      bottom: 80px;
      left: 50%;
      transform: translateX(-50%) translateY(50px);
      background: rgba(22, 27, 34, 0.95);
      border: 1px solid var(--border);
      color: #fff;
      padding: 10px 20px;
      border-radius: 30px;
      font-size: 0.85rem;
      font-weight: 600;
      opacity: 0;
      pointer-events: none;
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      z-index: 999;
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    }

    .toast.show {
      opacity: 1;
      transform: translateX(-50%) translateY(0);
    }

    .spinner {
      display: inline-block;
      width: 24px;
      height: 24px;
      border: 3px solid rgba(255,255,255,0.2);
      border-radius: 50%;
      border-top-color: var(--accent);
      animation: spin 0.8s linear infinite;
      margin: 40px auto;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
      color: var(--hint);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }
  </style>
</head>
<body>

  <!-- Header -->
  <header>
    <div class="top-bar">
      <div class="brand">
        <span>🎬</span>
        <span>TeleStream</span>
      </div>
      <div class="actions">
        <button class="icon-btn" id="langToggle">🌐 EN</button>
        <button class="icon-btn" id="donateBtn">💎 Donate</button>
      </div>
    </div>
    <div class="search-container">
      <span class="search-icon">🔍</span>
      <input type="text" class="search-input" id="searchInput" placeholder="Search movies, TV shows, anime..." />
    </div>
  </header>

  <!-- Filter Chips -->
  <div class="chips-bar">
    <div class="chip active" data-filter="all">🌟 All</div>
    <div class="chip" data-filter="movie">🎬 Movies</div>
    <div class="chip" data-filter="series">📺 Series</div>
    <div class="chip" data-filter="anime">🌸 Anime</div>
    <div class="chip" data-filter="fa">🇮🇷 فارسی</div>
  </div>

  <!-- Main Content Views -->
  <main id="mainView">
    <div class="section-title" id="sectionTitle">
      <span>🔥</span>
      <span id="titleText">Discover</span>
    </div>
    <div class="media-grid" id="mediaGrid">
      <!-- Media Cards injected by JS -->
    </div>
    <div id="loadingSpinner" style="display: none; text-align: center;">
      <div class="spinner"></div>
    </div>
  </main>

  <!-- Bookmarks View (Hidden by default) -->
  <main id="bookmarksView" style="display: none;">
    <div class="section-title">
      <span>⭐</span>
      <span id="bookmarksTitle">Saved Bookmarks</span>
    </div>
    <div class="media-grid" id="bookmarksGrid"></div>
  </main>

  <!-- Bottom Navigation -->
  <nav class="bottom-nav">
    <div class="nav-item active" id="navHome">
      <span class="nav-icon">🎬</span>
      <span id="navHomeText">Discover</span>
    </div>
    <div class="nav-item" id="navBookmarks">
      <span class="nav-icon">⭐</span>
      <span id="navBookmarksText">Bookmarks</span>
    </div>
  </nav>

  <!-- Details & Player Modal -->
  <div class="modal-overlay" id="detailModal">
    <div class="modal-sheet">
      <div class="modal-close-bar" id="closeBar"></div>
      
      <!-- Video Player Area (shown when playing) -->
      <div class="player-container" id="playerArea" style="display: none;">
        <video id="videoPlayer" controls playsinline></video>
      </div>

      <div class="detail-header">
        <img class="detail-poster" id="detailPoster" src="" alt="Poster" />
        <div class="detail-meta">
          <div class="detail-title" id="detailTitle"></div>
          <div class="detail-tags" id="detailTags"></div>
        </div>
      </div>

      <div class="detail-actions">
        <button class="btn-action btn-primary" id="playBtn">▶️ <span id="playBtnText">Play Stream</span></button>
        <button class="btn-action btn-secondary" id="bookmarkDetailBtn">⭐ <span id="bookmarkBtnText">Bookmark</span></button>
      </div>

      <div class="detail-plot" id="detailPlot"></div>

      <!-- Episodes (Series Only) -->
      <div id="episodesSection" style="display: none;">
        <div class="section-title">📺 <span id="episodesTitleText">Episodes</span></div>
        <div class="episode-grid" id="episodesGrid"></div>
      </div>

      <!-- Streaming Servers -->
      <div id="streamsSection" style="display: none;">
        <div class="section-title">📡 <span id="streamsTitleText">Available Streams</span></div>
        <div class="links-list" id="linksList"></div>
      </div>
    </div>
  </div>

  <!-- Donation Modal -->
  <div class="modal-overlay" id="donationModal">
    <div class="modal-sheet">
      <div class="modal-close-bar" id="closeDonateBar"></div>
      <div class="section-title">💎 <span id="donateModalTitle">Support TeleStream</span></div>
      <p style="font-size: 0.85rem; color: var(--hint); margin-bottom: 16px;" id="donateSubText">
        Tap on any address below to copy it to your clipboard:
      </p>
      <div class="links-list" id="donationWallets">
        <!-- Injected via JS -->
      </div>
    </div>
  </div>

  <!-- Toast Notification -->
  <div class="toast" id="toast">Copied to clipboard!</div>

  <script>
    // Initialize Telegram WebApp SDK
    const tg = window.Telegram ? window.Telegram.WebApp : null;
    if (tg) {
      tg.ready();
      tg.expand();
      if (tg.setHeaderColor) tg.setHeaderColor('#0d1117');
    }

    function haptic(type = 'light') {
      if (tg && tg.HapticFeedback) {
        tg.HapticFeedback.impactOccurred(type);
      }
    }

    // State
    let currentLang = localStorage.getItem('telestream_lang') || 'en';
    let currentFilter = 'all';
    let activeMedia = null;
    let currentEpisodeData = null;
    let hlsInstance = null;
    let configData = {};
    let searchDebounceTimer = null;

    // Translations Dictionary
    const i18n = {
      en: {
        searchPlaceholder: "Search movies, TV shows, anime...",
        discover: "Discover",
        savedBookmarks: "Saved Bookmarks",
        play: "Play Stream",
        bookmark: "Bookmark",
        bookmarked: "Bookmarked",
        episodes: "Episodes",
        streams: "Available Streams",
        donateTitle: "Support TeleStream",
        donateDesc: "Tap on any address below to copy it to your clipboard:",
        copied: "Address copied to clipboard! ✅",
        noResults: "No titles found. Try another search!",
        noBookmarks: "No saved bookmarks yet.",
        extracting: "Resolving stream links...",
        navDiscover: "Discover",
        navBookmarks: "Bookmarks"
      },
      fa: {
        searchPlaceholder: "جستجوی فیلم، سریال، انیمه...",
        discover: "کاوش و پیشنهادها",
        savedBookmarks: "فیلم‌های نشان‌شده",
        play: "پخش آنلاین",
        bookmark: "نشان کردن",
        bookmarked: "نشان شده",
        episodes: "قسمت‌ها",
        streams: "سرورهای پخش و دانلود",
        donateTitle: "حمایت مالی از پروژه تله‌استریم",
        donateDesc: "برای کپی کردن هر آدرس، کافیست روی آن ضربه بزنید:",
        copied: "آدرس با موفقیت کپی شد! ✅",
        noResults: "نتیجه‌ای یافت نشد. عنوان دیگری را جستجو کنید!",
        noBookmarks: "هنوز فیلمی ذخیره نکرده‌اید.",
        extracting: "در حال دریافت لینک‌های پخش...",
        navDiscover: "کاوش",
        navBookmarks: "نشان‌شده‌ها"
      }
    };

    function applyLanguage(lang) {
      currentLang = lang;
      localStorage.setItem('telestream_lang', lang);
      const isRtl = lang === 'fa';
      document.documentElement.dir = isRtl ? 'rtl' : 'ltr';
      document.getElementById('langToggle').textContent = isRtl ? '🌐 فارسی' : '🌐 EN';

      const t = i18n[lang];
      document.getElementById('searchInput').placeholder = t.searchPlaceholder;
      document.getElementById('titleText').textContent = t.discover;
      document.getElementById('bookmarksTitle').textContent = t.savedBookmarks;
      document.getElementById('playBtnText').textContent = t.play;
      document.getElementById('episodesTitleText').textContent = t.episodes;
      document.getElementById('streamsTitleText').textContent = t.streams;
      document.getElementById('donateModalTitle').textContent = t.donateTitle;
      document.getElementById('donateSubText').textContent = t.donateDesc;
      document.getElementById('navHomeText').textContent = t.navDiscover;
      document.getElementById('navBookmarksText').textContent = t.navBookmarks;
    }

    function showToast(msg) {
      const toast = document.getElementById('toast');
      toast.textContent = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2400);
    }

    // Fetch Config (Donations, etc.)
    async function loadConfig() {
      try {
        const res = await fetch('/api/config');
        configData = await res.json();
        renderDonations();
      } catch (e) {
        console.error("Config fetch failed", e);
      }
    }

    function renderDonations() {
      const list = document.getElementById('donationWallets');
      list.innerHTML = '';
      const wallets = [
        { label: "USDT (TRC20)", address: configData.usdtTrc20 || "TQn9Y2khEsLJW1ChVWFMSMeSTow5KaxnSE", icon: "💵" },
        { label: "TON (Telegram / TON)", address: configData.tonWallet || "UQDP14pSjV1k8L0Fj8d2p7k8XqZ7YjB7p9", icon: "⚡" },
        { label: "Bitcoin (BTC)", address: configData.btcWallet || "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", icon: "🪙" },
        { label: "Ethereum (ERC20)", address: configData.ethWallet || "0x71C...YourEthAddressHere", icon: "🔷" }
      ];

      wallets.forEach(w => {
        const div = document.createElement('div');
        div.className = 'link-item';
        div.innerHTML = '<div><div style="font-size:0.9rem;">' + w.icon + ' ' + w.label + '</div><div style="font-size:0.75rem; color:var(--hint); word-break:break-all;">' + w.address + '</div></div><div>📋</div>';
        div.onclick = () => {
          haptic('medium');
          navigator.clipboard.writeText(w.address);
          showToast(i18n[currentLang].copied);
        };
        list.appendChild(div);
      });
    }

    // Media Search & Render
    async function searchMedia(query) {
      const grid = document.getElementById('mediaGrid');
      const spinner = document.getElementById('loadingSpinner');
      spinner.style.display = 'block';

      try {
        const res = await fetch('/api/search?q=' + encodeURIComponent(query));
        const items = await res.json();
        spinner.style.display = 'none';
        renderCards(items, grid);
      } catch (e) {
        spinner.style.display = 'none';
        grid.innerHTML = '<div class="empty-state">❌ Failed to fetch results</div>';
      }
    }

    function renderCards(items, container) {
      container.innerHTML = '';
      if (!items || items.length === 0) {
        container.innerHTML = '<div class="empty-state"><span>🎬</span><span>' + i18n[currentLang].noResults + '</span></div>';
        return;
      }

      items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'card';
        const poster = item.posterUrl || 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300';
        const typeBadge = item.type || 'Media';
        const year = item.year ? item.year : '';

        card.innerHTML = 
          '<div class="poster-wrap">' +
            '<img class="poster-img" src="' + poster + '" loading="lazy" onerror="this.src=\'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300\'" />' +
            '<span class="card-badge">' + typeBadge + '</span>' +
            '<span class="source-badge">' + item.apiName + '</span>' +
          '</div>' +
          '<div class="card-info">' +
            '<div class="card-title">' + item.name + '</div>' +
            '<div class="card-meta">' +
              '<span>' + year + '</span>' +
              '<span>⭐ 8.2</span>' +
            '</div>' +
          '</div>';

        card.onclick = () => {
          haptic('light');
          openDetails(item);
        };

        container.appendChild(card);
      });
    }

    // Detail Drawer & Player
    async function openDetails(item) {
      activeMedia = item;
      const modal = document.getElementById('detailModal');
      const poster = document.getElementById('detailPoster');
      const title = document.getElementById('detailTitle');
      const tags = document.getElementById('detailTags');
      const plot = document.getElementById('detailPlot');
      const epSec = document.getElementById('episodesSection');
      const streamSec = document.getElementById('streamsSection');
      const playerArea = document.getElementById('playerArea');
      const video = document.getElementById('videoPlayer');

      // Stop previous video if any
      video.pause();
      if (hlsInstance) {
        hlsInstance.destroy();
        hlsInstance = null;
      }
      playerArea.style.display = 'none';
      streamSec.style.display = 'none';
      epSec.style.display = 'none';

      poster.src = item.posterUrl || '';
      title.textContent = item.name;
      tags.innerHTML = '<span class="tag">' + item.apiName + '</span><span class="tag">' + item.type + '</span>' + (item.year ? '<span class="tag">' + item.year + '</span>' : '');
      plot.textContent = "Loading full details...";

      updateBookmarkButtonState(item);
      modal.classList.add('open');

      // Fetch Full Load Response
      try {
        const res = await fetch('/api/load?provider=' + encodeURIComponent(item.apiName) + '&url=' + encodeURIComponent(item.url));
        const details = await res.json();
        plot.textContent = details.plot || "No synopsis available.";
        activeMedia.details = details;

        if (details.episodes && details.episodes.length > 0) {
          renderEpisodes(details.episodes);
          epSec.style.display = 'block';
        }
      } catch (e) {
        plot.textContent = "Unable to load details.";
      }
    }

    function renderEpisodes(episodes) {
      const grid = document.getElementById('episodesGrid');
      grid.innerHTML = '';
      episodes.forEach((ep, idx) => {
        const btn = document.createElement('button');
        btn.className = 'ep-btn' + (idx === 0 ? ' active' : '');
        btn.textContent = 'E' + (ep.episode || (idx + 1));
        btn.onclick = () => {
          haptic('light');
          grid.querySelectorAll('.ep-btn').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          currentEpisodeData = ep.data;
          fetchAndPlayStreams(activeMedia.apiName, ep.data);
        };
        grid.appendChild(btn);
      });
      // Default episode
      currentEpisodeData = episodes[0].data;
    }

    async function fetchAndPlayStreams(provider, dataUrl) {
      showToast(i18n[currentLang].extracting);
      const streamSec = document.getElementById('streamsSection');
      const list = document.getElementById('linksList');
      list.innerHTML = '';

      try {
        const res = await fetch('/api/links?provider=' + encodeURIComponent(provider) + '&data=' + encodeURIComponent(dataUrl));
        const links = await res.json();

        if (!links || links.length === 0) {
          list.innerHTML = '<div class="empty-state">⚠️ No direct links found for this item.</div>';
          streamSec.style.display = 'block';
          return;
        }

        streamSec.style.display = 'block';
        links.forEach((link, idx) => {
          const item = document.createElement('div');
          item.className = 'link-item';
          const icon = link.isM3u8 ? '📡' : '📥';
          item.innerHTML = 
            '<div>' + icon + ' ' + (link.name || 'Server') + ' [' + link.quality + 'p]</div>' +
            '<div style="font-size:0.75rem; color:var(--accent);">▶ Play</div>';
          
          item.onclick = () => {
            haptic('medium');
            playStream(link);
          };
          list.appendChild(item);
        });

        // Automatically play the first stream
        playStream(links[0]);
      } catch (e) {
        showToast("Failed to resolve links");
      }
    }

    function playStream(link) {
      const playerArea = document.getElementById('playerArea');
      const video = document.getElementById('videoPlayer');
      playerArea.style.display = 'block';
      playerArea.scrollIntoView({ behavior: 'smooth' });

      if (hlsInstance) {
        hlsInstance.destroy();
        hlsInstance = null;
      }

      if (link.isM3u8 && Hls.isSupported()) {
        hlsInstance = new Hls({ enableWorker: true });
        hlsInstance.loadSource(link.url);
        hlsInstance.attachMedia(video);
        hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
          video.play().catch(() => {});
        });
      } else {
        video.src = link.url;
        video.play().catch(() => {});
      }
    }

    // Bookmarks Management
    function getBookmarks() {
      try {
        return JSON.parse(localStorage.getItem('telestream_bookmarks') || '[]');
      } catch (e) {
        return [];
      }
    }

    function isBookmarked(url) {
      return getBookmarks().some(b => b.url === url);
    }

    function toggleBookmark(media) {
      haptic('medium');
      let bookmarks = getBookmarks();
      const exists = isBookmarked(media.url);
      if (exists) {
        bookmarks = bookmarks.filter(b => b.url !== media.url);
        showToast("Removed from bookmarks");
      } else {
        bookmarks.unshift(media);
        showToast("Saved to bookmarks! ⭐");
      }
      localStorage.setItem('telestream_bookmarks', JSON.stringify(bookmarks));
      updateBookmarkButtonState(media);
      renderBookmarksView();
    }

    function updateBookmarkButtonState(media) {
      const btn = document.getElementById('bookmarkDetailBtn');
      const isSaved = isBookmarked(media.url);
      btn.innerHTML = (isSaved ? '❌ ' : '⭐ ') + '<span id="bookmarkBtnText">' + (isSaved ? i18n[currentLang].bookmarked : i18n[currentLang].bookmark) + '</span>';
    }

    function renderBookmarksView() {
      const grid = document.getElementById('bookmarksGrid');
      const bookmarks = getBookmarks();
      if (bookmarks.length === 0) {
        grid.innerHTML = '<div class="empty-state"><span>⭐</span><span>' + i18n[currentLang].noBookmarks + '</span></div>';
        return;
      }
      renderCards(bookmarks, grid);
    }

    // Event Listeners
    document.getElementById('langToggle').onclick = () => {
      haptic('light');
      applyLanguage(currentLang === 'en' ? 'fa' : 'en');
    };

    document.getElementById('donateBtn').onclick = () => {
      haptic('light');
      document.getElementById('donationModal').classList.add('open');
    };

    document.getElementById('closeDonateBar').onclick = () => {
      document.getElementById('donationModal').classList.remove('open');
    };

    document.getElementById('closeBar').onclick = () => {
      const video = document.getElementById('videoPlayer');
      video.pause();
      document.getElementById('detailModal').classList.remove('open');
    };

    document.getElementById('playBtn').onclick = () => {
      haptic('medium');
      if (activeMedia) {
        const dataUrl = currentEpisodeData || activeMedia.url;
        fetchAndPlayStreams(activeMedia.apiName, dataUrl);
      }
    };

    document.getElementById('bookmarkDetailBtn').onclick = () => {
      if (activeMedia) toggleBookmark(activeMedia);
    };

    // Filter Chips
    document.querySelectorAll('.chip').forEach(chip => {
      chip.onclick = () => {
        haptic('light');
        document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        const filter = chip.dataset.filter;
        currentFilter = filter;
        if (filter === 'fa') {
          searchMedia('پوست شیر');
        } else if (filter === 'anime') {
          searchMedia('Solo Leveling');
        } else if (filter === 'series') {
          searchMedia('House of the Dragon');
        } else if (filter === 'movie') {
          searchMedia('Dune');
        } else {
          searchMedia('Spider');
        }
      };
    });

    // Search input debouncer
    document.getElementById('searchInput').addEventListener('input', (e) => {
      const val = e.target.value.trim();
      clearTimeout(searchDebounceTimer);
      if (val.length >= 2) {
        searchDebounceTimer = setTimeout(() => {
          searchMedia(val);
        }, 350);
      }
    });

    // Bottom Navigation
    document.getElementById('navHome').onclick = () => {
      haptic('light');
      document.getElementById('navHome').classList.add('active');
      document.getElementById('navBookmarks').classList.remove('active');
      document.getElementById('mainView').style.display = 'block';
      document.getElementById('bookmarksView').style.display = 'none';
    };

    document.getElementById('navBookmarks').onclick = () => {
      haptic('light');
      document.getElementById('navBookmarks').classList.add('active');
      document.getElementById('navHome').classList.remove('active');
      document.getElementById('mainView').style.display = 'none';
      document.getElementById('bookmarksView').style.display = 'block';
      renderBookmarksView();
    };

    // Initial Load
    applyLanguage(currentLang);
    loadConfig();
    searchMedia('Batman');
  </script>
</body>
</html>
        """.trimIndent()
    }
}
