package android.webkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintDocumentAdapter
import android.util.AttributeSet
import android.util.SparseArray
import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.textclassifier.TextClassifier
import xyz.nulldev.androidcompat.CallableArgument
import xyz.nulldev.androidcompat.webkit.HeadlessWebViewProvider
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor

// -----------------------------------------------------------------------------
// WebView
// -----------------------------------------------------------------------------

open class WebView @JvmOverloads constructor(
    context: Context? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    privateBrowsing: Boolean = false
) {
    companion object {
        private var mProviderFactory: CallableArgument<WebView, WebViewProvider>? = null

        @JvmStatic
        fun setProviderFactory(factory: CallableArgument<WebView, WebViewProvider>?) {
            mProviderFactory = factory
        }

        @JvmStatic
        fun setWebContentsDebuggingEnabled(enabled: Boolean) {}

        @JvmStatic
        fun findAddress(addr: String?): String? = null
    }

    private val mProvider: WebViewProvider = mProviderFactory?.call(this) ?: HeadlessWebViewProvider(this)

    val webViewLooper: Looper = Looper.myLooper() ?: Looper.getMainLooper() ?: run {
        try { Looper.prepareMainLooper() } catch (_: Throwable) {}
        Looper.getMainLooper() ?: Looper.myLooper() ?: Looper.getMainLooper()!!
    }

    class HitTestResult {
        var type: Int = 0
        var extra: String? = null
        companion object {
            const val UNKNOWN_TYPE = 0
            const val ANCHOR_TYPE = 1
            const val PHONE_TYPE = 2
            const val GEO_TYPE = 3
            const val EMAIL_TYPE = 4
            const val IMAGE_TYPE = 5
            const val IMAGE_ANCHOR_TYPE = 6
            const val SRC_ANCHOR_TYPE = 7
            const val SRC_IMAGE_ANCHOR_TYPE = 8
            const val EDIT_TEXT_TYPE = 9
        }
    }

    fun interface PictureListener {
        fun onNewPicture(view: WebView, picture: Picture?)
    }

    fun interface VisualStateCallback {
        fun onComplete(requestId: Long)
    }

    fun interface FindListener {
        fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean)
    }

    class WebViewTransport {
        var webView: WebView? = null
    }

    var webViewClient: WebViewClient
        get() = mProvider.getWebViewClient()
        set(client) { mProvider.setWebViewClient(client) }

    var webChromeClient: WebChromeClient?
        get() = mProvider.getWebChromeClient()
        set(client) { if (client != null) mProvider.setWebChromeClient(client) }

    val settings: WebSettings
        get() = mProvider.getSettings()

    var downloadListener: DownloadListener? = null
        set(listener) {
            field = listener
            if (listener != null) mProvider.setDownloadListener(listener)
        }

    val url: String?
        get() = mProvider.getUrl()

    val originalUrl: String?
        get() = mProvider.getOriginalUrl()

    val title: String?
        get() = mProvider.getTitle()

    val favicon: Bitmap?
        get() = mProvider.getFavicon()

    val progress: Int
        get() = mProvider.getProgress()

    val contentHeight: Int
        get() = mProvider.getContentHeight()

    val contentWidth: Int
        get() = mProvider.getContentWidth()

    val scale: Float
        get() = mProvider.getScale()

    val certificate: SslCertificate?
        get() = mProvider.getCertificate()

    fun loadUrl(url: String) {
        mProvider.loadUrl(url)
    }

    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        mProvider.loadUrl(url, additionalHttpHeaders)
    }

    fun postUrl(url: String, postData: ByteArray) {
        mProvider.postUrl(url, postData)
    }

    fun loadData(data: String, mimeType: String?, encoding: String?) {
        mProvider.loadData(data, mimeType, encoding)
    }

    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        mProvider.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        mProvider.evaluateJavaScript(script, resultCallback)
    }

    fun stopLoading() {
        mProvider.stopLoading()
    }

    fun reload() {
        mProvider.reload()
    }

    fun canGoBack(): Boolean = mProvider.canGoBack()

    fun goBack() {
        mProvider.goBack()
    }

    fun canGoForward(): Boolean = mProvider.canGoForward()

    fun goForward() {
        mProvider.goForward()
    }

    fun canGoBackOrForward(steps: Int): Boolean = mProvider.canGoBackOrForward(steps)

    fun goBackOrForward(steps: Int) {
        mProvider.goBackOrForward(steps)
    }

    fun clearCache(includeDiskFiles: Boolean) {
        mProvider.clearCache(includeDiskFiles)
    }

    fun clearFormData() {
        mProvider.clearFormData()
    }

    fun clearHistory() {
        mProvider.clearHistory()
    }

    fun clearSslPreferences() {
        mProvider.clearSslPreferences()
    }

    fun clearView() {
        mProvider.clearView()
    }

    fun clearMatches() {
        mProvider.clearMatches()
    }

    fun destroy() {
        mProvider.destroy()
    }

    fun pauseTimers() {
        mProvider.pauseTimers()
    }

    fun resumeTimers() {
        mProvider.resumeTimers()
    }

    fun onPause() {
        mProvider.onPause()
    }

    fun onResume() {
        mProvider.onResume()
    }

    fun isPaused(): Boolean = mProvider.isPaused()

    fun freeMemory() {
        mProvider.freeMemory()
    }

    fun addJavascriptInterface(obj: Any, interfaceName: String) {
        mProvider.addJavascriptInterface(obj, interfaceName)
    }

    fun removeJavascriptInterface(interfaceName: String) {
        mProvider.removeJavascriptInterface(interfaceName)
    }

    fun createWebMessageChannel(): Array<WebMessagePort> = mProvider.createWebMessageChannel()

    fun postMessageToMainFrame(message: WebMessage, targetOrigin: Uri) {
        mProvider.postMessageToMainFrame(message, targetOrigin)
    }

    fun saveWebArchive(filename: String) {
        mProvider.saveWebArchive(filename)
    }

    fun saveWebArchive(basename: String, autoname: Boolean, callback: ValueCallback<String>) {
        mProvider.saveWebArchive(basename, autoname, callback)
    }

    fun setInitialScale(scaleInPercent: Int) {
        mProvider.setInitialScale(scaleInPercent)
    }

    fun setNetworkAvailable(networkUp: Boolean) {
        mProvider.setNetworkAvailable(networkUp)
    }

    fun saveState(outState: Bundle): WebBackForwardList? = mProvider.saveState(outState)

    fun restoreState(inState: Bundle): WebBackForwardList? = mProvider.restoreState(inState)

    fun capturePicture(): Picture = mProvider.capturePicture()

    fun createPrintDocumentAdapter(documentName: String = "default"): PrintDocumentAdapter = mProvider.createPrintDocumentAdapter(documentName)

    fun hitTestResult(): HitTestResult = mProvider.getHitTestResult()

    fun getHitTestResult(): HitTestResult = mProvider.getHitTestResult()

    fun requestFocusNodeHref(hrefMsg: Message?) {
        mProvider.requestFocusNodeHref(hrefMsg)
    }

    fun requestImageRef(msg: Message?) {
        mProvider.requestImageRef(msg)
    }

    fun setFindListener(listener: FindListener) {
        mProvider.setFindListener(listener)
    }

    fun findNext(forward: Boolean) {
        mProvider.findNext(forward)
    }

    fun findAll(find: String): Int = mProvider.findAll(find)

    fun findAllAsync(find: String) {
        mProvider.findAllAsync(find)
    }

    fun showFindDialog(text: String?, showIme: Boolean): Boolean = mProvider.showFindDialog(text, showIme)

    fun documentHasImages(response: Message) {
        mProvider.documentHasImages(response)
    }

    fun copyBackForwardList(): WebBackForwardList? = mProvider.copyBackForwardList()

    fun setRendererPriorityPolicy(rendererRequestedPriority: Int, waivedWhenNotVisible: Boolean) {
        mProvider.setRendererPriorityPolicy(rendererRequestedPriority, waivedWhenNotVisible)
    }

    fun getRendererRequestedPriority(): Int = mProvider.getRendererRequestedPriority()

    fun getRendererPriorityWaivedWhenNotVisible(): Boolean = mProvider.getRendererPriorityWaivedWhenNotVisible()

    fun postVisualStateCallback(requestId: Long, callback: VisualStateCallback) {
        mProvider.insertVisualStateCallback(requestId, callback)
    }
}

