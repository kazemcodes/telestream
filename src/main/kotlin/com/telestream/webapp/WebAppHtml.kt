package com.telestream.webapp

object WebAppHtml {
    fun renderHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
  <title>TeleStream — Watch Movies & Series</title>
  
  <!-- Telegram WebApp SDK -->
  <script src="https://telegram.org/js/telegram-web-app.js"></script>
  <!-- HLS.js for direct .m3u8 adaptive streaming -->
  <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js"></script>

  <style>
    :root {
      --bg: #07090e;
      --surface: #0f131d;
      --card-bg: #151b28;
      --card-hover: #1c2436;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --hint: #64748b;
      --accent: #6366f1;
      --accent-gradient: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
      --accent-glow: rgba(99, 102, 241, 0.35);
      --badge-bg: rgba(99, 102, 241, 0.15);
      --badge-text: #818cf8;
      --gold: #fbbf24;
      --gold-bg: rgba(251, 191, 36, 0.12);
      --danger: #ef4444;
      --border: rgba(255, 255, 255, 0.08);
      --border-bright: rgba(255, 255, 255, 0.15);
      --radius: 16px;
      --radius-sm: 10px;
      --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", "Vazirmatn", sans-serif;
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
      padding-bottom: 96px;
      -webkit-font-smoothing: antialiased;
    }

    /* Scrollbar */
    ::-webkit-scrollbar {
      display: none;
    }
    * {
      scrollbar-width: none;
    }

    /* Frosted Sticky Header */
    header {
      position: sticky;
      top: 0;
      z-index: 100;
      backdrop-filter: blur(24px) saturate(180%);
      -webkit-backdrop-filter: blur(24px) saturate(180%);
      background: rgba(7, 9, 14, 0.82);
      border-bottom: 1px solid var(--border);
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      transition: all 0.3s ease;
    }

