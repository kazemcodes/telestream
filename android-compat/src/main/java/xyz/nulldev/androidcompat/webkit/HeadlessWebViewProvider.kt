package xyz.nulldev.androidcompat.webkit

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintDocumentAdapter
import android.util.Log
import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowInsets
import android.webkit.*
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.PictureListener
import android.webkit.WebView.VisualStateCallback
import android.webkit.WebViewProvider.ScrollDelegate
import android.webkit.WebViewProvider.ViewDelegate
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Headless implementation of WebViewProvider for pure JVM execution.
 * Intercepts page loads and Cloudflare challenges without requiring native Chromium/CEF.
 * Dispatches standard Android WebViewClient callbacks so plugins work seamlessly.
 */
class HeadlessWebViewProvider(
    private val view: WebView
) : WebViewProvider {

    private val settings = HeadlessWebSettings()
    private var viewClient = WebViewClient()
    private var chromeClient = WebChromeClient()

    private var currentUrl: String = "about:blank"
    private var originalUrl: String = "about:blank"
    private var currentTitle: String = ""
    private var pageHtml: String = ""
    private var progress: Int = 100

    private val jsInterfaces = ConcurrentHashMap<String, Any>()
    private val handler: Handler by lazy {
        val looper = Looper.myLooper() ?: Looper.getMainLooper() ?: run {
            try { Looper.prepare() } catch (_: Throwable) {}
            Looper.myLooper() ?: Looper.getMainLooper()!!
        }
        Handler(looper)
    }

    private val viewDelegate = HeadlessViewDelegate()
    private val scrollDelegate = HeadlessScrollDelegate()

    companion object {
        private const val TAG = "HeadlessWebView"
        private val executor: Executor = Executors.newCachedThreadPool { r ->
            Thread(r, "HeadlessWebView-Worker").apply { isDaemon = true }
        }

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        // Optional FlareSolverr endpoint if available in environment or system property
        private val flareSolverrUrl: String
            get() = System.getProperty("FLARESOLVERR_URL")?.ifBlank { null }
                ?: System.getenv("FLARESOLVERR_URL")?.ifBlank { null }
                ?: "http://localhost:8191/v1"
    }

    override fun init(javaScriptInterfaces: Map<String, Any>?, privateBrowsing: Boolean) {
        javaScriptInterfaces?.forEach { (name, obj) ->
            jsInterfaces[name] = obj
        }
    }

    override fun setHorizontalScrollbarOverlay(overlay: Boolean) {}
    override fun setVerticalScrollbarOverlay(overlay: Boolean) {}
    override fun overlayHorizontalScrollbar(): Boolean = false
    override fun overlayVerticalScrollbar(): Boolean = false
    override fun getVisibleTitleHeight(): Int = 0
    override fun getCertificate(): SslCertificate? = null
    override fun setCertificate(certificate: SslCertificate?) {}
    override fun savePassword(host: String?, username: String?, password: String?) {}
    override fun setHttpAuthUsernamePassword(host: String?, realm: String?, username: String?, password: String?) {}
    override fun getHttpAuthUsernamePassword(host: String?, realm: String?): Array<String>? = null
    override fun destroy() {}
    override fun setNetworkAvailable(networkUp: Boolean) {}
    override fun saveState(outState: Bundle?): WebBackForwardList? = null
    override fun savePicture(b: Bundle?, dest: File?): Boolean = false
    override fun restorePicture(b: Bundle?, src: File?): Boolean = false
    override fun restoreState(inState: Bundle?): WebBackForwardList? = null

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>?) {
        loadUrlInternal(url, additionalHttpHeaders ?: emptyMap(), null)
    }

    override fun loadUrl(url: String) {
        if (url.startsWith("javascript:")) {
            evaluateJavaScript(url.removePrefix("javascript:"), null)
            return
        }
        loadUrlInternal(url, emptyMap(), null)
    }

    override fun postUrl(url: String, postData: ByteArray) {
        loadUrlInternal(url, emptyMap(), postData)
    }

    override fun loadData(data: String, mimeType: String?, encoding: String?) {
        pageHtml = data
        currentUrl = "data:text/html;charset=utf-8,$data"
        dispatchPageEvents(currentUrl, 200, data)
    }

    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?
    ) {
        pageHtml = data
        currentUrl = baseUrl ?: "about:blank"
        dispatchPageEvents(currentUrl, 200, data)
    }

    private fun loadUrlInternal(url: String, headers: Map<String, String>, postData: ByteArray?) {
        currentUrl = url
        originalUrl = url
        progress = 10

        handler.post {
            viewClient.onPageStarted(view, url, null)
            chromeClient.onProgressChanged(view, 20)
        }

        executor.execute {
            try {
                // 1. Try FlareSolverr if reachable and looks like Cloudflare challenge URL
                val solvedByFlare = tryFlareSolverr(url, postData)
                if (solvedByFlare != null) {
                    val (html, cookies) = solvedByFlare
                    pageHtml = html
                    cookies.forEach { (name, value) ->
                        CookieManager.getInstance().setCookie(url, "$name=$value")
                    }
                    dispatchPageEvents(url, 200, html)
                    return@execute
                }

                // 2. Direct OkHttp Request with browser-like headers & CookieManager integration
                val reqBuilder = Request.Builder().url(url)
                val ua = settings.userAgentString?.ifBlank { null }
                    ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                reqBuilder.header("User-Agent", ua)
                reqBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                reqBuilder.header("Accept-Language", "en-US,en;q=0.9")

                val storedCookies = CookieManager.getInstance().getCookie(url)
                if (!storedCookies.isNullOrBlank()) {
                    reqBuilder.header("Cookie", storedCookies)
                }

                headers.forEach { (k, v) ->
                    reqBuilder.header(k, v)
                }

                if (postData != null) {
                    reqBuilder.post(postData.toRequestBody())
                }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val code = response.code
                val body = response.body?.string() ?: ""
                pageHtml = body

                // Parse and update cookies
                response.headers("Set-Cookie").forEach { cStr ->
                    CookieManager.getInstance().setCookie(url, cStr)
                }

                // Extract title if present in HTML
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(body)
                if (titleMatcher.find()) {
                    currentTitle = titleMatcher.group(1).trim()
                }

                dispatchPageEvents(url, code, body)
            } catch (e: Exception) {
                Log.w(TAG, "Error loading url $url: ${e.message}")
                handler.post {
                    viewClient.onReceivedError(view, WebViewClient.ERROR_CONNECT, e.message ?: "Failed", url)
                    viewClient.onPageFinished(view, url)
                }
            }
        }
    }

    private fun tryFlareSolverr(url: String, postData: ByteArray?): Pair<String, Map<String, String>>? {
        return try {
            val endpoint = URL(flareSolverrUrl)
            val conn = endpoint.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 60000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("cmd", if (postData != null) "request.post" else "request.get")
                put("url", url)
                put("maxTimeout", 60000)
                if (postData != null) {
                    put("postData", String(postData))
                }
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
            }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respText)
                if (json.optString("status") == "ok") {
                    val solution = json.optJSONObject("solution")
                    val responseHtml = solution?.optString("response") ?: ""
                    val cookiesObj = solution?.optJSONArray("cookies")
                    val cookieMap = mutableMapOf<String, String>()
                    if (cookiesObj != null) {
                        for (i in 0 until cookiesObj.length()) {
                            val c = cookiesObj.getJSONObject(i)
                            cookieMap[c.optString("name")] = c.optString("value")
                        }
                    }
                    Log.i(TAG, "FlareSolverr successfully solved challenge for $url")
                    return responseHtml to cookieMap
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun dispatchPageEvents(url: String, code: Int, html: String) {
        progress = 100
        handler.post {
            chromeClient.onProgressChanged(view, 100)
            if (currentTitle.isNotBlank()) {
                chromeClient.onReceivedTitle(view, currentTitle)
            }
            viewClient.onPageFinished(view, url)
        }
    }

    override fun evaluateJavaScript(script: String, resultCallback: ValueCallback<String>?) {
        val cleanScript = script.trim()
        val result: String? = when {
            cleanScript.contains("document.title") -> "\"$currentTitle\""
            cleanScript.contains("document.cookie") -> {
                val c = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                "\"$c\""
            }
            cleanScript.contains("document.documentElement.outerHTML") || cleanScript.contains("document.body.innerHTML") -> {
                "\"${pageHtml.replace("\"", "\\\"").replace("\n", "\\n")}\""
            }
            else -> {
                // If script calls a known interface method, e.g. Interface.callback(...)
                for ((ifaceName, ifaceObj) in jsInterfaces) {
                    if (cleanScript.contains(ifaceName)) {
                        try {
                            val methodMatch = Pattern.compile("$ifaceName\\.([a-zA-Z0-9_]+)\\((.*?)\\)").matcher(cleanScript)
                            if (methodMatch.find()) {
                                val methodName = methodMatch.group(1)
                                val method = ifaceObj::class.java.methods.firstOrNull { it.name == methodName }
                                method?.invoke(ifaceObj)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed invoking JS interface $ifaceName: ${e.message}")
                        }
                    }
                }
                "\"\""
            }
        }
        handler.post {
            resultCallback?.onReceiveValue(result)
        }
    }

    override fun saveWebArchive(filename: String) {}
    override fun saveWebArchive(basename: String, autoname: Boolean, callback: ValueCallback<String>) {
        callback.onReceiveValue(null)
    }

    override fun stopLoading() {}
    override fun reload() { loadUrl(currentUrl) }
    override fun canGoBack(): Boolean = false
    override fun goBack() {}
    override fun canGoForward(): Boolean = false
    override fun goForward() {}
    override fun canGoBackOrForward(steps: Int): Boolean = false
    override fun goBackOrForward(steps: Int) {}
    override fun isPrivateBrowsingEnabled(): Boolean = false
    override fun pageUp(top: Boolean): Boolean = false
    override fun pageDown(bottom: Boolean): Boolean = false
    override fun insertVisualStateCallback(requestId: Long, callback: VisualStateCallback) {}
    override fun clearView() {}
    override fun capturePicture(): Picture = Picture()
    override fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter = throw RuntimeException("Stub!")
    override fun getScale(): Float = 1.0f
    override fun setInitialScale(scaleInPercent: Int) {}
    override fun invokeZoomPicker() {}
    override fun getHitTestResult(): HitTestResult = HitTestResult()
    override fun requestFocusNodeHref(hrefMsg: Message?) {}
    override fun requestImageRef(msg: Message?) {}
    override fun getUrl(): String? = currentUrl
    override fun getOriginalUrl(): String? = originalUrl
    override fun getTitle(): String? = currentTitle
    override fun getFavicon(): Bitmap? = null
    override fun getTouchIconUrl(): String? = null
    override fun getProgress(): Int = progress
    override fun getContentHeight(): Int = 1000
    override fun getContentWidth(): Int = 1000
    override fun pauseTimers() {}
    override fun resumeTimers() {}
    override fun onPause() {}
    override fun onResume() {}
    override fun isPaused(): Boolean = false
    override fun freeMemory() {}
    override fun clearCache(includeDiskFiles: Boolean) {}
    override fun clearFormData() {}
    override fun clearHistory() {}
    override fun clearSslPreferences() {}
    override fun copyBackForwardList(): WebBackForwardList? = null
    override fun setFindListener(listener: WebView.FindListener) {}
    override fun findNext(forward: Boolean) {}
    override fun findAll(find: String): Int = 0
    override fun findAllAsync(find: String) {}
    override fun showFindDialog(text: String?, showIme: Boolean): Boolean = false
    override fun clearMatches() {}
    override fun documentHasImages(response: Message) {}

    override fun setWebViewClient(client: WebViewClient) {
        viewClient = client
    }
    override fun getWebViewClient(): WebViewClient = viewClient

    override fun setDownloadListener(listener: DownloadListener) {}

    override fun setWebChromeClient(client: WebChromeClient) {
        chromeClient = client
    }
    override fun getWebChromeClient(): WebChromeClient = chromeClient

    override fun setPictureListener(listener: PictureListener) {}

    override fun addJavascriptInterface(obj: Any, interfaceName: String) {
        jsInterfaces[interfaceName] = obj
    }

    override fun removeJavascriptInterface(interfaceName: String) {
        jsInterfaces.remove(interfaceName)
    }

    override fun createWebMessageChannel(): Array<WebMessagePort> = emptyArray()

    override fun postMessageToMainFrame(message: WebMessage, targetOrigin: Uri) {}

    override fun getSettings(): WebSettings = settings

    override fun setMapTrackballToArrowKeys(setMap: Boolean) {}
    override fun flingScroll(vx: Int, vy: Int) {}
    override fun getZoomControls(): View? = null
    override fun canZoomIn(): Boolean = false
    override fun canZoomOut(): Boolean = false
    override fun zoomBy(zoomFactor: Float): Boolean = false
    override fun zoomIn(): Boolean = false
    override fun zoomOut(): Boolean = false
    override fun dumpViewHierarchyWithProperties(out: BufferedWriter, level: Int) {}
    override fun findHierarchyView(className: String, hashCode: Int): View? = null
    override fun setRendererPriorityPolicy(rendererRequestedPriority: Int, waivedWhenNotVisible: Boolean) {}
    override fun getRendererRequestedPriority(): Int = 0
    override fun getRendererPriorityWaivedWhenNotVisible(): Boolean = false
    override fun setTextClassifier(textClassifier: android.view.textclassifier.TextClassifier?) {}
    override fun getTextClassifier(): android.view.textclassifier.TextClassifier = android.view.textclassifier.TextClassifier.NO_OP
    override fun getViewDelegate(): ViewDelegate = viewDelegate
    override fun getScrollDelegate(): ScrollDelegate = scrollDelegate
    override fun notifyFindDialogDismissed() {}

    override fun getWebViewRenderProcess(): WebViewRenderProcess? = null
    override fun setWebViewRenderProcessClient(executor: Executor?, client: WebViewRenderProcessClient?) {}
    override fun getWebViewRenderProcessClient(): WebViewRenderProcessClient? = null

    class HeadlessViewDelegate : ViewDelegate {
        override fun shouldDelayChildPressedState(): Boolean = false
        override fun onProvideVirtualStructure(structure: android.view.ViewStructure) {}
        override fun onProvideAutofillVirtualStructure(structure: android.view.ViewStructure, flags: Int) {}
        override fun autofill(values: android.util.SparseArray<android.view.autofill.AutofillValue>) {}
        override fun isVisibleToUserForAutofill(virtualId: Int): Boolean = true
        override fun onProvideContentCaptureStructure(structure: android.view.ViewStructure, flags: Int) {}
        override fun getAccessibilityNodeProvider(): android.view.accessibility.AccessibilityNodeProvider? = null
        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {}
        override fun onInitializeAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) {}
        override fun performAccessibilityAction(action: Int, arguments: Bundle): Boolean = false
        override fun setOverScrollMode(mode: Int) {}
        override fun setScrollBarStyle(style: Int) {}
        override fun onDrawVerticalScrollBar(canvas: Canvas, scrollBar: Drawable, l: Int, t: Int, r: Int, b: Int) {}
        override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {}
        override fun onWindowVisibilityChanged(visibility: Int) {}
        override fun onDraw(canvas: Canvas) {}
        override fun setLayoutParams(layoutParams: LayoutParams) {}
        override fun performLongClick(): Boolean = false
        override fun onConfigurationChanged(newConfig: Configuration) {}
        override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? = null
        override fun onDragEvent(event: DragEvent): Boolean = false
        override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = false
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
        override fun onAttachedToWindow() {}
        override fun onDetachedFromWindow() {}
        override fun onMovedToDisplay(displayId: Int, config: Configuration) {}
        override fun onVisibilityChanged(changedView: View, visibility: Int) {}
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {}
        override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect) {}
        override fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean = false
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {}
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {}
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = false
        override fun onTouchEvent(ev: MotionEvent): Boolean = false
        override fun onHoverEvent(event: MotionEvent): Boolean = false
        override fun onGenericMotionEvent(event: MotionEvent): Boolean = false
        override fun onTrackballEvent(ev: MotionEvent): Boolean = false
        override fun requestFocus(direction: Int, previouslyFocusedRect: Rect): Boolean = false
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {}
        override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean = false
        override fun setBackgroundColor(color: Int) {}
        override fun setLayerType(layerType: Int, paint: Paint) {}
        override fun preDispatchDraw(canvas: Canvas) {}
        override fun onStartTemporaryDetach() {}
        override fun onFinishTemporaryDetach() {}
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent) {}
        override fun getHandler(originalHandler: Handler): Handler = originalHandler
        override fun findFocus(originalFocusedView: View): View = originalFocusedView
        override fun onCheckIsTextEditor(): Boolean = false
        override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets? = insets
        override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): android.view.PointerIcon? = null
    }

    class HeadlessScrollDelegate : ScrollDelegate {
        override fun computeHorizontalScrollRange(): Int = 0
        override fun computeHorizontalScrollOffset(): Int = 0
        override fun computeVerticalScrollRange(): Int = 0
        override fun computeVerticalScrollOffset(): Int = 0
        override fun computeVerticalScrollExtent(): Int = 0
        override fun computeScroll() {}
    }
}