// -----------------------------------------------------------------------------
// WebViewProvider
// -----------------------------------------------------------------------------

interface WebViewProvider {
    fun init(javaScriptInterfaces: Map<String, Any>?, privateBrowsing: Boolean)
    fun setHorizontalScrollbarOverlay(overlay: Boolean)
    fun setVerticalScrollbarOverlay(overlay: Boolean)
    fun overlayHorizontalScrollbar(): Boolean
    fun overlayVerticalScrollbar(): Boolean
    fun getVisibleTitleHeight(): Int
    fun getCertificate(): SslCertificate?
    fun setCertificate(certificate: SslCertificate?)
    fun savePassword(host: String?, username: String?, password: String?)
    fun setHttpAuthUsernamePassword(host: String?, realm: String?, username: String?, password: String?)
    fun getHttpAuthUsernamePassword(host: String?, realm: String?): Array<String>?
    fun destroy()
    fun setNetworkAvailable(networkUp: Boolean)
    fun saveState(outState: Bundle?): WebBackForwardList?
    fun savePicture(b: Bundle?, dest: File?): Boolean
    fun restorePicture(b: Bundle?, src: File?): Boolean
    fun restoreState(inState: Bundle?): WebBackForwardList?
    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>?)
    fun loadUrl(url: String)
    fun postUrl(url: String, postData: ByteArray)
    fun loadData(data: String, mimeType: String?, encoding: String?)
    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?)
    fun evaluateJavaScript(script: String, resultCallback: ValueCallback<String>?)
    fun saveWebArchive(filename: String)
    fun saveWebArchive(basename: String, autoname: Boolean, callback: ValueCallback<String>)
    fun stopLoading()
    fun reload()
    fun canGoBack(): Boolean
    fun goBack()
    fun canGoForward(): Boolean
    fun goForward()
    fun canGoBackOrForward(steps: Int): Boolean
    fun goBackOrForward(steps: Int)
    fun isPrivateBrowsingEnabled(): Boolean
    fun pageUp(top: Boolean): Boolean
    fun pageDown(bottom: Boolean): Boolean
    fun insertVisualStateCallback(requestId: Long, callback: WebView.VisualStateCallback)
    fun clearView()
    fun capturePicture(): Picture
    fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter
    fun getScale(): Float
    fun setInitialScale(scaleInPercent: Int)
    fun invokeZoomPicker()
    fun getHitTestResult(): WebView.HitTestResult
    fun requestFocusNodeHref(hrefMsg: Message?)
    fun requestImageRef(msg: Message?)
    fun getUrl(): String?
    fun getOriginalUrl(): String?
    fun getTitle(): String?
    fun getFavicon(): Bitmap?
    fun getTouchIconUrl(): String?
    fun getProgress(): Int
    fun getContentHeight(): Int
    fun getContentWidth(): Int
    fun pauseTimers()
    fun resumeTimers()
    fun onPause()
    fun onResume()
    fun isPaused(): Boolean
    fun freeMemory()
    fun clearCache(includeDiskFiles: Boolean)
    fun clearFormData()
    fun clearHistory()
    fun clearSslPreferences()
    fun copyBackForwardList(): WebBackForwardList?
    fun setFindListener(listener: WebView.FindListener)
    fun findNext(forward: Boolean)
    fun findAll(find: String): Int
    fun findAllAsync(find: String)
    fun showFindDialog(text: String?, showIme: Boolean): Boolean
    fun clearMatches()
    fun documentHasImages(response: Message)
    fun setWebViewClient(client: WebViewClient)
    fun getWebViewClient(): WebViewClient
    fun getWebViewRenderProcess(): WebViewRenderProcess?
    fun setWebViewRenderProcessClient(executor: Executor?, client: WebViewRenderProcessClient?)
    fun getWebViewRenderProcessClient(): WebViewRenderProcessClient?
    fun setDownloadListener(listener: DownloadListener)
    fun setWebChromeClient(client: WebChromeClient)
    fun getWebChromeClient(): WebChromeClient
    fun setPictureListener(listener: WebView.PictureListener)
    fun addJavascriptInterface(obj: Any, interfaceName: String)
    fun removeJavascriptInterface(interfaceName: String)
    fun createWebMessageChannel(): Array<WebMessagePort>
    fun postMessageToMainFrame(message: WebMessage, targetOrigin: Uri)
    fun getSettings(): WebSettings
    fun setMapTrackballToArrowKeys(setMap: Boolean)
    fun flingScroll(vx: Int, vy: Int)
    fun getZoomControls(): View?
    fun canZoomIn(): Boolean
    fun canZoomOut(): Boolean
    fun zoomBy(zoomFactor: Float): Boolean
    fun zoomIn(): Boolean
    fun zoomOut(): Boolean
    fun dumpViewHierarchyWithProperties(out: BufferedWriter, level: Int)
    fun findHierarchyView(className: String, hashCode: Int): View?
    fun setRendererPriorityPolicy(rendererRequestedPriority: Int, waivedWhenNotVisible: Boolean)
    fun getRendererRequestedPriority(): Int
    fun getRendererPriorityWaivedWhenNotVisible(): Boolean
    fun setTextClassifier(textClassifier: TextClassifier?)
    fun getTextClassifier(): TextClassifier
    fun getViewDelegate(): ViewDelegate
    fun getScrollDelegate(): ScrollDelegate
    fun notifyFindDialogDismissed()

    interface ViewDelegate {
        fun shouldDelayChildPressedState(): Boolean
        fun onProvideVirtualStructure(structure: android.view.ViewStructure)
        fun onProvideAutofillVirtualStructure(structure: android.view.ViewStructure, flags: Int) {}
        fun autofill(values: SparseArray<AutofillValue>) {}
        fun isVisibleToUserForAutofill(virtualId: Int): Boolean = true
        fun onProvideContentCaptureStructure(structure: android.view.ViewStructure, flags: Int) {}
        fun getAccessibilityNodeProvider(): AccessibilityNodeProvider?
        fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo)
        fun onInitializeAccessibilityEvent(event: AccessibilityEvent)
        fun performAccessibilityAction(action: Int, arguments: Bundle): Boolean
        fun setOverScrollMode(mode: Int)
        fun setScrollBarStyle(style: Int)
        fun onDrawVerticalScrollBar(canvas: Canvas, scrollBar: Drawable, l: Int, t: Int, r: Int, b: Int)
        fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean)
        fun onWindowVisibilityChanged(visibility: Int)
        fun onDraw(canvas: Canvas)
        fun setLayoutParams(layoutParams: LayoutParams)
        fun performLongClick(): Boolean
        fun onConfigurationChanged(newConfig: android.content.res.Configuration)
        fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection?
        fun onDragEvent(event: DragEvent): Boolean
        fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean
        fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
        fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
        fun onAttachedToWindow()
        fun onDetachedFromWindow()
        fun onMovedToDisplay(displayId: Int, config: android.content.res.Configuration) {}
        fun onVisibilityChanged(changedView: View, visibility: Int)
        fun onWindowFocusChanged(hasWindowFocus: Boolean)
        fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect)
        fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean
        fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int)
        fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int)
        fun dispatchKeyEvent(event: KeyEvent): Boolean
        fun onTouchEvent(ev: MotionEvent): Boolean
        fun onHoverEvent(event: MotionEvent): Boolean
        fun onGenericMotionEvent(event: MotionEvent): Boolean
        fun onTrackballEvent(ev: MotionEvent): Boolean
        fun requestFocus(direction: Int, previouslyFocusedRect: Rect): Boolean
        fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
        fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean
        fun setBackgroundColor(color: Int)
        fun setLayerType(layerType: Int, paint: Paint)
        fun preDispatchDraw(canvas: Canvas)
        fun onStartTemporaryDetach()
        fun onFinishTemporaryDetach()
        fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent)
        fun getHandler(originalHandler: Handler): Handler
        fun findFocus(originalFocusedView: View): View
        fun onCheckIsTextEditor(): Boolean = false
        fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets? = insets
        fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? = null
    }

    interface ScrollDelegate {
        fun computeHorizontalScrollRange(): Int
        fun computeHorizontalScrollOffset(): Int
        fun computeVerticalScrollRange(): Int
        fun computeVerticalScrollOffset(): Int
        fun computeVerticalScrollExtent(): Int
        fun computeScroll()
    }
}