    .top-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .brand-wrap {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .brand-logo {
      font-size: 1.35rem;
      line-height: 1;
      filter: drop-shadow(0 2px 8px var(--accent-glow));
    }

    .brand-title {
      font-weight: 800;
      font-size: 1.15rem;
      letter-spacing: -0.4px;
      background: var(--accent-gradient);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    /* Active Source Chip */
    .source-chip {
      background: rgba(99, 102, 241, 0.12);
      border: 1px solid rgba(99, 102, 241, 0.3);
      color: #c7d2fe;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .source-chip:active {
      transform: scale(0.95);
      background: rgba(99, 102, 241, 0.25);
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .pill-btn {
      background: var(--surface);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 6px 12px;
      border-radius: 20px;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 5px;
      transition: all 0.2s;
    }
    .pill-btn:active {
      transform: scale(0.95);
      border-color: var(--border-bright);
    }

    /* Search Bar */
    .search-container {
      position: relative;
      display: flex;
      align-items: center;
    }

    .search-input {
      width: 100%;
      background: var(--surface);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 11px 16px 11px 42px;
      border-radius: 24px;
      font-size: 0.92rem;
      outline: none;
      transition: all 0.25s ease;
    }
    [dir="rtl"] .search-input {
      padding: 11px 42px 11px 16px;
    }
    .search-input:focus {
      border-color: var(--accent);
      background: var(--card-bg);
      box-shadow: 0 0 0 3px var(--accent-glow);
    }
    .search-input::placeholder {
      color: var(--hint);
    }

    .search-icon {
      position: absolute;
      left: 14px;
      color: var(--hint);
      pointer-events: none;
      font-size: 1rem;
    }
    [dir="rtl"] .search-icon {
      left: auto;
      right: 14px;
    }

    /* Hero Billboard Section */
    .hero-billboard {
      position: relative;
      width: 100%;
      min-height: 260px;
      border-radius: var(--radius);
      margin: 12px 0 20px 0;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
      padding: 24px 20px;
      background-size: cover;
      background-position: center top;
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
    }

    .hero-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(0deg, var(--bg) 0%, rgba(7, 9, 14, 0.7) 45%, rgba(7, 9, 14, 0.2) 100%);
      pointer-events: none;
    }

    .hero-content {
      position: relative;
      z-index: 2;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .hero-badges {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .badge-gold {
      background: var(--gold-bg);
      color: var(--gold);
      border: 1px solid rgba(251, 191, 36, 0.3);
      padding: 2px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
    }

    .badge-sub {
      background: var(--badge-bg);
      color: var(--badge-text);
      padding: 2px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
    }

    .hero-title {
      font-size: 1.5rem;
      font-weight: 900;
      letter-spacing: -0.5px;
      line-height: 1.2;
      text-shadow: 0 2px 10px rgba(0, 0, 0, 0.8);
    }

    .hero-synopsis {
      font-size: 0.82rem;
      color: var(--text-muted);
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-shadow: 0 1px 4px rgba(0, 0, 0, 0.7);
    }

    .hero-actions {
      display: flex;
      gap: 10px;
      margin-top: 6px;
    }

    .btn-hero-play {
      background: var(--accent-gradient);
      color: #fff;
      border: none;
      padding: 10px 20px;
      border-radius: 24px;
      font-size: 0.88rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      box-shadow: 0 4px 16px var(--accent-glow);
      transition: all 0.2s;
    }
    .btn-hero-play:active {
      transform: scale(0.96);
    }

    .btn-hero-list {
      background: rgba(255, 255, 255, 0.12);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid var(--border-bright);
      color: var(--text);
      padding: 10px 16px;
      border-radius: 24px;
      font-size: 0.88rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s;
    }
    .btn-hero-list:active {
      transform: scale(0.96);
    }

    /* Shelves & Carousels (Netflix style) */
    .shelf-container {
      margin-bottom: 24px;
    }

    .shelf-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      padding: 0 4px;
    }

    .shelf-title {
      font-size: 1.05rem;
      font-weight: 800;
      letter-spacing: -0.3px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .shelf-see-all {
      font-size: 0.8rem;
      font-weight: 700;
      color: var(--accent);
      cursor: pointer;
    }

    .shelf-carousel {
      display: flex;
      gap: 12px;
      overflow-x: auto;
      padding-bottom: 6px;
      scroll-snap-type: x mandatory;
      -webkit-overflow-scrolling: touch;
    }

    /* Movie Poster Cards */
    .card {
      flex: 0 0 135px;
      width: 135px;
      scroll-snap-align: start;
      display: flex;
      flex-direction: column;
      border-radius: var(--radius-sm);
      overflow: hidden;
      cursor: pointer;
      transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s;
      position: relative;
    }
    .card:active {
      transform: scale(0.95);
    }

    .poster-wrap {
      position: relative;
      width: 100%;
      aspect-ratio: 2 / 3;
      border-radius: var(--radius-sm);
      overflow: hidden;
      background: var(--card-bg);
      border: 1px solid var(--border);
    }

    .poster-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
      transition: transform 0.3s;
    }

    .card:hover .poster-img {
      transform: scale(1.04);
    }

    .card-badge {
      position: absolute;
      top: 6px;
      left: 6px;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      color: #fff;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 2px 6px;
      border-radius: 4px;
      border: 1px solid rgba(255, 255, 255, 0.15);
      text-transform: uppercase;
    }
    [dir="rtl"] .card-badge {
      left: auto;
      right: 6px;
    }

    .card-score {
      position: absolute;
      bottom: 6px;
      right: 6px;
      background: rgba(0, 0, 0, 0.8);
      color: var(--gold);
      font-size: 0.7rem;
      font-weight: 800;
      padding: 2px 6px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      gap: 3px;
    }
    [dir="rtl"] .card-score {
      right: auto;
      left: 6px;
    }

    .card-info {
      padding: 6px 2px 2px 2px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .card-title {
      font-size: 0.82rem;
      font-weight: 700;
      line-height: 1.25;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .card-meta {
      font-size: 0.72rem;
      color: var(--hint);
      display: flex;
      justify-content: space-between;
    }

    /* Grid for Search & Categories */
    .media-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(115px, 1fr));
      gap: 12px;
      margin-top: 14px;
    }
    .media-grid .card {
      width: 100%;
      flex: none;
    }

    /* Category Pill Bar */
    .chips-bar {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding: 4px 16px 8px 16px;
      scrollbar-width: none;
    }

    .chip {
      white-space: nowrap;
      padding: 6px 14px;
      border-radius: 20px;
      background: var(--surface);
      border: 1px solid var(--border);
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--text-muted);
      cursor: pointer;
      transition: all 0.2s;
    }

    .chip.active {
      background: var(--accent-gradient);
      color: #fff;
      border-color: transparent;
      box-shadow: 0 3px 12px var(--accent-glow);
    }

    /* Modern Bottom Navigation Bar */
    .bottom-nav {
      position: fixed;
      bottom: 0;
      left: 0;
      width: 100%;
      height: 68px;
      background: rgba(7, 9, 14, 0.92);
      backdrop-filter: blur(24px) saturate(180%);
      -webkit-backdrop-filter: blur(24px) saturate(180%);
      border-top: 1px solid var(--border);
      display: flex;
      justify-content: space-around;
      align-items: center;
      z-index: 90;
      padding-bottom: env(safe-area-inset-bottom, 0px);
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      color: var(--hint);
      font-size: 0.72rem;
      font-weight: 600;
      cursor: pointer;
      padding: 6px 14px;
      border-radius: 12px;
      transition: all 0.2s ease;
    }

    .nav-item.active {
      color: var(--accent);
      transform: translateY(-2px);
    }

    .nav-icon {
      font-size: 1.35rem;
      line-height: 1;
    }

    /* Drawer / Modal Sheet */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(10px);
      -webkit-backdrop-filter: blur(10px);
      z-index: 200;
      display: flex;
      align-items: flex-end;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .modal-overlay.open {
      opacity: 1;
      pointer-events: auto;
    }

    .modal-sheet {
      width: 100%;
      max-height: 90vh;
      background: var(--surface);
      border-top-left-radius: 24px;
      border-top-right-radius: 24px;
      border-top: 1px solid var(--border-bright);
      padding: 16px 20px 32px 20px;
      overflow-y: auto;
      transform: translateY(100%);
      transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .modal-overlay.open .modal-sheet {
      transform: translateY(0);
    }

    .modal-close-bar {
      width: 44px;
      height: 5px;
      background: var(--border-bright);
      border-radius: 3px;
      margin: 0 auto 16px auto;
      cursor: pointer;
    }

    /* Video Player */
    .player-container {
      width: 100%;
      background: #000;
      border-radius: 14px;
      overflow: hidden;
      margin-bottom: 16px;
      position: relative;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
    }

    video {
      width: 100%;
      aspect-ratio: 16 / 9;
      display: block;
      outline: none;
      background: #000;
    }

    .detail-header {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
    }

    .detail-poster {
      width: 100px;
      aspect-ratio: 2 / 3;
      border-radius: var(--radius-sm);
      object-fit: cover;
      border: 1px solid var(--border);
      flex-shrink: 0;
    }

    .detail-meta {
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 6px;
    }

    .detail-title {
      font-size: 1.18rem;
      font-weight: 800;
      line-height: 1.3;
    }

    .detail-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .tag {
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text-muted);
      font-size: 0.72rem;
      font-weight: 600;
      padding: 3px 8px;
      border-radius: 6px;
    }

    .tag-trying {
      background: rgba(99, 102, 241, 0.25);
      border-color: var(--accent);
      color: #c7d2fe;
      animation: tagPulse 1.4s ease-in-out infinite alternate;
      font-weight: 700;
    }

    @keyframes tagPulse {
      0% { opacity: 0.6; transform: scale(0.97); }
      100% { opacity: 1; transform: scale(1.02); }
    }

    .loading-state-tag {
      background: rgba(99, 102, 241, 0.15);
      border: 1px dashed rgba(99, 102, 241, 0.4);
      color: #c7d2fe;
      padding: 12px;
      border-radius: var(--radius-sm);
      text-align: center;
      font-size: 0.85rem;
      font-weight: 600;
      margin-bottom: 12px;
      animation: tagPulse 1.4s ease-in-out infinite alternate;
    }

    .detail-plot {
      font-size: 0.85rem;
      line-height: 1.5;
      color: var(--text-muted);
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
      border-radius: var(--radius-sm);
      border: none;
      font-weight: 700;
      font-size: 0.9rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s;
    }

    .btn-primary {
      background: var(--accent-gradient);
      color: #fff;
      box-shadow: 0 4px 16px var(--accent-glow);
    }

    .btn-secondary {
      background: var(--card-bg);
      border: 1px solid var(--border);
      color: var(--text);
    }

    /* Episode Selector */
    .episode-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
      gap: 8px;
      max-height: 220px;
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
      transition: all 0.2s;
    }

    .ep-btn.active {
      background: var(--accent-gradient);
      color: #fff;
      border-color: transparent;
      box-shadow: 0 2px 8px var(--accent-glow);
    }

    /* Stream Links */
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
      padding: 12px 16px;
      border-radius: var(--radius-sm);
      background: var(--card-bg);
      border: 1px solid var(--border);
      font-size: 0.86rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .link-item:hover, .link-item:active {
      border-color: var(--accent);
      background: var(--card-hover);
    }

    /* Toast Notification */
    .toast {
      position: fixed;
      bottom: 84px;
      left: 50%;
      transform: translateX(-50%) translateY(40px);
      background: rgba(21, 27, 40, 0.95);
      border: 1px solid var(--border-bright);
      color: #fff;
      padding: 10px 20px;
      border-radius: 30px;
      font-size: 0.85rem;
      font-weight: 600;
      opacity: 0;
      pointer-events: none;
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      z-index: 999;
      box-shadow: 0 8px 24px rgba(0,0,0,0.6);
    }

    .toast.show {
      opacity: 1;
      transform: translateX(-50%) translateY(0);
    }

    .spinner {
      display: inline-block;
      width: 28px;
      height: 28px;
      border: 3px solid rgba(255,255,255,0.15);
      border-radius: 50%;
      border-top-color: var(--accent);
      animation: spin 0.8s linear infinite;
      margin: 30px auto;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 50px 20px;
      color: var(--hint);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }
  </style>
</head>
<body>

  <!-- Sticky Header -->
  <header>
    <div class="top-bar">
      <div class="brand-wrap">
        <span class="brand-logo">🎬</span>
        <span class="brand-title">TeleStream</span>
        <div class="source-chip" id="sourceChip">
          <span>📡</span>
          <span id="currentSourceName">KissKH</span>
          <span>▾</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="pill-btn" id="langToggle">🌐 EN</button>
        <button class="pill-btn" id="donateBtn">💎</button>
      </div>
    </div>
    <div class="search-container">
      <span class="search-icon">🔍</span>
      <input type="text" class="search-input" id="searchInput" placeholder="Search movies, TV shows, anime..." />
    </div>
  </header>

  <!-- Category Filter Chips -->
  <div class="chips-bar" id="chipsBar">
    <div class="chip active" data-filter="home">🌟 Home</div>
    <div class="chip" data-filter="popular">🔥 Popular</div>
    <div class="chip" data-filter="latest">✨ Latest</div>
    <div class="chip" data-filter="sections">📂 Categories</div>
  </div>

  <!-- Main Views Container -->
  <main style="padding: 12px 16px;">
    <!-- Home View -->
    <div id="homeView">
      <!-- Dynamic Hero Billboard -->
      <div class="hero-billboard" id="heroBillboard" style="display: none;">
        <div class="hero-overlay"></div>
        <div class="hero-content">
          <div class="hero-badges">
            <span class="badge-gold">⭐ 8.7</span>
            <span class="badge-sub" id="heroType">MOVIE</span>
            <span class="badge-sub" id="heroSource">KissKH</span>
          </div>
          <div class="hero-title" id="heroTitle">Featured Title</div>
          <div class="hero-synopsis" id="heroSynopsis">Synopsis preview will appear here...</div>
          <div class="hero-actions">
            <button class="btn-hero-play" id="heroPlayBtn">▶ Play Now</button>
            <button class="btn-hero-list" id="heroBookmarkBtn">+ My List</button>
          </div>
        </div>
      </div>

      <!-- Category Shelves Container -->
      <div id="shelvesContainer">
        <!-- Rendered dynamically by JS -->
      </div>
    </div>

    <!-- Search Results / Grid View -->
    <div id="gridView" style="display: none;">
      <div class="shelf-header">
        <div class="shelf-title" id="gridTitle">Results</div>
      </div>
      <div class="media-grid" id="mediaGrid"></div>
    </div>

    <!-- Watch History View -->
    <div id="historyView" style="display: none;">
      <div class="shelf-header">
        <div class="shelf-title">🕒 <span id="historyTitleText">Continue Watching</span></div>
        <span class="shelf-see-all" id="clearHistoryBtn" style="color: var(--danger);">Clear</span>
      </div>
      <div class="media-grid" id="historyGrid"></div>
    </div>

    <!-- Bookmarks View -->
    <div id="bookmarksView" style="display: none;">
      <div class="shelf-header">
        <div class="shelf-title">⭐ <span id="bookmarksTitleText">My Saved List</span></div>
      </div>
      <div class="media-grid" id="bookmarksGrid"></div>
    </div>

    <!-- Categories Directory View -->
    <div id="categoriesView" style="display: none;">
      <div class="shelf-header">
        <div class="shelf-title">📂 <span id="categoriesDirectoryTitle">Provider Categories</span></div>
      </div>
      <div class="links-list" id="categoriesList"></div>
    </div>

    <div id="loadingSpinner" style="display: none; text-align: center;">
      <div class="spinner"></div>
    </div>
  </main>

  <!-- Bottom Navigation -->
  <nav class="bottom-nav">
    <div class="nav-item active" id="navHome">
      <span class="nav-icon">🏠</span>
      <span id="navHomeText">Home</span>
    </div>
    <div class="nav-item" id="navCategories">
      <span class="nav-icon">📂</span>
      <span id="navCategoriesText">Categories</span>
    </div>
    <div class="nav-item" id="navHistory">
      <span class="nav-icon">🕒</span>
      <span id="navHistoryText">History</span>
    </div>
    <div class="nav-item" id="navBookmarks">
      <span class="nav-icon">⭐</span>
      <span id="navBookmarksText">My List</span>
    </div>
  </nav>

  <!-- Details & Video Player Modal -->
  <div class="modal-overlay" id="detailModal">
    <div class="modal-sheet">
      <div class="modal-close-bar" id="closeBar"></div>
      
      <!-- Video Player Area -->
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

      <!-- Episodes for TV Shows / Anime -->
      <div id="episodesSection" style="display: none;">
        <div class="shelf-title" style="margin-bottom: 8px;">📺 <span id="episodesTitleText">Episodes</span></div>
        <div class="episode-grid" id="episodesGrid"></div>
      </div>

      <!-- Stream Quality & Servers -->
      <div id="streamsSection" style="display: none;">
        <div class="shelf-title" style="margin-bottom: 8px;">📡 <span id="streamsTitleText">Available Streams</span></div>
        <div class="links-list" id="linksList"></div>
      </div>
    </div>
  </div>

  <!-- Source Switcher Modal -->
  <div class="modal-overlay" id="sourceModal">
    <div class="modal-sheet">
      <div class="modal-close-bar" id="closeSourceBar"></div>
      <div class="shelf-title" style="margin-bottom: 12px;">📡 <span id="sourceModalTitle">Select Provider</span></div>
      <div class="links-list" id="sourcesList"></div>
    </div>
  </div>

  <!-- Donation Modal -->
  <div class="modal-overlay" id="donationModal">
    <div class="modal-sheet">
      <div class="modal-close-bar" id="closeDonateBar"></div>
      <div class="shelf-title" style="margin-bottom: 8px;">💎 <span id="donateModalTitle">Support TeleStream</span></div>
      <p style="font-size: 0.84rem; color: var(--hint); margin-bottom: 16px;" id="donateSubText">
        Tap on any address below to copy:
      </p>
      <div class="links-list" id="donationWallets"></div>
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
      if (tg.setHeaderColor) tg.setHeaderColor('#07090e');
    }

