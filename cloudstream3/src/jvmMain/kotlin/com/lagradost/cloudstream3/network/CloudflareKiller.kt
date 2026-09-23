package com.lagradost.cloudstream3.network

import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@AnyThread
open class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val logger = LoggerFactory.getLogger(CloudflareKiller::class.java)
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }

        private val DEFAULT_HEADERS = mapOf("user-agent" to USER_AGENT)

        fun getHeaders(
            headers: Map<String, String>,
            cookie: Map<String, String>
        ): Headers {
            val cookieMap =
                if (cookie.isNotEmpty()) mapOf(
                    "Cookie" to cookie.entries.joinToString("; ") {
                        "${it.key}=${it.value}"
                    }) else mapOf()
            val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap)
            return tempHeaders.toHeaders()
        }
    }

    init {
        safe {
            CookieManager.getInstance()?.removeAllCookies(null)
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()

    /**
     * Gets the headers with cookies, webview user agent included!
     */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        return getHeaders(userAgentHeaders, savedCookies[URI(url).host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()

        when (val cookies = savedCookies[request.url.host]) {
            null -> {
                val response = chain.proceed(request)
                val server = response.header("Server")?.lowercase() ?: ""
                val isCf = (server in CLOUDFLARE_SERVERS || server.contains("cloudflare")) && response.code in ERROR_CODES
                if (!isCf) {
                    return@runBlocking response
                } else {
                    response.close()
                    bypassCloudflare(request)?.let {
                        logger.info("[$TAG] Succeeded bypassing Cloudflare for: ${request.url}")
                        return@runBlocking it
                    }
                }
            }
            else -> {
                return@runBlocking proceed(request, cookies)
            }
        }

        logger.warn("[$TAG] Cloudflare challenge failed or unsolved for: ${request.url}")
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return safe {
            CookieManager.getInstance()?.getCookie(url)
        }
    }

    private fun trySolveWithSavedCookies(request: Request): Boolean {
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            cookie.contains("cf_clearance").also { solved ->
                if (solved) savedCookies[request.url.host] = parseCookieMap(cookie)
            }
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val userAgent = WebViewResolver.webViewUserAgent
        val userAgentMap = if (userAgent != null) {
            mapOf("user-agent" to userAgent)
        } else {
            emptyMap()
        }

        val headers =
            getHeaders(request.headers.toMap() + userAgentMap, cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // 1. Try saved cookies first
        if (trySolveWithSavedCookies(request)) {
            val cookies = savedCookies[request.url.host]
            if (cookies != null) return proceed(request, cookies)
        }

        // 2. Try FlareSolverr on-demand ONLY when faced with Cloudflare challenge
        try {
            val solverClass = Class.forName("com.telestream.network.FlareSolverrManager")
            val method = solverClass.getMethod("solveOnDemand", String::class.java)
            val solution = method.invoke(null, url)
            if (solution != null) {
                val cookiesMethod = solution.javaClass.getDeclaredMethod("getCookies")
                @Suppress("UNCHECKED_CAST")
                val cookieMap = cookiesMethod.invoke(solution) as? Map<String, String>
                val uaMethod = solution.javaClass.getDeclaredMethod("getUserAgent")
                val ua = uaMethod.invoke(solution) as? String
                if (!cookieMap.isNullOrEmpty()) {
                    savedCookies[request.url.host] = cookieMap
                    if (!ua.isNullOrBlank()) {
                        WebViewResolver.webViewUserAgent = ua
                    }
                    return proceed(request, cookieMap)
                }
            }
        } catch (_: Throwable) {
            // FlareSolverr not available or failed; fall through to WebViewResolver
        }

        // 3. Fallback to WebViewResolver
        logger.info("[$TAG] Solving Cloudflare via WebViewResolver for $url")
        try {
            WebViewResolver(
                Regex(".^"),
                additionalUrls = listOf(Regex(".")),
                userAgent = null,
                useOkhttp = false
            ).resolveUsingWebView(url) {
                trySolveWithSavedCookies(request)
            }
        } catch (e: Throwable) {
            logError(e)
        }

        val cookies = savedCookies[request.url.host] ?: return null
        return proceed(request, cookies)
    }
}