// -----------------------------------------------------------------------------
// WebViewClient
// -----------------------------------------------------------------------------

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
    open fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return shouldOverrideUrlLoading(view, request?.url?.toString())
    }
    open fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
    open fun onPageFinished(view: WebView?, url: String?) {}
    open fun onLoadResource(view: WebView?, url: String?) {}
    open fun onPageCommitVisible(view: WebView?, url: String?) {}
    open fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = null
    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return shouldInterceptRequest(view, request?.url?.toString())
    }
    open fun onTooManyRedirects(view: WebView?, cancelMsg: Message?, continueMsg: Message?) {
        cancelMsg?.sendToTarget()
    }
    open fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {}
    open fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {}
    open fun onFormResubmission(view: WebView?, dontResend: Message?, resend: Message?) {
        dontResend?.sendToTarget()
    }
    open fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {}
    open fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }
    open fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        request?.cancel()
    }
    open fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        handler?.cancel()
    }
    open fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean = false
    open fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {}
    open fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {}
    open fun onReceivedLoginRequest(view: WebView?, realm: String?, account: String?, args: String?) {}
    open fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = false
    open fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
        callback?.backToSafety(true)
    }

    companion object {
        const val ERROR_UNKNOWN = -1
        const val ERROR_HOST_LOOKUP = -2
        const val ERROR_UNSUPPORTED_AUTH_SCHEME = -3
        const val ERROR_AUTHENTICATION = -4
        const val ERROR_PROXY_AUTHENTICATION = -5
        const val ERROR_CONNECT = -6
        const val ERROR_IO = -7
        const val ERROR_TIMEOUT = -8
        const val ERROR_REDIRECT_LOOP = -9
        const val ERROR_UNSUPPORTED_SCHEME = -10
        const val ERROR_FAILED_SSL_HANDSHAKE = -11
        const val ERROR_BAD_URL = -12
        const val ERROR_FILE = -13
        const val ERROR_FILE_NOT_FOUND = -14
        const val ERROR_TOO_MANY_REQUESTS = -15
    }
}