    function haptic(type = 'light') {
      if (tg && tg.HapticFeedback) {
        tg.HapticFeedback.impactOccurred(type);
      }
    }

    // State
    let currentLang = localStorage.getItem('telestream_lang') || 'en';
    let currentProvider = localStorage.getItem('telestream_provider') || 'KissKH';
    let activeMedia = null;
    let heroMedia = null;
    let currentEpisodeData = null;
    let hlsInstance = null;
    let configData = {};
    let searchDebounceTimer = null;
    let allSources = [];
    let isDetailLoading = false;
    let isExtractingLinks = false;
    let isSearchLoading = false;

    // Translations Dictionary
    const i18n = {
      en: {
        searchPlaceholder: "Search movies, TV shows, anime...",
        home: "Home",
        popular: "Popular & Trending",
        latest: "Latest Releases",
        categories: "Categories",
        history: "Continue Watching",
        bookmarks: "My Saved List",
        play: "Play Stream",
        bookmark: "Add to List",
        bookmarked: "In List",
        episodes: "Episodes",
        streams: "Available Streams",
        donateTitle: "Support TeleStream",
        donateDesc: "Tap on any address below to copy it:",
        copied: "Address copied to clipboard! ✅",
        noResults: "No titles found. Try another search!",
        noBookmarks: "No saved titles in your list yet.",
        noHistory: "You haven't watched any movies or series yet.",
        extracting: "Resolving stream links...",
        loadingDetails: "⏳ Trying to fetch details...",
        loadingLinks: "⏳ Trying to extract streaming links...",
        requestInProgress: "⏳ A request is already loading, please wait...",
        selectProvider: "Select Provider / Source",
        seeAll: "See All"
      },
      fa: {
        searchPlaceholder: "جستجوی فیلم، سریال، انیمه...",
        home: "خانه",
        popular: "محبوب و پرطرفدار",
        latest: "تازه‌ترین آثار",
        categories: "دسته‌بندی‌ها",
        history: "ادامه تماشا",
        bookmarks: "نشان‌شده‌ها",
        play: "پخش آنلاین",
        bookmark: "افزودن به لیست",
        bookmarked: "در لیست شما",
        episodes: "قسمت‌ها",
        streams: "کیفیت‌ها و سرورها",
        donateTitle: "حمایت مالی از پروژه تله‌استریم",
        donateDesc: "برای کپی کردن آدرس، کافیست روی آن ضربه بزنید:",
        copied: "آدرس با موفقیت کپی شد! ✅",
        noResults: "نتیجه‌ای یافت نشد. عنوان دیگری را جستجو کنید!",
        noBookmarks: "هنوز فیلمی ذخیره نکرده‌اید.",
        noHistory: "هنوز اثری را تماشا نکرده‌اید.",
        extracting: "در حال دریافت لینک‌های پخش...",
        loadingDetails: "⏳ در حال دریافت مشخصات...",
        loadingLinks: "⏳ در حال استخراج لینک‌های پخش...",
        requestInProgress: "⏳ لطفاً صبر کنید، درخواست قبلی در حال بارگذاری است...",
        selectProvider: "انتخاب منبع فیلم و سریال",
        seeAll: "مشاهده همه"
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
      document.getElementById('navHomeText').textContent = t.home;
      document.getElementById('navCategoriesText').textContent = t.categories;
      document.getElementById('navHistoryText').textContent = t.history;
      document.getElementById('navBookmarksText').textContent = t.bookmarks;
      document.getElementById('historyTitleText').textContent = t.history;
      document.getElementById('bookmarksTitleText').textContent = t.bookmarks;
      document.getElementById('categoriesDirectoryTitle').textContent = t.categories;
      document.getElementById('playBtnText').textContent = t.play;
      document.getElementById('episodesTitleText').textContent = t.episodes;
      document.getElementById('streamsTitleText').textContent = t.streams;
      document.getElementById('donateModalTitle').textContent = t.donateTitle;
      document.getElementById('donateSubText').textContent = t.donateDesc;
      document.getElementById('sourceModalTitle').textContent = t.selectProvider;
    }

    function showToast(msg) {
      const toast = document.getElementById('toast');
      toast.textContent = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2400);
    }

