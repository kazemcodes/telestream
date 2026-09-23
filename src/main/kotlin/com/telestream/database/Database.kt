package com.telestream.database

import java.io.File
import java.sql.DriverManager

data class Bookmark(
    val id: Int,
    val userId: Long,
    val provider: String,
    val mediaUrl: String,
    val title: String,
    val posterUrl: String
)

data class WatchHistoryItem(
    val id: Int,
    val userId: Long,
    val provider: String,
    val mediaUrl: String,
    val title: String,
    val posterUrl: String?,
    val episodeTitle: String?,
    val episodeData: String?,
    val updatedAt: String
)

object Database {
    private val dbDir = File("data").apply { mkdirs() }
    private val dbFile = File(dbDir, "telestream.db")
    private val url = "jdbc:sqlite:${dbFile.absolutePath}"

    init {
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                // High-concurrency multi-user optimizations
                stmt.execute("PRAGMA journal_mode = WAL;")
                stmt.execute("PRAGMA synchronous = NORMAL;")
                stmt.execute("PRAGMA busy_timeout = 5000;")
                stmt.execute("PRAGMA cache_size = -8000;") // 8MB memory cache

                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INTEGER PRIMARY KEY,
                        language TEXT DEFAULT 'en',
                        active_source TEXT DEFAULT 'KissKH',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """.trimIndent()
                )
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN active_source TEXT DEFAULT 'KissKH';")
                } catch (ignored: Exception) {}
                try {
                    stmt.execute("UPDATE users SET active_source = 'KissKH' WHERE active_source = 'AvaMovie';")
                } catch (ignored: Exception) {}
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER,
                        provider TEXT,
                        media_url TEXT,
                        title TEXT,
                        poster_url TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(user_id, media_url)
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS user_sources (
                        user_id INTEGER,
                        source_name TEXT,
                        is_enabled INTEGER DEFAULT 1,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (user_id, source_name)
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS watch_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        media_url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        poster_url TEXT,
                        episode_title TEXT,
                        episode_data TEXT,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(user_id, media_url)
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS admin_repos (
                        repo_id TEXT PRIMARY KEY,
                        repo_name TEXT,
                        is_enabled INTEGER DEFAULT 1,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """.trimIndent()
                )
            }
        }
    }

    fun ensureUser(userId: Long, defaultLang: String = "en") {
        DriverManager.getConnection(url).use { conn ->
            val checkSql = "SELECT user_id, language, active_source FROM users WHERE user_id = ?"
            conn.prepareStatement(checkSql).use { checkStmt ->
                checkStmt.setLong(1, userId)
                val rs = checkStmt.executeQuery()
                if (!rs.next()) {
                    val activeSrc = "KissKH"
                    val insertSql = "INSERT INTO users (user_id, language, active_source) VALUES (?, ?, ?)"
                    conn.prepareStatement(insertSql).use { insStmt ->
                        insStmt.setLong(1, userId)
                        insStmt.setString(2, defaultLang)
                        insStmt.setString(3, activeSrc)
                        insStmt.executeUpdate()
                    }
                    val sourceSql = "INSERT OR IGNORE INTO user_sources (user_id, source_name, is_enabled) VALUES (?, ?, 1)"
                    conn.prepareStatement(sourceSql).use { sStmt ->
                        sStmt.setLong(1, userId)
                        sStmt.setString(2, "KissKH")
                        sStmt.executeUpdate()
                        sStmt.setLong(1, userId)
                        sStmt.setString(2, "StreamPlay")
                        sStmt.executeUpdate()
                    }
                }
            }
        }
    }

    fun getUserLanguage(userId: Long): String {
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT language FROM users WHERE user_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getString("language") ?: "en"
                }
            }
        }
        return "en"
    }

    fun setUserLanguage(userId: Long, language: String) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO users (user_id, language) VALUES (?, ?)
                ON CONFLICT(user_id) DO UPDATE SET language = excluded.language
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, language)
                stmt.executeUpdate()
            }
        }
    }

    fun isSourceEnabled(userId: Long, sourceName: String): Boolean {
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT is_enabled FROM user_sources WHERE user_id = ? AND source_name = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, sourceName)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getInt("is_enabled") == 1
                }
            }
        }
        // Defaults if not explicitly recorded yet
        if (sourceName.equals("KissKH", ignoreCase = true) ||
            sourceName.equals("StreamPlay", ignoreCase = true) ||
            sourceName.equals("SuperStream", ignoreCase = true)) return true
        return false
    }

    fun setSourceEnabled(userId: Long, sourceName: String, enabled: Boolean) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO user_sources (user_id, source_name, is_enabled) VALUES (?, ?, ?)
                ON CONFLICT(user_id, source_name) DO UPDATE SET is_enabled = excluded.is_enabled, updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, sourceName)
                stmt.setInt(3, if (enabled) 1 else 0)
                stmt.executeUpdate()
            }
        }
    }

    fun toggleSourceEnabled(userId: Long, sourceName: String): Boolean {
        val current = isSourceEnabled(userId, sourceName)
        val newStatus = !current
        setSourceEnabled(userId, sourceName, newStatus)
        return newStatus
    }

    fun getEnabledSources(userId: Long): List<String> {
        val enabledSet = mutableSetOf<String>()

        // Query explicitly enabled sources from DB
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT source_name FROM user_sources WHERE user_id = ? AND is_enabled = 1"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val s = rs.getString("source_name")
                    if (!s.isNullOrBlank()) {
                        enabledSet.add(s)
                    }
                }
            }
        }
        if (enabledSet.isEmpty()) {
            enabledSet.add("KissKH")
            enabledSet.add("StreamPlay")
        }
        return enabledSet.toList()
    }

    fun setSourcesBulk(userId: Long, sourceNames: List<String>, enabled: Boolean) {
        DriverManager.getConnection(url).use { conn ->
            conn.autoCommit = false
            try {
                val sql = """
                    INSERT INTO user_sources (user_id, source_name, is_enabled) VALUES (?, ?, ?)
                    ON CONFLICT(user_id, source_name) DO UPDATE SET is_enabled = excluded.is_enabled, updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    for (name in sourceNames) {
                        stmt.setLong(1, userId)
                        stmt.setString(2, name)
                        stmt.setInt(3, if (enabled) 1 else 0)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun getUserSource(userId: Long): String {
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT active_source FROM users WHERE user_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val src = rs.getString("active_source")
                    if (!src.isNullOrBlank() && isSourceEnabled(userId, src)) {
                        return src
                    }
                }
            }
        }
        val enabled = getEnabledSources(userId)
        val defaultSrc = "KissKH"
        return enabled.firstOrNull { it.equals(defaultSrc, true) } ?: enabled.firstOrNull() ?: "KissKH"
    }

    fun setUserSource(userId: Long, source: String) {
        setSourceEnabled(userId, source, true)
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO users (user_id, active_source) VALUES (?, ?)
                ON CONFLICT(user_id) DO UPDATE SET active_source = excluded.active_source
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, source)
                stmt.executeUpdate()
            }
        }
    }

    fun addBookmark(userId: Long, provider: String, mediaUrl: String, title: String, posterUrl: String) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT OR IGNORE INTO bookmarks (user_id, provider, media_url, title, poster_url)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, provider)
                stmt.setString(3, mediaUrl)
                stmt.setString(4, title)
                stmt.setString(5, posterUrl)
                stmt.executeUpdate()
            }
        }
    }

    fun removeBookmark(userId: Long, mediaUrl: String) {
        DriverManager.getConnection(url).use { conn ->
            val sql = "DELETE FROM bookmarks WHERE user_id = ? AND media_url = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, mediaUrl)
                stmt.executeUpdate()
            }
        }
    }

    fun isBookmarked(userId: Long, mediaUrl: String): Boolean {
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT 1 FROM bookmarks WHERE user_id = ? AND media_url = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, mediaUrl)
                val rs = stmt.executeQuery()
                return rs.next()
            }
        }
    }

    fun getBookmarks(userId: Long): List<Bookmark> {
        val list = mutableListOf<Bookmark>()
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT * FROM bookmarks WHERE user_id = ? ORDER BY created_at DESC LIMIT 30"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        Bookmark(
                            id = rs.getInt("id"),
                            userId = rs.getLong("user_id"),
                            provider = rs.getString("provider"),
                            mediaUrl = rs.getString("media_url"),
                            title = rs.getString("title"),
                            posterUrl = rs.getString("poster_url") ?: ""
                        )
                    )
                }
            }
        }
        return list
    }

    fun getTotalUsers(): Int {
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM users")
                if (rs.next()) return rs.getInt(1)
            }
        }
        return 0
    }

    fun getTotalBookmarks(): Int {
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM bookmarks")
                if (rs.next()) return rs.getInt(1)
            }
        }
        return 0
    }

    fun getSetting(key: String, defaultValue: String = ""): String {
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT value FROM app_settings WHERE key = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getString("value") ?: defaultValue
                }
            }
        }
        return defaultValue
    }

    fun setSetting(key: String, value: String) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO app_settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        }
    }

    fun isNsfwEnabled(): Boolean {
        return getSetting("nsfw_enabled", "false").toBoolean()
    }

    fun setNsfwEnabled(enabled: Boolean) {
        setSetting("nsfw_enabled", enabled.toString())
    }

    fun recordWatch(
        userId: Long,
        provider: String,
        mediaUrl: String,
        title: String,
        posterUrl: String? = null,
        episodeTitle: String? = null,
        episodeData: String? = null
    ) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO watch_history (user_id, provider, media_url, title, poster_url, episode_title, episode_data, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(user_id, media_url) DO UPDATE SET
                    provider = excluded.provider,
                    title = excluded.title,
                    poster_url = COALESCE(excluded.poster_url, watch_history.poster_url),
                    episode_title = COALESCE(excluded.episode_title, watch_history.episode_title),
                    episode_data = COALESCE(excluded.episode_data, watch_history.episode_data),
                    updated_at = CURRENT_TIMESTAMP;
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, provider)
                stmt.setString(3, mediaUrl)
                stmt.setString(4, title)
                stmt.setString(5, posterUrl)
                stmt.setString(6, episodeTitle)
                stmt.setString(7, episodeData)
                stmt.executeUpdate()
            }
        }
    }

    fun getWatchHistory(userId: Long, limit: Int = 10): List<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT * FROM watch_history WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setInt(2, limit)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        WatchHistoryItem(
                            id = rs.getInt("id"),
                            userId = rs.getLong("user_id"),
                            provider = rs.getString("provider"),
                            mediaUrl = rs.getString("media_url"),
                            title = rs.getString("title"),
                            posterUrl = rs.getString("poster_url"),
                            episodeTitle = rs.getString("episode_title"),
                            episodeData = rs.getString("episode_data"),
                            updatedAt = rs.getString("updated_at")
                        )
                    )
                }
            }
        }
        return list
    }

    fun getLastWatched(userId: Long): WatchHistoryItem? {
        return getWatchHistory(userId, limit = 1).firstOrNull()
    }

    fun clearWatchHistory(userId: Long): Boolean {
        return DriverManager.getConnection(url).use { conn ->
            val sql = "DELETE FROM watch_history WHERE user_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeUpdate() > 0
            }
        }
    }

    fun isRepoEnabled(repoIdOrName: String): Boolean {
        if (repoIdOrName.isBlank()) return true
        val clean = repoIdOrName.trim().lowercase()
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT is_enabled FROM admin_repos WHERE LOWER(repo_id) = ? OR LOWER(repo_name) = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, clean)
                stmt.setString(2, clean)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return rs.getInt("is_enabled") == 1
                }
            }
        }
        // Repositories are enabled by default until an admin explicitly toggles them off
        return true
    }

    fun setRepoEnabled(repoId: String, repoName: String, enabled: Boolean) {
        DriverManager.getConnection(url).use { conn ->
            val sql = """
                INSERT INTO admin_repos (repo_id, repo_name, is_enabled) VALUES (?, ?, ?)
                ON CONFLICT(repo_id) DO UPDATE SET is_enabled = excluded.is_enabled, repo_name = excluded.repo_name, updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, repoId)
                stmt.setString(2, repoName)
                stmt.setInt(3, if (enabled) 1 else 0)
                stmt.executeUpdate()
            }
        }
    }

    fun setAllReposEnabled(enabled: Boolean) {
        val allRepos = com.telestream.repo.CloudStreamRepoManager.DEFAULT_REPOS
        DriverManager.getConnection(url).use { conn ->
            conn.autoCommit = false
            try {
                val sql = """
                    INSERT INTO admin_repos (repo_id, repo_name, is_enabled) VALUES (?, ?, ?)
                    ON CONFLICT(repo_id) DO UPDATE SET is_enabled = excluded.is_enabled, repo_name = excluded.repo_name, updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    for (r in allRepos) {
                        stmt.setString(1, r.id)
                        stmt.setString(2, r.name)
                        stmt.setInt(3, if (enabled) 1 else 0)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun getAdminRepos(): List<AdminRepoRecord> {
        val allDefault = com.telestream.repo.CloudStreamRepoManager.DEFAULT_REPOS
        val statusMap = mutableMapOf<String, Boolean>()
        DriverManager.getConnection(url).use { conn ->
            val sql = "SELECT repo_id, is_enabled FROM admin_repos"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    statusMap[rs.getString("repo_id").lowercase()] = rs.getInt("is_enabled") == 1
                }
            }
        }
        return allDefault.map { repo ->
            AdminRepoRecord(
                repoId = repo.id,
                repoName = repo.name,
                url = repo.url,
                description = repo.description,
                isEnabled = statusMap[repo.id.lowercase()] ?: true
            )
        }
    }
}

data class AdminRepoRecord(
    val repoId: String,
    val repoName: String,
    val url: String,
    val description: String,
    val isEnabled: Boolean
)