// -----------------------------------------------------------------------------
// WebChromeClient
// -----------------------------------------------------------------------------

open class WebChromeClient {
    fun interface CustomViewCallback {
        fun onCustomViewHidden()
    }

    open fun onProgressChanged(view: WebView?, newProgress: Int) {}
    open fun onReceivedTitle(view: WebView?, title: String?) {}
    open fun onReceivedIcon(view: WebView?, icon: Bitmap?) {}
    open fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {}
    open fun onShowCustomView(view: View?, callback: CustomViewCallback?) {}
    open fun onHideCustomView() {}
    open fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
    open fun onRequestFocus(view: WebView?) {}
    open fun onCloseWindow(window: WebView?) {}
    open fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean = false
    open fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean = false
    open fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean = false
    open fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean = false
    open fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = false
    open fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
    open fun onPermissionRequestCanceled(request: PermissionRequest?) {}
}

// -----------------------------------------------------------------------------
// WebSettings
// -----------------------------------------------------------------------------

abstract class WebSettings {
    enum class LayoutAlgorithm { NORMAL, SINGLE_COLUMN, NARROW_COLUMNS, TEXT_AUTOSIZING }
    enum class TextSize(val value: Int) { SMALLEST(50), SMALLER(75), NORMAL(100), LARGER(150), LARGEST(200) }
    enum class ZoomDensity(val value: Int) { FAR(150), MEDIUM(100), CLOSE(75) }
    enum class RenderPriority { NORMAL, HIGH, LOW }
    enum class PluginState { ON, ON_DEMAND, OFF }