    // Load App Config & Sources
    async function loadConfig() {
      try {
        const res = await fetch('/api/config');
        configData = await res.json();
        if (configData.activeProviders && configData.activeProviders.length > 0) {
          if (!configData.activeProviders.includes(currentProvider)) {
            currentProvider = configData.activeProviders[0];
          }
        }
        document.getElementById('currentSourceName').textContent = currentProvider;
        renderDonations();
      } catch (e) {
        console.error("Config fetch failed", e);
      }

      try {
        const res = await fetch('/api/sources?filter=all');
        allSources = await res.json();
      } catch (e) {}
    }

    function renderDonations() {
      const list = document.getElementById('donationWallets');
      list.innerHTML = '';
      const wallets = [
        { label: "USDT (TRC20)", address: configData.usdtTrc20 || "TBor8Rmq1UNeQ6kZMnxUPqRns3aU8tsD1D", icon: "💵" },
        { label: "TON (Telegram / TON)", address: configData.tonWallet || "UQDP14pSjV1k8L0Fj8d2p7k8XqZ7YjB7p9", icon: "⚡" },
        { label: "Bitcoin (BTC)", address: configData.btcWallet || "bc1qlh484m8e0ff4pewvuyu0xg7tc7zzynzkj6ufcx", icon: "🪙" },
        { label: "Ethereum (ERC20)", address: configData.ethWallet || "0x86dA13b11011B7Bdff2259B576AD5c1c9E94d3Ef", icon: "🔷" }
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

    // Switch Provider Source
    function openSourceModal() {
      haptic('light');
      const modal = document.getElementById('sourceModal');
      const list = document.getElementById('sourcesList');
      list.innerHTML = '';

      const providers = configData.activeProviders || [currentProvider];
      providers.forEach(p => {
        const item = document.createElement('div');
        item.className = 'link-item';
        const isCurrent = p === currentProvider;
        item.innerHTML = '<div>' + (isCurrent ? '🔘 ' : '📡 ') + '<b>' + p + '</b></div>' + (isCurrent ? '<span style="color:var(--accent); font-size:0.8rem;">Active</span>' : '');
        item.onclick = () => {
          haptic('medium');
          currentProvider = p;
          localStorage.setItem('telestream_provider', p);
          document.getElementById('currentSourceName').textContent = p;
          modal.classList.remove('open');
          loadHomeScreen();
        };
        list.appendChild(item);
      });

      modal.classList.add('open');
    }

    // Home Screen Loader (Hero + Category Shelves)
    async function loadHomeScreen() {
      const spinner = document.getElementById('loadingSpinner');
      spinner.style.display = 'block';
      const shelvesContainer = document.getElementById('shelvesContainer');
      shelvesContainer.innerHTML = '';

      try {
        // Fetch Popular, Latest & Sections concurrently
        const [popRes, latestRes, secRes] = await Promise.all([
          fetch('/api/popular?provider=' + encodeURIComponent(currentProvider) + '&page=1').then(r => r.json()).catch(() => []),
          fetch('/api/latest?provider=' + encodeURIComponent(currentProvider) + '&page=1').then(r => r.json()).catch(() => []),
          fetch('/api/sections?provider=' + encodeURIComponent(currentProvider)).then(r => r.json()).catch(() => [])
        ]);

        spinner.style.display = 'none';

        // Billboard using 1st item of popular
        if (popRes.length > 0) {
          setupHeroBillboard(popRes[0]);
        } else if (latestRes.length > 0) {
          setupHeroBillboard(latestRes[0]);
        }

        // Shelf 1: Popular
        if (popRes.length > 0) {
          renderShelf(i18n[currentLang].popular, "🔥", popRes);
        }

        // Shelf 2: Latest
        if (latestRes.length > 0) {
          renderShelf(i18n[currentLang].latest, "✨", latestRes);
        }

        // Shelves from Sections (take up to 3)
        if (secRes && secRes.length > 0) {
          for (let s of secRes.slice(0, 3)) {
            try {
              const items = await fetch('/api/section?provider=' + encodeURIComponent(currentProvider) + '&name=' + encodeURIComponent(s.name) + '&page=1').then(r => r.json());
              if (items && items.length > 0) {
                renderShelf(s.name, "📂", items);
              }
            } catch (err) {}
          }
        }
      } catch (e) {
        spinner.style.display = 'none';
        shelvesContainer.innerHTML = '<div class="empty-state">⚠️ Failed to load content from ' + currentProvider + '</div>';
      }
    }

    function setupHeroBillboard(item) {
      heroMedia = item;
      const billboard = document.getElementById('heroBillboard');
      const poster = item.posterUrl || '';
      billboard.style.backgroundImage = 'url("' + poster + '")';
      document.getElementById('heroTitle').textContent = item.name;
      document.getElementById('heroType').textContent = item.type || 'MOVIE';
      document.getElementById('heroSource').textContent = currentProvider;
      document.getElementById('heroSynopsis').textContent = "Trending now on " + currentProvider + ". Tap Play to start streaming instantly in HD.";
      billboard.style.display = 'flex';

      document.getElementById('heroPlayBtn').onclick = () => {
        haptic('medium');
        openDetails(heroMedia);
      };

      document.getElementById('heroBookmarkBtn').onclick = () => {
        toggleBookmark(heroMedia);
      };
    }

    function renderShelf(title, icon, items) {
      const container = document.getElementById('shelvesContainer');
      const shelf = document.createElement('div');
      shelf.className = 'shelf-container';

      shelf.innerHTML = 
        '<div class="shelf-header">' +
          '<div class="shelf-title">' + icon + ' ' + title + '</div>' +
          '<span class="shelf-see-all">' + i18n[currentLang].seeAll + '</span>' +
        '</div>' +
        '<div class="shelf-carousel"></div>';

      const carousel = shelf.querySelector('.shelf-carousel');
      items.forEach(item => {
        const card = createCardElement(item);
        carousel.appendChild(card);
      });

      shelf.querySelector('.shelf-see-all').onclick = () => {
        haptic('light');
        openGridView(title, items);
      };

      container.appendChild(shelf);
    }

    function createCardElement(item) {
      const card = document.createElement('div');
      card.className = 'card';
      const poster = item.posterUrl || 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300';
      const typeBadge = item.type || 'HD';
      const year = item.year ? item.year : '';

      card.innerHTML = 
        '<div class="poster-wrap">' +
          '<img class="poster-img" src="' + poster + '" loading="lazy" onerror="this.src=\'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300\'" />' +
          '<span class="card-badge">' + typeBadge + '</span>' +
          '<span class="card-score">⭐ 8.5</span>' +
        '</div>' +
        '<div class="card-info">' +
          '<div class="card-title">' + item.name + '</div>' +
          '<div class="card-meta">' +
            '<span>' + year + '</span>' +
            '<span>' + (item.apiName || currentProvider) + '</span>' +
          '</div>' +
        '</div>';

      card.onclick = () => {
        haptic('light');
        openDetails(item);
      };

      return card;
    }

    function openGridView(title, items) {
      document.getElementById('homeView').style.display = 'none';
      document.getElementById('categoriesView').style.display = 'none';
      document.getElementById('historyView').style.display = 'none';
      document.getElementById('bookmarksView').style.display = 'none';
      const gridView = document.getElementById('gridView');
      document.getElementById('gridTitle').textContent = title;
      const grid = document.getElementById('mediaGrid');
      grid.innerHTML = '';
      items.forEach(it => grid.appendChild(createCardElement(it)));
      gridView.style.display = 'block';
    }

    // Media Search & Render
    async function searchMedia(query) {
      if (isSearchLoading) {
        showToast(i18n[currentLang].requestInProgress);
        return;
      }
      isSearchLoading = true;
      document.getElementById('homeView').style.display = 'none';
      document.getElementById('categoriesView').style.display = 'none';
      document.getElementById('historyView').style.display = 'none';
      document.getElementById('bookmarksView').style.display = 'none';
      const gridView = document.getElementById('gridView');
      gridView.style.display = 'block';
      document.getElementById('gridTitle').textContent = (currentLang === 'fa' ? 'جستجو: ' : 'Search: ') + query;

      const grid = document.getElementById('mediaGrid');
      const spinner = document.getElementById('loadingSpinner');
      spinner.style.display = 'block';
      grid.innerHTML = '<div class="loading-state-tag">' + (currentLang === 'fa' ? '⏳ در حال جستجو در سورس...' : '⏳ Trying to search in source...') + '</div>';

      try {
        const res = await fetch('/api/search?provider=' + encodeURIComponent(currentProvider) + '&q=' + encodeURIComponent(query));
        const items = await res.json();
        spinner.style.display = 'none';
        grid.innerHTML = '';

        if (!items || items.length === 0) {
          grid.innerHTML = '<div class="empty-state"><span>🎬</span><span>' + i18n[currentLang].noResults + '</span></div>';
          return;
        }

        items.forEach(it => grid.appendChild(createCardElement(it)));
      } catch (e) {
        spinner.style.display = 'none';
        grid.innerHTML = '<div class="empty-state">❌ Search failed. Check connection or try another source.</div>';
      } finally {
        isSearchLoading = false;
      }
    }

    // Load Provider Categories Directory
    async function loadCategoriesDirectory() {
      const spinner = document.getElementById('loadingSpinner');
      spinner.style.display = 'block';
      const list = document.getElementById('categoriesList');
      list.innerHTML = '';

      try {
        const sections = await fetch('/api/sections?provider=' + encodeURIComponent(currentProvider)).then(r => r.json());
        spinner.style.display = 'none';

        if (!sections || sections.length === 0) {
          list.innerHTML = '<div class="empty-state">No specific categories for ' + currentProvider + '</div>';
          return;
        }

        sections.forEach(s => {
          const div = document.createElement('div');
          div.className = 'link-item';
          div.innerHTML = '<div>📂 <b>' + s.name + '</b></div><div style="color:var(--accent); font-size:0.8rem;">Browse ▶</div>';
          div.onclick = async () => {
            haptic('medium');
            spinner.style.display = 'block';
            const items = await fetch('/api/section?provider=' + encodeURIComponent(currentProvider) + '&name=' + encodeURIComponent(s.name) + '&page=1').then(r => r.json()).catch(() => []);
            spinner.style.display = 'none';
            openGridView(s.name, items);
          };
          list.appendChild(div);
        });
      } catch (e) {
        spinner.style.display = 'none';
        list.innerHTML = '<div class="empty-state">Failed to load categories</div>';
      }
    }

    // Media Details & In-App Player
    async function openDetails(item) {
      if (isDetailLoading) {
        showToast(i18n[currentLang].requestInProgress);
        return;
      }
      isDetailLoading = true;
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
      tags.innerHTML = '<span class="tag">' + (item.apiName || currentProvider) + '</span>' +
        '<span class="tag">' + (item.type || 'HD') + '</span>' +
        (item.year ? '<span class="tag">' + item.year + '</span>' : '') +
        '<span class="tag tag-trying" id="tryingTag">' + i18n[currentLang].loadingDetails + '</span>';
      plot.textContent = i18n[currentLang].loadingDetails;

      updateBookmarkButtonState(item);
      modal.classList.add('open');

      try {
        const provider = item.apiName || currentProvider;
        const res = await fetch('/api/load?provider=' + encodeURIComponent(provider) + '&url=' + encodeURIComponent(item.url));
        const details = await res.json();
        plot.textContent = details.plot || "No synopsis available.";
        activeMedia.details = details;

        const tryingTag = document.getElementById('tryingTag');
        if (tryingTag) tryingTag.remove();

        if (details.episodes && details.episodes.length > 0) {
          renderEpisodes(details.episodes);
          epSec.style.display = 'block';
        }
      } catch (e) {
        plot.textContent = "Unable to load complete details.";
        const tryingTag = document.getElementById('tryingTag');
        if (tryingTag) tryingTag.remove();
      } finally {
        isDetailLoading = false;
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
          fetchAndPlayStreams(activeMedia.apiName || currentProvider, ep.data, ep.name || ('Episode ' + (ep.episode || (idx + 1))));
        };
        grid.appendChild(btn);
      });
      currentEpisodeData = episodes[0].data;
    }

