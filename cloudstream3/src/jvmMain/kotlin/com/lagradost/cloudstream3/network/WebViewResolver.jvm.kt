package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.nicehttp.requestCreator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 * */
actual class WebViewResolver actual constructor(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex>,
    val userAgent: String?,
    val useOkhttp: Boolean,
    val script: String?,
    val scriptCallback: ((String) -> Unit)?,
    val timeout: Long
) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request)
    }

    actual companion object {
        actual val DEFAULT_TIMEOUT = 60_000L
        actual var webViewUserAgent: String? = null
    }

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    actual suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val targetUrl = request.url.toString()
        val additionalMatches = mutableListOf<Request>()
        var mainIntercepted: Request? = null

        // 1. Direct match check on the initial request
        if (interceptUrl.containsMatchIn(targetUrl)) {
            mainIntercepted = request
            if (requestCallBack(request)) {
                return@withContext request to listOf(request)
            }
        }

        val ua = userAgent ?: webViewUserAgent ?: com.lagradost.cloudstream3.USER_AGENT
        val okClient = com.lagradost.cloudstream3.app.baseClient

        try {
            // First check FlareSolverr if reachable
            val flareUrl = System.getProperty("FLARESOLVERR_URL")?.ifBlank { null }
                ?: System.getenv("FLARESOLVERR_URL")?.ifBlank { null }
                ?: "http://localhost:8191/v1"
            var html = ""
            try {
                val conn = java.net.URL(flareUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = timeout.toInt().coerceAtMost(60000)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = org.json.JSONObject().apply {
                    put("cmd", "request.get")
                    put("url", targetUrl)
                    put("maxTimeout", timeout.coerceAtMost(60000))
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                if (conn.responseCode == 200) {
                    val respJson = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (respJson.optString("status") == "ok") {
                        val solution = respJson.optJSONObject("solution")
                        html = solution?.optString("response") ?: ""
                    }
                }
            } catch (_: Exception) {}

            // If FlareSolverr didn't provide HTML, fetch with OkHttp
            if (html.isBlank()) {
                val reqBuilder = request.newBuilder()
                    .header("User-Agent", ua)
                val resp = okClient.newCall(reqBuilder.build()).execute()
                html = resp.body?.string() ?: ""
            }

            // Execute script callback if provided
            if (script != null && scriptCallback != null) {
                try {
                    scriptCallback.invoke(html)
                } catch (_: Exception) {}
            }

            // Extract all URLs matching interceptUrl or additionalUrls from the HTML / text
            val urlRegex = Regex("""https?://[^\s"'<>]+""")
            val foundUrls = urlRegex.findAll(html).map { it.value }.distinct().toList()

            for (u in foundUrls) {
                if (mainIntercepted == null && interceptUrl.containsMatchIn(u)) {
                    val interceptedReq = request.newBuilder().url(u).build()
                    mainIntercepted = interceptedReq
                    additionalMatches.add(interceptedReq)
                    if (requestCallBack(interceptedReq)) {
                        break
                    }
                }
                for (addRegex in additionalUrls) {
                    if (addRegex.containsMatchIn(u)) {
                        val addReq = request.newBuilder().url(u).build()
                        if (!additionalMatches.any { it.url == addReq.url }) {
                            additionalMatches.add(addReq)
                            requestCallBack(addReq)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }

        return@withContext (mainIntercepted ?: request) to additionalMatches
    }
}