    open fun setNavDump(p0: Boolean) {}
    open fun getNavDump(): Boolean = false
    open fun setSupportZoom(p0: Boolean) {}
    open fun supportZoom(): Boolean = false
    open fun setMediaPlaybackRequiresUserGesture(p0: Boolean) {}
    open fun getMediaPlaybackRequiresUserGesture(): Boolean = false
    open fun setBuiltInZoomControls(p0: Boolean) {}
    open fun getBuiltInZoomControls(): Boolean = false
    open fun setDisplayZoomControls(p0: Boolean) {}
    open fun getDisplayZoomControls(): Boolean = false
    open fun setAllowFileAccess(p0: Boolean) {}
    open fun getAllowFileAccess(): Boolean = true
    open fun setAllowContentAccess(p0: Boolean) {}
    open fun getAllowContentAccess(): Boolean = true
    open fun setLoadWithOverviewMode(p0: Boolean) {}
    open fun getLoadWithOverviewMode(): Boolean = false
    open fun setPluginsEnabled(p0: Boolean) {}
    open fun getPluginsEnabled(): Boolean = false
    open fun setEnableSmoothTransition(p0: Boolean) {}
    open fun enableSmoothTransition(): Boolean = false
    open fun setUseWebViewBackgroundForOverscrollBackground(p0: Boolean) {}
    open fun getUseWebViewBackgroundForOverscrollBackground(): Boolean = false
    open fun setSaveFormData(p0: Boolean) {}
    open fun getSaveFormData(): Boolean = false
    open fun setSavePassword(p0: Boolean) {}
    open fun getSavePassword(): Boolean = false
    open fun setTextZoom(p0: Int) {}
    open fun getTextZoom(): Int = 100
    open fun setDefaultZoom(p0: ZoomDensity?) {}
    open fun getDefaultZoom(): ZoomDensity? = ZoomDensity.MEDIUM
    open fun setAcceptThirdPartyCookies(p0: Boolean) {}
    open fun getAcceptThirdPartyCookies(): Boolean = true
    open fun setLightTouchEnabled(p0: Boolean) {}
    open fun getLightTouchEnabled(): Boolean = false
    open fun setUserAgent(ua: Int): Unit = throw RuntimeException("Stub!")
    open fun getUserAgent(): Int = throw RuntimeException("Stub!")
    open fun setUseWideViewPort(p0: Boolean) {}
    open fun getUseWideViewPort(): Boolean = true
    open fun setSupportMultipleWindows(p0: Boolean) {}
    open fun supportMultipleWindows(): Boolean = false
    open fun setLayoutAlgorithm(p0: LayoutAlgorithm?) {}
    open fun getLayoutAlgorithm(): LayoutAlgorithm? = LayoutAlgorithm.NORMAL
    open fun setStandardFontFamily(p0: String?) {}
    open fun getStandardFontFamily(): String? = "sans-serif"
    open fun setFixedFontFamily(p0: String?) {}
    open fun getFixedFontFamily(): String? = "monospace"
    open fun setSansSerifFontFamily(p0: String?) {}
    open fun getSansSerifFontFamily(): String? = "sans-serif"
    open fun setSerifFontFamily(p0: String?) {}
    open fun getSerifFontFamily(): String? = "serif"
    open fun setCursiveFontFamily(p0: String?) {}
    open fun getCursiveFontFamily(): String? = "cursive"
    open fun setFantasyFontFamily(p0: String?) {}
    open fun getFantasyFontFamily(): String? = "fantasy"
    open fun setMinimumFontSize(p0: Int) {}
    open fun getMinimumFontSize(): Int = 8
    open fun setMinimumLogicalFontSize(p0: Int) {}
    open fun getMinimumLogicalFontSize(): Int = 8
    open fun setDefaultFontSize(p0: Int) {}
    open fun getDefaultFontSize(): Int = 16
    open fun setDefaultFixedFontSize(p0: Int) {}
    open fun getDefaultFixedFontSize(): Int = 13
    open fun setLoadsImagesAutomatically(p0: Boolean) {}
    open fun getLoadsImagesAutomatically(): Boolean = true
    open fun setBlockNetworkImage(p0: Boolean) {}
    open fun getBlockNetworkImage(): Boolean = false
    open fun setBlockNetworkLoads(p0: Boolean) {}
    open fun getBlockNetworkLoads(): Boolean = false
    open fun setJavaScriptEnabled(p0: Boolean) {}
    open fun getJavaScriptEnabled(): Boolean = true
    open fun setAllowUniversalAccessFromFileURLs(p0: Boolean) {}
    open fun getAllowUniversalAccessFromFileURLs(): Boolean = false
    open fun setAllowFileAccessFromFileURLs(p0: Boolean) {}
    open fun getAllowFileAccessFromFileURLs(): Boolean = false
    open fun setPluginState(p0: PluginState?) {}
    open fun getPluginState(): PluginState? = PluginState.OFF
    open fun setDatabasePath(p0: String?) {}
    open fun getDatabasePath(): String? = null
    open fun setGeolocationDatabasePath(p0: String?) {}
    open fun getGeolocationDatabasePath(): String? = null
    open fun setAppCacheEnabled(p0: Boolean) {}
    open fun setAppCachePath(p0: String?) {}
    open fun setAppCacheMaxSize(p0: Long) {}
    open fun setDatabaseEnabled(p0: Boolean) {}
    open fun getDatabaseEnabled(): Boolean = true
    open fun setDomStorageEnabled(p0: Boolean) {}
    open fun getDomStorageEnabled(): Boolean = true
    open fun setGeolocationEnabled(p0: Boolean) {}
    open fun setJavaScriptCanOpenWindowsAutomatically(p0: Boolean) {}
    open fun getJavaScriptCanOpenWindowsAutomatically(): Boolean = false
    open fun setDefaultTextEncodingName(p0: String?) {}
    open fun getDefaultTextEncodingName(): String? = "UTF-8"
    open fun setUserAgentString(p0: String?) {}
    open fun getUserAgentString(): String? = null
    open fun setNeedInitialFocus(p0: Boolean) {}
    open fun setRenderPriority(p0: RenderPriority?) {}
    open fun setCacheMode(p0: Int) {}
    open fun getCacheMode(): Int = 0
    open fun setMixedContentMode(p0: Int) {}
    open fun getMixedContentMode(): Int = 0
    open fun setOffscreenPreRaster(p0: Boolean) {}
    open fun getOffscreenPreRaster(): Boolean = false
    open fun setVideoOverlayForEmbeddedEncryptedVideoEnabled(p0: Boolean) {}
    open fun getVideoOverlayForEmbeddedEncryptedVideoEnabled(): Boolean = false
    open fun setSafeBrowsingEnabled(p0: Boolean) {}
    open fun getSafeBrowsingEnabled(): Boolean = true
    open fun setDisabledActionModeMenuItems(p0: Int) {}
    open fun getDisabledActionModeMenuItems(): Int = 0