    async function fetchAndPlayStreams(provider, dataUrl, epTitle = null) {
      if (isExtractingLinks) {
        showToast(i18n[currentLang].requestInProgress);
        return;
      }
      isExtractingLinks = true;
      showToast(i18n[currentLang].loadingLinks);
      const streamSec = document.getElementById('streamsSection');
      const list = document.getElementById('linksList');
      list.innerHTML = '<div class="loading-state-tag">' + i18n[currentLang].loadingLinks + '</div>';
      streamSec.style.display = 'block';

      try {
        const res = await fetch('/api/links?provider=' + encodeURIComponent(provider) + '&data=' + encodeURIComponent(dataUrl));
        const links = await res.json();

        if (!links || links.length === 0) {
          list.innerHTML = '<div class="empty-state">⚠️ No direct streams found.</div>';
          streamSec.style.display = 'block';
          return;
        }

        list.innerHTML = '';
        streamSec.style.display = 'block';
        links.forEach((link, idx) => {
          const item = document.createElement('div');
          item.className = 'link-item';
          const icon = link.isM3u8 ? '⚡' : '📥';
          const typeBadge = link.isM3u8 ? 'HLS Stream' : 'Direct';
          item.innerHTML = 
            '<div>' + icon + ' ' + (link.name || 'Server ' + (idx + 1)) + ' [' + link.quality + 'p • ' + typeBadge + ']</div>' +
            '<div style="font-size:0.78rem; color:var(--accent); font-weight:700;">▶ Play</div>';
          
          item.onclick = () => {
            haptic('medium');
            playStream(link);
            recordHistory(activeMedia, link.url, epTitle);
          };
          list.appendChild(item);
        });

        // Autoplay highest quality
        playStream(links[0]);
        recordHistory(activeMedia, links[0].url, epTitle);
      } catch (e) {
        showToast("Failed to resolve links");
        list.innerHTML = '<div class="empty-state">❌ Failed to resolve streaming links.</div>';
      } finally {
        isExtractingLinks = false;
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

    // Watch History Management
    async function recordHistory(media, streamUrl, episodeTitle = null) {
      if (!media) return;
      try {
        const payload = {
          userId: (tg && tg.initDataUnsafe && tg.initDataUnsafe.user) ? tg.initDataUnsafe.user.id : 0,
          title: media.name,
          url: media.url,
          posterUrl: media.posterUrl,
          provider: media.apiName || currentProvider,
          episodeName: episodeTitle,
          streamUrl: streamUrl
        };
        await fetch('/api/history', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
      } catch (e) {}

      // Also save in localStorage for offline fast UI
      let localHistory = JSON.parse(localStorage.getItem('telestream_history') || '[]');
      localHistory = localHistory.filter(h => h.url !== media.url);
      localHistory.unshift({
        title: media.name,
        url: media.url,
        posterUrl: media.posterUrl,
        provider: media.apiName || currentProvider,
        episodeName: episodeTitle
      });
      localStorage.setItem('telestream_history', JSON.stringify(localHistory.slice(0, 20)));
    }

    function renderHistoryView() {
      const grid = document.getElementById('historyGrid');
      const history = JSON.parse(localStorage.getItem('telestream_history') || '[]');
      grid.innerHTML = '';
      if (history.length === 0) {
        grid.innerHTML = '<div class="empty-state"><span>🕒</span><span>' + i18n[currentLang].noHistory + '</span></div>';
        return;
      }
      history.forEach(item => {
        grid.appendChild(createCardElement(item));
      });
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
      if (!media) return;
      haptic('medium');
      let bookmarks = getBookmarks();
      const exists = isBookmarked(media.url);
      if (exists) {
        bookmarks = bookmarks.filter(b => b.url !== media.url);
        showToast("Removed from My List");
      } else {
        bookmarks.unshift(media);
        showToast("Added to My List! ⭐");
      }
      localStorage.setItem('telestream_bookmarks', JSON.stringify(bookmarks));
      updateBookmarkButtonState(media);
    }

    function updateBookmarkButtonState(media) {
      const btn = document.getElementById('bookmarkDetailBtn');
      const isSaved = isBookmarked(media.url);
      btn.innerHTML = (isSaved ? '❌ ' : '⭐ ') + '<span id="bookmarkBtnText">' + (isSaved ? i18n[currentLang].bookmarked : i18n[currentLang].bookmark) + '</span>';
    }

    function renderBookmarksView() {
      const grid = document.getElementById('bookmarksGrid');
      const bookmarks = getBookmarks();
      grid.innerHTML = '';
      if (bookmarks.length === 0) {
        grid.innerHTML = '<div class="empty-state"><span>⭐</span><span>' + i18n[currentLang].noBookmarks + '</span></div>';
        return;
      }
      bookmarks.forEach(item => {
        grid.appendChild(createCardElement(item));
      });
    }

    // Deep-linking Router (#/watch?provider=...&data=...&title=...)
    function handleDeepLinks() {
      const hash = window.location.hash;
      if (hash.startsWith('#/watch')) {
        const params = new URLSearchParams(hash.substring(hash.indexOf('?')));
        const provider = params.get('provider') || currentProvider;
        const dataUrl = params.get('data');
        const title = params.get('title') || 'Streaming Video';
        if (dataUrl) {
          activeMedia = { name: title, url: dataUrl, apiName: provider };
          openDetails(activeMedia);
          fetchAndPlayStreams(provider, dataUrl, title);
        }
      }
    }

    // Event Listeners
    document.getElementById('langToggle').onclick = () => {
      haptic('light');
      applyLanguage(currentLang === 'en' ? 'fa' : 'en');
    };

    document.getElementById('sourceChip').onclick = () => {
      openSourceModal();
    };

    document.getElementById('closeSourceBar').onclick = () => {
      document.getElementById('sourceModal').classList.remove('open');
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
        fetchAndPlayStreams(activeMedia.apiName || currentProvider, dataUrl);
      }
    };

    document.getElementById('bookmarkDetailBtn').onclick = () => {
      if (activeMedia) toggleBookmark(activeMedia);
    };

    document.getElementById('clearHistoryBtn').onclick = () => {
      haptic('medium');
      localStorage.removeItem('telestream_history');
      renderHistoryView();
      showToast("History cleared");
    };

    // Filter Chips
    document.querySelectorAll('.chip').forEach(chip => {
      chip.onclick = () => {
        haptic('light');
        document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        const filter = chip.dataset.filter;

        if (filter === 'home') {
          showTab('home');
        } else if (filter === 'popular') {
          fetch('/api/popular?provider=' + encodeURIComponent(currentProvider) + '&page=1')
            .then(r => r.json())
            .then(items => openGridView(i18n[currentLang].popular, items));
        } else if (filter === 'latest') {
          fetch('/api/latest?provider=' + encodeURIComponent(currentProvider) + '&page=1')
            .then(r => r.json())
            .then(items => openGridView(i18n[currentLang].latest, items));
        } else if (filter === 'sections') {
          showTab('categories');
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
      } else if (val.length === 0) {
        showTab('home');
      }
    });

    // Bottom Navigation Router
    function showTab(tab) {
      document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
      document.getElementById('homeView').style.display = 'none';
      document.getElementById('gridView').style.display = 'none';
      document.getElementById('categoriesView').style.display = 'none';
      document.getElementById('historyView').style.display = 'none';
      document.getElementById('bookmarksView').style.display = 'none';

      if (tab === 'home') {
        document.getElementById('navHome').classList.add('active');
        document.getElementById('homeView').style.display = 'block';
      } else if (tab === 'categories') {
        document.getElementById('navCategories').classList.add('active');
        document.getElementById('categoriesView').style.display = 'block';
        loadCategoriesDirectory();
      } else if (tab === 'history') {
        document.getElementById('navHistory').classList.add('active');
        document.getElementById('historyView').style.display = 'block';
        renderHistoryView();
      } else if (tab === 'bookmarks') {
        document.getElementById('navBookmarks').classList.add('active');
        document.getElementById('bookmarksView').style.display = 'block';
        renderBookmarksView();
      }
    }

    document.getElementById('navHome').onclick = () => { haptic('light'); showTab('home'); };
    document.getElementById('navCategories').onclick = () => { haptic('light'); showTab('categories'); };
    document.getElementById('navHistory').onclick = () => { haptic('light'); showTab('history'); };
    document.getElementById('navBookmarks').onclick = () => { haptic('light'); showTab('bookmarks'); };

    // Initial Bootstrap
    applyLanguage(currentLang);
    loadConfig().then(() => {
      loadHomeScreen();
      handleDeepLinks();
    });
  </script>
</body>
</html>
        """.trimIndent()
    }
}
