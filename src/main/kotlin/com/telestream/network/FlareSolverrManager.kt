package com.telestream.network

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

object FlareSolverrManager {
    private val logger = LoggerFactory.getLogger(FlareSolverrManager::class.java)
    const val DEFAULT_PORT = 8191
    val defaultUrl: String
        get() = System.getProperty("FLARESOLVERR_URL")?.ifBlank { null }
            ?: System.getenv("FLARESOLVERR_URL")?.ifBlank { null }
            ?: "http://localhost:$DEFAULT_PORT/v1"

    @Volatile
    private var process: Process? = null

    @Volatile
    private var isDownloading = false

    fun isAvailable(): Boolean {
        return try {
            val rootUrl = defaultUrl.removeSuffix("/v1").removeSuffix("/") + "/"
            val conn = URI(rootUrl).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            code in 200..499
        } catch (_: Exception) {
            false
        }
    }

    data class Solution(
        val cookies: Map<String, String>,
        val userAgent: String?,
        val responseHtml: String?
    )

    @JvmStatic
    fun solveOnDemand(url: String): Solution? {
        val running = runBlocking { ensureRunning() }
        if (!running) return null

        return try {
            val endpoint = URI(defaultUrl).toURL()
            val conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json")

            val payload = """{"cmd":"request.get","url":"$url","maxTimeout":60000}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = com.lagradost.cloudstream3.mapper.readTree(jsonStr)
                val status = root.get("status")?.asText()
                if (status == "ok") {
                    val solution = root.get("solution")
                    val cookiesList = solution?.get("cookies")
                    val cookieMap = mutableMapOf<String, String>()
                    if (cookiesList != null && cookiesList.isArray) {
                        for (c in cookiesList) {
                            val name = c.get("name")?.asText()
                            val value = c.get("value")?.asText()
                            if (name != null && value != null) {
                                cookieMap[name] = value
                            }
                        }
                    }
                    val ua = solution?.get("userAgent")?.asText()
                    val html = solution?.get("response")?.asText()
                    logger.info("✅ FlareSolverr solved challenge on demand for: $url (found ${cookieMap.size} cookies)")
                    return Solution(cookieMap, ua, html)
                }
            }
            null
        } catch (e: Exception) {
            logger.warn("FlareSolverr solve on demand failed: ${e.message}")
            null
        }
    }

    fun startAsync(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        scope.launch {
            try {
                ensureRunning()
            } catch (e: Throwable) {
                logger.warn("FlareSolverr auto-starter encountered an issue: ${e.message}")
            }
        }
    }

    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        if (isAvailable()) {
            logger.info("FlareSolverr is already running and accessible at $defaultUrl")
            System.setProperty("FLARESOLVERR_URL", defaultUrl)
            return@withContext true
        }

        val baseDir = File("data/flaresolverr").apply { mkdirs() }
        val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")
        val exeName = if (isWindows) "flaresolverr.exe" else "flaresolverr"

        var exe = findExecutable(baseDir, exeName)
        if (exe == null && !isDownloading) {
            isDownloading = true
            try {
                logger.info("FlareSolverr binary not found locally. Starting automatic download...")
                val downloadUrl = if (isWindows) {
                    "https://github.com/FlareSolverr/FlareSolverr/releases/latest/download/flaresolverr_windows_x64.zip"
                } else {
                    "https://github.com/FlareSolverr/FlareSolverr/releases/latest/download/flaresolverr_linux_x64.tar.gz"
                }

                val archiveFile = File("data", "flaresolverr_pkg.tmp")
                downloadFile(downloadUrl, archiveFile)
                logger.info("FlareSolverr archive downloaded (${archiveFile.length() / 1024 / 1024} MB). Extracting...")

                if (isWindows) {
                    extractZip(archiveFile, baseDir)
                } else {
                    // Tar extraction via tar command or fallback
                    ProcessBuilder("tar", "-xzf", archiveFile.absolutePath, "-C", baseDir.absolutePath).start().waitFor()
                }
                archiveFile.delete()
                logger.info("FlareSolverr extracted successfully.")
            } catch (e: Throwable) {
                logger.warn("Failed to automatically download FlareSolverr: ${e.message}")
            } finally {
                isDownloading = false
            }
            exe = findExecutable(baseDir, exeName)
        }

        if (exe == null || !exe.exists()) {
            logger.warn("FlareSolverr executable ($exeName) is not available.")
            return@withContext false
        }

        try {
            exe.setExecutable(true)
            logger.info("Launching FlareSolverr daemon from: ${exe.absolutePath}...")
            val pb = ProcessBuilder(exe.absolutePath)
                .directory(exe.parentFile)
                .redirectErrorStream(true)

            pb.environment()["PORT"] = DEFAULT_PORT.toString()
            pb.environment()["LOG_LEVEL"] = "info"

            val proc = pb.start()
            process = proc

            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    proc.destroyForcibly()
                } catch (_: Throwable) {}
            })

            // Wait up to 20 seconds for FlareSolverr to bind port and answer
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 20_000) {
                delay(1000)
                if (isAvailable()) {
                    System.setProperty("FLARESOLVERR_URL", defaultUrl)
                    logger.info("✅ FlareSolverr daemon successfully started and ready at $defaultUrl")
                    return@withContext true
                }
                if (!proc.isAlive) {
                    val exit = proc.exitValue()
                    logger.warn("FlareSolverr process terminated prematurely with exit code: $exit")
                    return@withContext false
                }
            }
        } catch (e: Throwable) {
            logger.warn("Failed to launch FlareSolverr process: ${e.message}")
        }

        val available = isAvailable()
        if (available) {
            System.setProperty("FLARESOLVERR_URL", defaultUrl)
        }
        return@withContext available
    }

    private fun findExecutable(dir: File, name: String): File? {
        val direct = File(dir, name)
        if (direct.exists() && direct.isFile) return direct
        val subDir = File(dir, "flaresolverr")
        val inSubDir = File(subDir, name)
        if (inSubDir.exists() && inSubDir.isFile) return inSubDir
        return dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
    }

    private fun downloadFile(urlStr: String, destination: File) {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val conn = URI(currentUrl).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "TeleStream-FlareSolverr-Manager/1.0")

            when (conn.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                307, 308 -> {
                    val location = conn.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use { input ->
                        FileOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return
                }
                else -> throw IllegalStateException("HTTP ${conn.responseCode} while downloading FlareSolverr from $currentUrl")
            }
        }
    }

    private fun extractZip(zipFile: File, outputDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