    companion object {
        const val LOAD_DEFAULT = -1
        const val LOAD_NORMAL = 0
        const val LOAD_CACHE_ELSE_NETWORK = 1
        const val LOAD_NO_CACHE = 2
        const val LOAD_CACHE_ONLY = 3
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2
    }
}

var WebSettings.userAgentString: String?
    get() = getUserAgentString()
    set(value) { setUserAgentString(value) }

var WebSettings.javaScriptEnabled: Boolean
    get() = getJavaScriptEnabled()
    set(value) { setJavaScriptEnabled(value) }

var WebSettings.domStorageEnabled: Boolean
    get() = getDomStorageEnabled()
    set(value) { setDomStorageEnabled(value) }

var WebSettings.databaseEnabled: Boolean
    get() = getDatabaseEnabled()
    set(value) { setDatabaseEnabled(value) }

var WebSettings.blockNetworkLoads: Boolean
    get() = getBlockNetworkLoads()
    set(value) { setBlockNetworkLoads(value) }

var WebSettings.mixedContentMode: Int
    get() = getMixedContentMode()
    set(value) { setMixedContentMode(value) }

var WebSettings.cacheMode: Int
    get() = getCacheMode()
    set(value) { setCacheMode(value) }

// -----------------------------------------------------------------------------
// CookieManager
// -----------------------------------------------------------------------------

abstract class CookieManager {
    companion object {
        @Volatile
        private var instance: CookieManager? = null

        @JvmStatic
        fun getInstance(): CookieManager {
            return instance ?: synchronized(this) {
                instance ?: xyz.nulldev.androidcompat.webkit.CookieManagerImpl().also { instance = it }
            }
        }

        @JvmStatic
        fun allowFileSchemeCookies(): Boolean = false

        @JvmStatic
        fun setAcceptFileSchemeCookies(accept: Boolean) {}
    }

    abstract fun setAcceptCookie(accept: Boolean)
    abstract fun acceptCookie(): Boolean
    abstract fun setAcceptThirdPartyCookies(webview: WebView?, accept: Boolean)
    abstract fun acceptThirdPartyCookies(webview: WebView?): Boolean
    abstract fun setCookie(url: String, value: String?)
    abstract fun setCookie(url: String, value: String?, callback: ValueCallback<Boolean>?)
    abstract fun getCookie(url: String): String?
    open fun getCookie(url: String?, privateBrowsing: Boolean): String? = url?.let { getCookie(it) }
    open fun removeSessionCookie() {}
    abstract fun removeSessionCookies(callback: ValueCallback<Boolean>?)
    open fun removeExpiredCookie() {}
    open fun removeAllCookie() {}
    abstract fun removeAllCookies(callback: ValueCallback<Boolean>?)
    abstract fun hasCookies(): Boolean
    abstract fun flush()
    open fun allowFileSchemeCookiesImpl(): Boolean = false
    open fun setAcceptFileSchemeCookiesImpl(accept: Boolean) {}
}

// -----------------------------------------------------------------------------
// WebResourceRequest & WebResourceResponse
// -----------------------------------------------------------------------------

interface WebResourceRequest {
    fun getUrl(): Uri
    fun isForMainFrame(): Boolean
    fun isRedirect(): Boolean
    fun hasGesture(): Boolean
    fun getMethod(): String
    fun getRequestHeaders(): Map<String, String>?
}

val WebResourceRequest.url: Uri get() = getUrl()
val WebResourceRequest.method: String get() = getMethod()
val WebResourceRequest.requestHeaders: Map<String, String>? get() = getRequestHeaders()

open class WebResourceResponse {
    private var _mimeType: String? = null
    private var _encoding: String? = null
    private var _data: InputStream? = null
    private var _statusCode: Int = 200
    private var _reasonPhrase: String = "OK"
    private var _responseHeaders: Map<String, String>? = null

    constructor(mimeType: String? = null, encoding: String? = null, data: InputStream? = null) {
        this._mimeType = mimeType
        this._encoding = encoding
        this._data = data
    }

    constructor(
        mimeType: String?,
        encoding: String?,
        statusCode: Int,
        reasonPhrase: String,
        responseHeaders: Map<String, String>?,
        data: InputStream?
    ) : this(mimeType, encoding, data) {
        this._statusCode = statusCode
        this._reasonPhrase = reasonPhrase
        this._responseHeaders = responseHeaders
    }

    open fun setMimeType(mimeType: String?) { this._mimeType = mimeType }
    open fun getMimeType(): String? = _mimeType
    open fun setEncoding(encoding: String?) { this._encoding = encoding }
    open fun getEncoding(): String? = _encoding
    open fun setData(data: InputStream?) { this._data = data }
    open fun getData(): InputStream? = _data
    open fun setStatusCodeAndReasonPhrase(statusCode: Int, reasonPhrase: String) {
        this._statusCode = statusCode
        this._reasonPhrase = reasonPhrase
    }
    open fun getStatusCode(): Int = _statusCode
    open fun getReasonPhrase(): String = _reasonPhrase
    open fun setResponseHeaders(headers: Map<String, String>?) { this._responseHeaders = headers }
    open fun getResponseHeaders(): Map<String, String>? = _responseHeaders
}

var WebResourceResponse.mimeType: String?
    get() = getMimeType()
    set(value) { setMimeType(value) }
var WebResourceResponse.encoding: String?
    get() = getEncoding()
    set(value) { setEncoding(value) }
var WebResourceResponse.data: InputStream?
    get() = getData()
    set(value) { setData(value) }
var WebResourceResponse.statusCode: Int
    get() = getStatusCode()
    set(value) { setStatusCodeAndReasonPhrase(value, reasonPhrase) }
var WebResourceResponse.reasonPhrase: String
    get() = getReasonPhrase()
    set(value) { setStatusCodeAndReasonPhrase(statusCode, value) }
var WebResourceResponse.responseHeaders: Map<String, String>?
    get() = getResponseHeaders()
    set(value) { setResponseHeaders(value) }

// -----------------------------------------------------------------------------
// Callbacks & Listeners
// -----------------------------------------------------------------------------

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T?)
}

fun interface DownloadListener {
    fun onDownloadStart(url: String?, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long)
}

// -----------------------------------------------------------------------------
// Auxiliary WebKit classes
// -----------------------------------------------------------------------------

abstract class PermissionRequest {
    abstract fun getOrigin(): Uri
    abstract fun getResources(): Array<String>
    abstract fun grant(resources: Array<String>)
    abstract fun deny()

    companion object {
        const val RESOURCE_VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
        const val RESOURCE_AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
        const val RESOURCE_PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID"
        const val RESOURCE_MIDI_SYSEX = "android.webkit.resource.MIDI_SYSEX"
    }
}

val PermissionRequest.origin: Uri get() = getOrigin()
val PermissionRequest.resources: Array<String> get() = getResources()

abstract class RenderProcessGoneDetail {
    abstract fun didCrash(): Boolean
    abstract fun rendererPriorityAtExit(): Int
}

abstract class WebBackForwardList : Cloneable {
    abstract val currentItem: WebHistoryItem?
    abstract val currentIndex: Int
    abstract val size: Int
    abstract fun getItemAtIndex(index: Int): WebHistoryItem?
}

abstract class WebHistoryItem : Cloneable {
    abstract val id: Int
    abstract val url: String?
    abstract val originalUrl: String?
    abstract val title: String?
    abstract val favicon: Bitmap?
}

open class WebMessage(
    val data: String? = null,
    val ports: Array<WebMessagePort>? = null
)

abstract class WebMessagePort {
    abstract fun postMessage(message: WebMessage)
    abstract fun close()
    abstract fun setWebMessageCallback(callback: WebMessageCallback?)
    abstract fun setWebMessageCallback(callback: WebMessageCallback?, handler: Handler?)

    abstract class WebMessageCallback {
        open fun onMessage(port: WebMessagePort?, message: WebMessage?) {}
    }
}

abstract class WebViewRenderProcess {
    abstract fun terminate(): Boolean
}

abstract class WebViewRenderProcessClient {
    abstract fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?)
    abstract fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?)
}

open class MimeTypeMap {
    companion object {
        private val singleton = MimeTypeMap()
        @JvmStatic
        fun getSingleton(): MimeTypeMap = singleton
        @JvmStatic
        fun getFileExtensionFromUrl(url: String?): String {
            if (url.isNullOrBlank()) return ""
            val clean = url.substringBefore('?').substringBefore('#')
            val slash = clean.lastIndexOf('/')
            val filename = if (slash >= 0) clean.substring(slash + 1) else clean
            val dot = filename.lastIndexOf('.')
            return if (dot >= 0) filename.substring(dot + 1) else ""
        }
    }

    open fun hasMimeType(mimeType: String?): Boolean = !mimeType.isNullOrBlank()
    open fun getMimeTypeFromExtension(extension: String?): String? = null
    open fun hasExtension(extension: String?): Boolean = !extension.isNullOrBlank()
    open fun getExtensionFromMimeType(mimeType: String?): String? = null
}

open class URLUtil {
    companion object {
        @JvmStatic
        fun isHttpUrl(url: String?): Boolean = url?.startsWith("http://", ignoreCase = true) == true
        @JvmStatic
        fun isHttpsUrl(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true
        @JvmStatic
        fun isNetworkUrl(url: String?): Boolean = isHttpUrl(url) || isHttpsUrl(url)
        @JvmStatic
        fun isAssetUrl(url: String?): Boolean = url?.startsWith("file:///android_asset/") == true
        @JvmStatic
        fun isFileUrl(url: String?): Boolean = url?.startsWith("file://") == true && !isAssetUrl(url)
        @JvmStatic
        fun isContentUrl(url: String?): Boolean = url?.startsWith("content://") == true
        @JvmStatic
        fun isDataUrl(url: String?): Boolean = url?.startsWith("data:", ignoreCase = true) == true
        @JvmStatic
        fun isAboutUrl(url: String?): Boolean = url?.startsWith("about:", ignoreCase = true) == true
        @JvmStatic
        fun isJavaScriptUrl(url: String?): Boolean = url?.startsWith("javascript:", ignoreCase = true) == true
        @JvmStatic
        fun guessFileName(url: String?, contentDisposition: String?, mimeType: String?): String = "downloadfile"
    }
}

open class ConsoleMessage(
    val message: String? = null,
    val sourceId: String? = null,
    val lineNumber: Int = 0,
    val messageLevel: MessageLevel = MessageLevel.LOG
) {
    enum class MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }
}

open class JsResult {
    open fun cancel() {}
    open fun confirm() {}
}

open class JsPromptResult : JsResult() {
    open fun confirm(result: String?) {}
}

open class SslErrorHandler : Handler() {
    open fun proceed() {}
    open fun cancel() {}
}

abstract class ClientCertRequest {
    abstract val keyTypes: Array<String>?
    abstract val principals: Array<java.security.Principal>?
    abstract val host: String?
    abstract val port: Int
    abstract fun proceed(privateKey: java.security.PrivateKey?, certificateChain: Array<java.security.cert.X509Certificate>?)
    abstract fun ignore()
    abstract fun cancel()
}

open class HttpAuthHandler : Handler() {
    open fun proceed(username: String?, password: String?) {}
    open fun cancel() {}
    open fun useHttpAuthUsernamePassword(): Boolean = false
}

abstract class SafeBrowsingResponse {
    abstract fun showInterstitial(allowReporting: Boolean)
    abstract fun proceed(report: Boolean)
    abstract fun backToSafety(report: Boolean)
}
